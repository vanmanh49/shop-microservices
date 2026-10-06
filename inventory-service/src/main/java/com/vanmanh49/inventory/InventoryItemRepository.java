package com.vanmanh49.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {

	/**
	 * Takes stock only if enough is available. The check and the write are one statement,
	 * so concurrent reservations cannot oversell.
	 * @return 1 if the stock was taken, 0 if the product is unknown or short
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update InventoryItem i set i.available = i.available - :quantity"
			+ " where i.productId = :productId and i.available >= :quantity")
	int decrement(@Param("productId") Long productId, @Param("quantity") int quantity);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("update InventoryItem i set i.available = i.available + :quantity where i.productId = :productId")
	int increment(@Param("productId") Long productId, @Param("quantity") int quantity);

}
