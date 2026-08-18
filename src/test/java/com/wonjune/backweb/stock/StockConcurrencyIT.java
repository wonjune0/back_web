package com.wonjune.backweb.stock;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The claim these tests exist to back: no matter how many buyers arrive at once, we never
 * sell more units than exist.
 *
 * Both guarded strategies are asserted exactly -- successes must equal the starting stock,
 * not merely stay under it. StockStrategy.NONE is deliberately not asserted here: its
 * failure is a race, so a test around it would pass or fail depending on how the machine
 * felt that morning. It is measured in the k6 run instead, where the oversell is the
 * headline number rather than a flaky assertion.
 */
@Testcontainers
@SpringBootTest
class StockConcurrencyIT {

	private static final int THREADS = 60;

	@Container
	static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
			.withDatabaseName("back_web")
			.withUsername("back_web")
			.withPassword("back_web");

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
		registry.add("spring.datasource.username", MYSQL::getUsername);
		registry.add("spring.datasource.password", MYSQL::getPassword);
		// Sixty threads against the default pool of ten would spend the test queueing.
		registry.add("spring.datasource.hikari.maximum-pool-size", () -> 20);
	}

	@Autowired
	private StockService stockService;

	@Autowired
	private StockRepository stockRepository;

	@Autowired
	private StockProperties stockProperties;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@AfterEach
	void restoreStrategy() {
		stockProperties.setStrategy(StockStrategy.ATOMIC);
	}

	@Test
	void 원자적_UPDATE는_재고보다_많이_팔지_않는다() {
		stockProperties.setStrategy(StockStrategy.ATOMIC);
		assertNoOversell(16L);
	}

	@Test
	void 비관적_락도_재고보다_많이_팔지_않는다() {
		stockProperties.setStrategy(StockStrategy.PESSIMISTIC);
		assertNoOversell(13L);
	}

	@Test
	void 여러_상품을_동시에_주문해도_교착상태에_빠지지_않는다() {
		stockProperties.setStrategy(StockStrategy.PESSIMISTIC);

		// Two products taken in opposite cart order by two halves of the pool. Locking in
		// the order the buyer happened to add them would deadlock; StockService sorts first.
		List<Map<Long, Integer>> carts = List.of(
				Map.of(1L, 1, 7L, 1),
				Map.of(7L, 1, 1L, 1));

		AtomicInteger succeeded = new AtomicInteger();
		AtomicInteger failed = new AtomicInteger();
		runConcurrently(20, index -> {
			try {
				decreaseInTransaction(carts.get(index % carts.size()));
				succeeded.incrementAndGet();
			} catch (RuntimeException e) {
				failed.incrementAndGet();
			}
		});

		assertThat(succeeded.get()).isEqualTo(20);
		assertThat(failed.get()).isZero();
	}

	private void assertNoOversell(long productId) {
		int initial = stockRepository.findById(productId).orElseThrow().getQuantity();
		assertThat(initial)
				.as("the test only proves anything if demand outstrips supply")
				.isLessThan(THREADS);

		AtomicInteger sold = new AtomicInteger();
		AtomicInteger rejected = new AtomicInteger();
		runConcurrently(THREADS, index -> {
			try {
				decreaseInTransaction(Map.of(productId, 1));
				sold.incrementAndGet();
			} catch (OutOfStockException e) {
				rejected.incrementAndGet();
			}
		});

		assertThat(sold.get()).isEqualTo(initial);
		assertThat(rejected.get()).isEqualTo(THREADS - initial);
		assertThat(stockRepository.findById(productId).orElseThrow().getQuantity()).isZero();
	}

	/**
	 * StockService is MANDATORY on purpose, so each attempt gets its own transaction here --
	 * which is also what makes them contend the way real checkouts do.
	 */
	private void decreaseInTransaction(Map<Long, Integer> quantityByProductId) {
		new TransactionTemplate(transactionManager).executeWithoutResult(status ->
				stockService.decrease(quantityByProductId));
	}

	/**
	 * Releases every thread at the same instant; staggered starts would not contend.
	 */
	private void runConcurrently(int threads, IntConsumer body) {
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(threads);
		try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
			for (int i = 0; i < threads; i++) {
				int index = i;
				pool.submit(() -> {
					try {
						start.await();
						body.accept(index);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					} finally {
						done.countDown();
					}
				});
			}
			start.countDown();
			if (!done.await(60, TimeUnit.SECONDS)) {
				throw new IllegalStateException("concurrent run did not finish in time");
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}

}
