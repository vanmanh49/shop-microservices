package com.vanmanh49.order.web;

import java.util.List;

import com.vanmanh49.order.OrderService;
import com.vanmanh49.order.UnauthenticatedException;
import com.vanmanh49.order.web.dto.OrderResponse;
import com.vanmanh49.order.web.dto.PlaceOrderRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The caller's identity arrives in X-User-Id, which the gateway sets from the token. */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

	private static final String USER_ID = "X-User-Id";

	private final OrderService orderService;

	public OrderController(OrderService orderService) {
		this.orderService = orderService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public OrderResponse place(@RequestHeader(name = USER_ID, required = false) String userId,
			@Valid @RequestBody PlaceOrderRequest request) {
		return OrderResponse.from(this.orderService.place(requireUser(userId), request.items()));
	}

	@GetMapping
	public List<OrderResponse> list(@RequestHeader(name = USER_ID, required = false) String userId) {
		return this.orderService.list(requireUser(userId)).stream().map(OrderResponse::from).toList();
	}

	@GetMapping("/{id}")
	public OrderResponse get(@RequestHeader(name = USER_ID, required = false) String userId, @PathVariable Long id) {
		return OrderResponse.from(this.orderService.get(id, requireUser(userId)));
	}

	@PostMapping("/{id}/cancel")
	public OrderResponse cancel(@RequestHeader(name = USER_ID, required = false) String userId,
			@PathVariable Long id) {
		return OrderResponse.from(this.orderService.cancel(id, requireUser(userId)));
	}

	private static String requireUser(String userId) {
		if (userId == null || userId.isBlank()) {
			throw new UnauthenticatedException();
		}
		return userId;
	}

}
