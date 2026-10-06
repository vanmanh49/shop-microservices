package com.vanmanh49.notification.web;

import java.util.List;

import com.vanmanh49.notification.NotificationService;
import com.vanmanh49.notification.UnauthenticatedException;
import com.vanmanh49.notification.web.dto.NotificationResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

	private final NotificationService notifications;

	public NotificationController(NotificationService notifications) {
		this.notifications = notifications;
	}

	// The gateway sets X-User-Id from the validated token.
	@GetMapping
	public List<NotificationResponse> list(@RequestHeader(name = "X-User-Id", required = false) String userId) {
		if (userId == null || userId.isBlank()) {
			throw new UnauthenticatedException();
		}
		return this.notifications.listFor(userId).stream().map(NotificationResponse::from).toList();
	}

}
