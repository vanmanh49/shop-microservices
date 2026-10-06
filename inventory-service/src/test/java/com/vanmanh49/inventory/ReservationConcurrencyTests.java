package com.vanmanh49.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.vanmanh49.inventory.web.dto.ReservationItem;
import com.vanmanh49.inventory.web.dto.ReservationRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ReservationConcurrencyTests {

	private static final long PRODUCT_ID = 9001;

	@Autowired
	private InventoryService inventory;

	@AfterEach
	void cleanUp() {
		this.inventory.release("race-a");
		this.inventory.release("race-b");
		this.inventory.setStock(PRODUCT_ID, 0);
	}

	@Test
	void onlyOneOfTwoConcurrentReservationsWins() throws Exception {
		this.inventory.setStock(PRODUCT_ID, 1);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<Boolean> first = executor.submit(reserveAfter(start, "race-a"));
			Future<Boolean> second = executor.submit(reserveAfter(start, "race-b"));
			start.countDown();

			List<Boolean> outcomes = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

			assertThat(outcomes).containsExactlyInAnyOrder(true, false);
			assertThat(this.inventory.get(PRODUCT_ID).getAvailable()).isZero();
		}
		finally {
			executor.shutdownNow();
		}
	}

	private Callable<Boolean> reserveAfter(CountDownLatch start, String orderRef) {
		return () -> {
			start.await();
			try {
				this.inventory.reserve(new ReservationRequest(orderRef, List.of(new ReservationItem(PRODUCT_ID, 1))));
				return true;
			}
			catch (InsufficientStockException ex) {
				return false;
			}
		};
	}

}
