package com.vanmanh49.order.web.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record PlaceOrderRequest(@NotEmpty @Size(max = 50) List<@Valid OrderLine> items) {
}
