package com.vanmanh49.inventory;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

	List<Reservation> findByOrderRef(String orderRef);

	boolean existsByOrderRef(String orderRef);

}
