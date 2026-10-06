package com.vanmanh49.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ConfigServerApplicationTests {

	@Value("${local.server.port}")
	private int port;

	@Test
	void servesProductServicePort() throws Exception {
		HttpResponse<String> response = get("/product-service/default");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).contains("\"server.port\":8082");
	}

	@Test
	void servesSharedEurekaSetting() throws Exception {
		HttpResponse<String> response = get("/product-service/default");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).contains("eureka.client.service-url.defaultZone");
	}

	private HttpResponse<String> get(String path) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + this.port + path)).build();
		return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
	}

}
