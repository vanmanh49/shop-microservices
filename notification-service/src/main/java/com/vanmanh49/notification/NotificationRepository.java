package com.vanmanh49.notification;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

	boolean existsByEventId(UUID eventId);

	List<Notification> findByUserIdOrderByIdDesc(String userId);

}
