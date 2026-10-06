package com.vanmanh49.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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
class ReleaseConcurrencyTests {

	private static final long PRODUCT_ID = 9201;

	private static final int ROUNDS = 25;

	@Autowired
	private InventoryService inventory;

	@AfterEach
	void cleanUp() {
		this.inventory.setStock(PRODUCT_ID, 0);
	}

	/** A reservation released twice at once (a double-clicked cancel) is returned once. */
	@Test
	void concurrentReleasesReturnTheStockOnce() throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			for (int round = 0; round < ROUNDS; round++) {
				String orderRef = "release-race-" + round;
				this.inventory.setStock(PRODUCT_ID, 5);
				this.inventory.reserve(new ReservationRequest(orderRef, List.of(new ReservationItem(PRODUCT_ID, 3))));
				CountDownLatch start = new CountDownLatch(1);
				Future<?> first = executor.submit(() -> releaseAfter(start, orderRef));
				Future<?> second = executor.submit(() -> releaseAfter(start, orderRef));
				start.countDown();
				first.get(10, TimeUnit.SECONDS);
				second.get(10, TimeUnit.SECONDS);

				assertThat(this.inventory.get(PRODUCT_ID).getAvailable()).as("stock after round %d", round)
					.isEqualTo(5);
			}
		}
		finally {
			executor.shutdownNow();
		}
	}

	private void releaseAfter(CountDownLatch start, String orderRef) {
		try {
			start.await();
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
		this.inventory.release(orderRef);
	}

}
