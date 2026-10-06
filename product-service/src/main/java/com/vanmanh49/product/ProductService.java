package com.vanmanh49.product;

import com.vanmanh49.product.web.dto.ProductRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProductService {

	private final ProductRepository products;

	public ProductService(ProductRepository products) {
		this.products = products;
	}

	@Transactional(readOnly = true)
	public Page<Product> list(Pageable pageable) {
		return this.products.findAll(pageable);
	}

	@Transactional(readOnly = true)
	public Product get(Long id) {
		return this.products.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
	}

	public Product create(ProductRequest request) {
		if (this.products.existsBySku(request.sku())) {
			throw new DuplicateSkuException(request.sku());
		}
		return this.products
			.save(new Product(request.sku(), request.name(), request.description(), request.price()));
	}

	public Product update(Long id, ProductRequest request) {
		Product product = get(id);
		if (this.products.existsBySkuAndIdNot(request.sku(), id)) {
			throw new DuplicateSkuException(request.sku());
		}
		product.update(request.sku(), request.name(), request.description(), request.price());
		return product;
	}

	public void delete(Long id) {
		this.products.delete(get(id));
	}

}
