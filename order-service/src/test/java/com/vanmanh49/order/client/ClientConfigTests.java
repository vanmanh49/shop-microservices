package com.vanmanh49.order.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;

@SpringBootTest
class ClientConfigTests {

	@Autowired
	private ObjectProvider<RestClient.Builder> defaultBuilder;

	@Autowired
	@LoadBalanced
	private RestClient.Builder loadBalancedBuilder;

	/**
	 * Infrastructure such as the Eureka client takes the default builder to reach fixed
	 * addresses. If that builder resolved host names through the load balancer, the
	 * service could not even reach the registry to register itself.
	 */
	@Test
	void defaultBuilderIsNotLoadBalanced() {
		assertThat(loadBalancerInterceptors(this.defaultBuilder.getIfAvailable())).isEmpty();
	}

	@Test
	void qualifiedBuilderIsLoadBalanced() {
		assertThat(loadBalancerInterceptors(this.loadBalancedBuilder)).hasSize(1);
	}

	private static List<ClientHttpRequestInterceptor> loadBalancerInterceptors(RestClient.Builder builder) {
		List<ClientHttpRequestInterceptor> found = new ArrayList<>();
		builder.clone()
			.requestInterceptors((interceptors) -> interceptors.stream()
				.filter((interceptor) -> interceptor.getClass().getSimpleName().contains("LoadBalancer"))
				.forEach(found::add));
		return found;
	}

}
