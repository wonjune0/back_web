package com.wonjune.backweb.order;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

	Optional<Order> findByOrderNumberAndUserId(String orderNumber, Long userId);

	Page<Order> findByUserIdOrderByPlacedAtDesc(Long userId, Pageable pageable);

}
