package com.vanmanh49.order.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OrderLine(@NotNull Long productId, @Min(1) @Max(10_000) int quantity) {
}
