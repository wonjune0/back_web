package com.wonjune.backweb.stock;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockRepository extends JpaRepository<Stock, Long> {

	List<Stock> findByProductIdIn(Collection<Long> productIds);

	/**
	 * Emits SELECT ... FOR UPDATE, so concurrent checkouts for the same product queue up
	 * behind the row lock until the holder's transaction commits.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT s FROM Stock s WHERE s.productId = :productId")
	Optional<Stock> findByProductIdForUpdate(@Param("productId") Long productId);

	/**
	 * Check and decrement in one statement: InnoDB takes the row lock, evaluates
	 * quantity >= :quantity and writes, all without a round trip in between. Returns the
	 * number of rows changed -- 0 means there was not enough stock, and nothing was written.
	 *
	 * clearAutomatically keeps the persistence context from serving a stale entity to
	 * anything that read this row earlier in the same transaction.
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			UPDATE Stock s SET s.quantity = s.quantity - :quantity
			WHERE s.productId = :productId AND s.quantity >= :quantity
			""")
	int decreaseIfEnough(@Param("productId") Long productId, @Param("quantity") int quantity);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("UPDATE Stock s SET s.quantity = s.quantity + :quantity WHERE s.productId = :productId")
	int increase(@Param("productId") Long productId, @Param("quantity") int quantity);

}
