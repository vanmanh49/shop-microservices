package com.vanmanh49.gateway;

import static org.springframework.cloud.gateway.server.mvc.filter.CircuitBreakerFilterFunctions.circuitBreaker;
import static org.springframework.cloud.gateway.server.mvc.filter.LoadBalancerFilterFunctions.lb;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.path;

import java.net.URI;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/** One route per service: a path prefix mapped to the service's name in Eureka. */
@Configuration(proxyBeanMethods = false)
public class RouteConfig {

	private static final URI FALLBACK = URI.create("forward:/fallback");

	@Bean
	RouterFunction<ServerResponse> authRoute() {
		return serviceRoute("auth-service", "/api/auth/**");
	}

	@Bean
	RouterFunction<ServerResponse> productRoute() {
		return serviceRoute("product-service", "/api/products/**");
	}

	@Bean
	RouterFunction<ServerResponse> inventoryRoute() {
		return serviceRoute("inventory-service", "/api/inventory/**");
	}

	@Bean
	RouterFunction<ServerResponse> orderRoute() {
		return serviceRoute("order-service", "/api/orders/**");
	}

	@Bean
	RouterFunction<ServerResponse> notificationRoute() {
		return serviceRoute("notification-service", "/api/notifications/**");
	}

	/**
	 * Proxies requests matching the pattern to an instance of the service. The circuit
	 * breaker answers from the fallback when the service fails or cannot be reached.
	 */
	private static RouterFunction<ServerResponse> serviceRoute(String serviceId, String pattern) {
		return route(serviceId).route(path(pattern), http())
			.before(IdentityHeaders::apply)
			.filter(circuitBreaker(serviceId, FALLBACK))
			.filter(lb(serviceId))
			.build();
	}

}
