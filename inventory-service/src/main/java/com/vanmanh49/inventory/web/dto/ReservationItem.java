package com.vanmanh49.inventory.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ReservationItem(@NotNull Long productId, @Min(1) int quantity) {
}
