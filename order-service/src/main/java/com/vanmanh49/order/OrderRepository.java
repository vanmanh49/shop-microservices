package com.vanmanh49.order;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

	@EntityGraph(attributePaths = "items")
	Optional<Order> findByIdAndUserId(Long id, String userId);

	@EntityGraph(attributePaths = "items")
	List<Order> findByUserIdOrderByIdDesc(String userId);

}
