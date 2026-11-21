package com.vanmanh49.order.web;

import com.vanmanh49.order.OrderService;
import com.vanmanh49.order.UnauthenticatedException;
import com.vanmanh49.order.web.dto.OrderResponse;
import com.vanmanh49.order.web.dto.PlaceOrderRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The caller's identity arrives in X-User-Id, which the gateway sets from the token. */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

	private static final String USER_ID = "X-User-Id";

	private static final int MAX_PAGE_SIZE = 100;

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

	// Newest first.
	@GetMapping
	public PagedModel<OrderResponse> list(@RequestHeader(name = USER_ID, required = false) String userId,
			@RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) int size) {
		PageRequest pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), Sort.by(Sort.Direction.DESC, "id"));
		return new PagedModel<>(this.orderService.list(requireUser(userId), pageable).map(OrderResponse::from));
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
