package com.vanmanh49.product;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {

	boolean existsBySku(String sku);

	boolean existsBySkuAndIdNot(String sku, Long id);

	List<Product> findByIdInOrderById(Collection<Long> ids);

}
