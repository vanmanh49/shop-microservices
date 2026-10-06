package com.vanmanh49.inventory;

import java.util.List;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

	List<Reservation> findByOrderRef(String orderRef);

	/**
	 * Reads the reservations of an order and locks them until the transaction ends. A
	 * second release of the same order waits here and then finds nothing left to return.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from Reservation r where r.orderRef = :orderRef")
	List<Reservation> lockByOrderRef(@Param("orderRef") String orderRef);

	boolean existsByOrderRef(String orderRef);

}
