package com.vanmanh49.order.event;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration(proxyBeanMethods = false)
public class KafkaTopicConfig {

	// Created at startup if missing. One replica suits the single-broker Compose setup.
	@Bean
	NewTopic orderEventsTopic(@Value("${shop.kafka.order-events-topic}") String topic) {
		return TopicBuilder.name(topic).partitions(1).replicas(1).build();
	}

}
