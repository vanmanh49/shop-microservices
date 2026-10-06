package com.vanmanh49.order.client.dto;

import java.util.List;

public record ReservationRequest(String orderRef, List<ReservationItem> items) {
}
