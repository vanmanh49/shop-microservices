package com.vanmanh49.notification.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;

import com.vanmanh49.notification.Notification;
import com.vanmanh49.notification.NotificationRepository;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

/** Runs the real listener against an in-process Kafka broker. */
@SpringBootTest(properties = { "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
		"spring.kafka.listener.auto-startup=true" })
@EmbeddedKafka(topics = "order-events", partitions = 1)
class OrderEventListenerIntegrationTests {

	@Autowired
	private EmbeddedKafkaBroker broker;

	@Autowired
	private NotificationRepository notifications;

	@AfterEach
	void cleanUp() {
		this.notifications.deleteAll();
	}

	@Test
	void malformedRecordIsSkippedAndNextIsProcessed() {
		Map<String, Object> producerProperties = KafkaTestUtils.producerProps(this.broker);
		DefaultKafkaProducerFactory<String, String> producerFactory = new DefaultKafkaProducerFactory<>(
				producerProperties, new StringSerializer(), new StringSerializer());
		try {
			KafkaTemplate<String, String> producer = new KafkaTemplate<>(producerFactory);
			producer.send("order-events", "76", "not json");
			producer.send("order-events", "77", """
					{"eventId":"3f2b7c1e-8a4d-4e0b-9c55-0a1b2c3d4e5f","type":"ORDER_PLACED","orderId":77,
					 "userId":"42","total":99.98,"occurredAt":"2026-10-07T10:15:30Z"}""");
			producer.flush();

			// Both records are on one partition, so the valid one is only reached if the
			// malformed one before it was skipped.
			await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(this.notifications.findAll())
				.singleElement()
				.extracting(Notification::getOrderId)
				.isEqualTo(77L));
		}
		finally {
			producerFactory.destroy();
		}
	}

}
