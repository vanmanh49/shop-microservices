package com.vanmanh49.notification.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;

/** Exercises the value deserializer exactly as the Kafka consumer is configured. */
@SpringBootTest
class OrderEventDeserializationTests {

	private static final String TOPIC = "order-events";

	@Autowired
	private ConsumerFactory<?, ?> consumerFactory;

	private ErrorHandlingDeserializer<Object> deserializer;

	@BeforeEach
	void configure() {
		this.deserializer = new ErrorHandlingDeserializer<>();
		this.deserializer.configure(this.consumerFactory.getConfigurationProperties(), false);
	}

	@AfterEach
	void close() {
		this.deserializer.close();
	}

	@Test
	void readsEventAndIgnoresUnknownFields() {
		String json = """
				{"eventId":"3f2b7c1e-8a4d-4e0b-9c55-0a1b2c3d4e5f","type":"ORDER_PLACED","orderId":7,"userId":"42",
				 "total":99.98,"occurredAt":"2026-10-07T10:15:30Z","addedLater":"ignored"}""";

		Object value = this.deserializer.deserialize(TOPIC, new RecordHeaders(), bytes(json));

		assertThat(value).isEqualTo(new OrderEvent(UUID.fromString("3f2b7c1e-8a4d-4e0b-9c55-0a1b2c3d4e5f"),
				"ORDER_PLACED", 7L, "42", new BigDecimal("99.98"), Instant.parse("2026-10-07T10:15:30Z")));
	}

	@Test
	void malformedRecordIsSkippedAndNextIsProcessed() {
		Headers headers = new RecordHeaders();

		Object malformed = this.deserializer.deserialize(TOPIC, headers, bytes("not json"));

		// No exception: the failure travels in a header and the container skips the record.
		assertThat(malformed).isNull();
		assertThat(headers.lastHeader("springDeserializerExceptionValue")).isNotNull();

		Object next = this.deserializer.deserialize(TOPIC, new RecordHeaders(), bytes("""
				{"eventId":"3f2b7c1e-8a4d-4e0b-9c55-0a1b2c3d4e5f","type":"ORDER_CANCELLED","orderId":8,
				 "userId":"42","total":10.00,"occurredAt":"2026-10-07T10:15:30Z"}"""));
		assertThat(next).isInstanceOf(OrderEvent.class);
	}

	private static byte[] bytes(String text) {
		return text.getBytes(StandardCharsets.UTF_8);
	}

}
