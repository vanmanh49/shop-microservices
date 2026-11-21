package com.vanmanh49.order;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

	@EntityGraph(attributePaths = "items")
	Optional<Order> findByIdAndUserId(Long id, String userId);

	// No entity graph: Hibernate would page a collection fetch in memory. Items are
	// batch-loaded instead (see Order.items).
	Page<Order> findByUserId(String userId, Pageable pageable);

}
