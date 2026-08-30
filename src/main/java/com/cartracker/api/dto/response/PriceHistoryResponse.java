package com.cartracker.api.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One recorded price point in a listing's history.
 */
public record PriceHistoryResponse(
    BigDecimal price,
    String currency,
    Instant recordedAt
) {
}
