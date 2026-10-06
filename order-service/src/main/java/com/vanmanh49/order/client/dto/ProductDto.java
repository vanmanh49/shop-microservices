package com.vanmanh49.order.client.dto;

import java.math.BigDecimal;

/** The part of product-service's product representation that orders need. */
public record ProductDto(Long id, String name, BigDecimal price) {
}
