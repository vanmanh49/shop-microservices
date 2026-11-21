package com.vanmanh49.inventory;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationOrderRepository extends JpaRepository<ReservationOrder, String> {

	/** Fails with a unique-key error if the order already has a row. */
	@Modifying
	@Query(value = "insert into reservation_orders (order_ref, released) values (:orderRef, false)",
			nativeQuery = true)
	void insertReserved(@Param("orderRef") String orderRef);

	/**
	 * Records the order as released unless it already has a row. If a reservation of the
	 * same order is still running, the database waits for it to finish first.
	 */
	@Modifying
	@Query(value = "insert into reservation_orders (order_ref, released) values (:orderRef, true)"
			+ " on conflict do nothing", nativeQuery = true)
	void insertReleasedIfAbsent(@Param("orderRef") String orderRef);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from ReservationOrder o where o.orderRef = :orderRef")
	ReservationOrder lock(@Param("orderRef") String orderRef);

}
