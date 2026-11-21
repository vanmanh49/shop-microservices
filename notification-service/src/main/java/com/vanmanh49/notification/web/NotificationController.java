package com.vanmanh49.notification.web;

import com.vanmanh49.notification.NotificationService;
import com.vanmanh49.notification.UnauthenticatedException;
import com.vanmanh49.notification.web.dto.NotificationResponse;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

	private static final int MAX_PAGE_SIZE = 100;

	private final NotificationService notifications;

	public NotificationController(NotificationService notifications) {
		this.notifications = notifications;
	}

	// The gateway sets X-User-Id from the validated token. Newest first.
	@GetMapping
	public PagedModel<NotificationResponse> list(@RequestHeader(name = "X-User-Id", required = false) String userId,
			@RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) int size) {
		if (userId == null || userId.isBlank()) {
			throw new UnauthenticatedException();
		}
		PageRequest pageable = PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE), Sort.by(Sort.Direction.DESC, "id"));
		return new PagedModel<>(this.notifications.listFor(userId, pageable).map(NotificationResponse::from));
	}

}
