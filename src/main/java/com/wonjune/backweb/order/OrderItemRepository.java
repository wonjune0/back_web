package com.wonjune.backweb.order;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

	List<OrderItem> findByOrderId(Long orderId);

	/**
	 * 주문 목록 화면용. 주문마다 따로 조회하면 페이지 크기만큼 쿼리가 늘어나므로
	 * 한 페이지분의 항목을 한 번에 읽어 메모리에서 묶는다.
	 */
	List<OrderItem> findByOrderIdIn(List<Long> orderIds);

}
