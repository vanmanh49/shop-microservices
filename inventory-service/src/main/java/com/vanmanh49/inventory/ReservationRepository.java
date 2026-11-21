package com.vanmanh49.inventory;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

	// Callers hold the lock on the order's ReservationOrder row.
	List<Reservation> findByOrderRef(String orderRef);

}
