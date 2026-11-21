package com.vanmanh49.notification;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

	boolean existsByEventId(UUID eventId);

	Page<Notification> findByUserId(String userId, Pageable pageable);

}
