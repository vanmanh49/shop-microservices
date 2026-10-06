package com.vanmanh49.inventory.web.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record ReservationRequest(@NotBlank @Size(max = 64) String orderRef,
		@NotEmpty List<@Valid ReservationItem> items) {
}
