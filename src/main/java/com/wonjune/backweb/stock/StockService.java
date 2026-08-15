package com.wonjune.backweb.stock;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockService {

	private final StockRepository stockRepository;
	private final StockProperties stockProperties;

	@Transactional(readOnly = true)
	public Map<Long, Integer> quantitiesOf(Collection<Long> productIds) {
		if (productIds.isEmpty()) {
			return Map.of();
		}
		return stockRepository.findByProductIdIn(productIds).stream()
				.collect(Collectors.toMap(Stock::getProductId, Stock::getQuantity));
	}

	/**
	 * Takes every line of an order off the shelf, or takes nothing.
	 *
	 * Products are walked in ascending id order so that two checkouts sharing products
	 * always acquire their row locks in the same sequence. Locking in cart order instead
	 * would let one order hold product 5 while waiting for 9 and another hold 9 while
	 * waiting for 5, which InnoDB resolves by killing one of them.
	 *
	 * MANDATORY because a partial decrement has to roll back with the order that caused
	 * it -- this must never run on its own transaction.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void decrease(Map<Long, Integer> quantityByProductId) {
		List<Long> productIds = quantityByProductId.keySet().stream().sorted().toList();
		for (Long productId : productIds) {
			int quantity = quantityByProductId.get(productId);
			switch (stockProperties.getStrategy()) {
				case ATOMIC -> decreaseAtomic(productId, quantity);
				case PESSIMISTIC -> decreasePessimistic(productId, quantity);
				case NONE -> decreaseUnguarded(productId, quantity);
			}
		}
	}

	/**
	 * Puts stock back when a payment fails or an order is cancelled. Ordered for the same
	 * reason as decrease.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void restore(Map<Long, Integer> quantityByProductId) {
		quantityByProductId.keySet().stream().sorted()
				.forEach(productId -> stockRepository.increase(productId, quantityByProductId.get(productId)));
	}

	private void decreaseAtomic(Long productId, int quantity) {
		if (stockRepository.decreaseIfEnough(productId, quantity) == 0) {
			// Nothing was written, so the row is only read here to report what is left.
			throw new OutOfStockException(productId, currentQuantity(productId));
		}
	}

	private void decreasePessimistic(Long productId, int quantity) {
		Stock stock = stockRepository.findByProductIdForUpdate(productId)
				.orElseThrow(() -> new OutOfStockException(productId, 0));
		if (!stock.decrease(quantity)) {
			throw new OutOfStockException(productId, stock.getQuantity());
		}
	}

	/**
	 * Deliberately unguarded: the read and the write are separate statements with no lock
	 * between them, which is exactly the window two concurrent checkouts slip through.
	 * Only reachable via stock.strategy=NONE, used to demonstrate the oversell.
	 */
	private void decreaseUnguarded(Long productId, int quantity) {
		Stock stock = stockRepository.findById(productId)
				.orElseThrow(() -> new OutOfStockException(productId, 0));
		if (!stock.decrease(quantity)) {
			throw new OutOfStockException(productId, stock.getQuantity());
		}
	}

	private int currentQuantity(Long productId) {
		return stockRepository.findById(productId).map(Stock::getQuantity).orElse(0);
	}

}
