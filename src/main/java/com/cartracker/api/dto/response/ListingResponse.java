package com.cartracker.api.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

public record ListingResponse(
    Integer id,
    String externalId,
    String source,
    String title,
    String brand,
    String model,
    BigDecimal price,
    String currency,
    Integer year,
    Integer mileageKm,
    String fuelType,
    String location,
    String url,
    Instant firstSeenAt,
    Instant lastSeenAt,
    Boolean isActive
) {
}
