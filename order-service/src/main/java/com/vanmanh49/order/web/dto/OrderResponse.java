package com.vanmanh49.order.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.vanmanh49.order.Order;
import com.vanmanh49.order.OrderItem;

public record OrderResponse(Long id, UUID orderRef, String status, BigDecimal total, Instant createdAt,
		List<Item> items) {

	public record Item(Long productId, String productName, BigDecimal unitPrice, int quantity) {

		static Item from(OrderItem item) {
			return new Item(item.getProductId(), item.getProductName(), item.getUnitPrice(), item.getQuantity());
		}

	}

	public static OrderResponse from(Order order) {
		return new OrderResponse(order.getId(), order.getOrderRef(), order.getStatus().name(), order.getTotal(),
				order.getCreatedAt(), order.getItems().stream().map(Item::from).toList());
	}

}
