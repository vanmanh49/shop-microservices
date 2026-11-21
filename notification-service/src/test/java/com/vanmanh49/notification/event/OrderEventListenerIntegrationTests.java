package com.vanmanh49.notification.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;

import java.time.Duration;
import java.util.Map;

import com.vanmanh49.notification.Notification;
import com.vanmanh49.notification.NotificationRepository;
import com.vanmanh49.notification.NotificationService;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.CannotCreateTransactionException;

/** Runs the real listener against an in-process Kafka broker. */
@SpringBootTest(properties = { "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
		"spring.kafka.listener.auto-startup=true" })
@EmbeddedKafka(topics = "order-events", partitions = 1)
class OrderEventListenerIntegrationTests {

	@Autowired
	private EmbeddedKafkaBroker broker;

	@Autowired
	private NotificationRepository notifications;

	@MockitoSpyBean
	private NotificationService notificationService;

	private DefaultKafkaProducerFactory<String, String> producerFactory;

	private KafkaTemplate<String, String> producer;

	@BeforeEach
	void setUp() {
		Map<String, Object> producerProperties = KafkaTestUtils.producerProps(this.broker);
		this.producerFactory = new DefaultKafkaProducerFactory<>(producerProperties, new StringSerializer(),
				new StringSerializer());
		this.producer = new KafkaTemplate<>(this.producerFactory);
	}

	@AfterEach
	void cleanUp() {
		this.producerFactory.destroy();
		this.notifications.deleteAll();
	}

	@Test
	void malformedRecordIsSkippedAndNextIsProcessed() {
		send("76", "not json");
		send("77", validEvent("3f2b7c1e-8a4d-4e0b-9c55-0a1b2c3d4e5f", 77));

		// Both records are on one partition, so the valid one is only reached if the
		// malformed one before it was skipped.
		awaitOnlyNotificationFor(77L, Duration.ofSeconds(30));
	}

	@Test
	void recordWithMissingFieldsIsSkippedWithoutLongRetries() {
		send("78", """
				{"eventId":"5d1e8a2b-3c4f-4a6b-8d7e-9f0a1b2c3d4e","type":"ORDER_PLACED","orderId":78}""");
		send("79", validEvent("6e2f9b3c-4d5a-4b7c-9e8f-0a1b2c3d4e5f", 79));

		awaitOnlyNotificationFor(79L, Duration.ofSeconds(10));
	}

	@Test
	void eventIsKeptWhileTheDatabaseIsBrieflyDown() {
		CannotCreateTransactionException down = new CannotCreateTransactionException("database down");
		willThrow(down).willThrow(down).willThrow(down).willCallRealMethod().given(this.notificationService).handle(any());

		send("80", validEvent("7f3a0c4d-5e6b-4c8d-8f9a-1b2c3d4e5f60", 80));

		awaitOnlyNotificationFor(80L, Duration.ofSeconds(30));
	}

	private void send(String key, String value) {
		this.producer.send("order-events", key, value);
		this.producer.flush();
	}

	private void awaitOnlyNotificationFor(long orderId, Duration timeout) {
		await().atMost(timeout)
			.untilAsserted(() -> assertThat(this.notifications.findAll()).singleElement()
				.extracting(Notification::getOrderId)
				.isEqualTo(orderId));
	}

	private static String validEvent(String eventId, long orderId) {
		return """
				{"eventId":"%s","type":"ORDER_PLACED","orderId":%d,
				 "userId":"42","total":99.98,"occurredAt":"2026-10-07T10:15:30Z"}""".formatted(eventId, orderId);
	}

}
