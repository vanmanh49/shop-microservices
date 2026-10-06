package com.vanmanh49.order.client;

import java.time.Duration;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.boot.restclient.autoconfigure.RestClientBuilderConfigurer;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class ClientConfig {

	/**
	 * A builder whose clients resolve host names such as {@code product-service} through
	 * Eureka. The configurer keeps Spring Boot's defaults (timeouts, tracing, JSON).
	 */
	@Bean
	@LoadBalanced
	RestClient.Builder loadBalancedRestClientBuilder(RestClientBuilderConfigurer configurer) {
		return configurer.configure(RestClient.builder());
	}

	@Bean
	Customizer<Resilience4JCircuitBreakerFactory> circuitBreakerCustomizer() {
		return circuitBreakerDefaults();
	}

	/**
	 * Opens a circuit when half of the last 10 calls failed (after at least 5 calls) and
	 * probes the dependency again after 10 seconds.
	 */
	public static Customizer<Resilience4JCircuitBreakerFactory> circuitBreakerDefaults() {
		CircuitBreakerConfig circuitBreaker = CircuitBreakerConfig.custom()
			.slidingWindowSize(10)
			.minimumNumberOfCalls(5)
			.failureRateThreshold(50)
			.waitDurationInOpenState(Duration.ofSeconds(10))
			.build();
		return (factory) -> factory.configureDefault((id) -> new Resilience4JConfigBuilder(id)
			.circuitBreakerConfig(circuitBreaker)
			.timeLimiterConfig(TimeLimiterConfig.ofDefaults())
			.build());
	}

}
