package com.cartracker.scraper.dto.response;

import java.math.BigDecimal;

/**
 * Normalized car listing extracted from a single olx.ba card.
 * Pure data holder — no JPA, no persistence concern.
 */
public record ScrapeResponse(
    String externalId,
    String title,
    String brand,
    String model,
    BigDecimal price,
    String currency,
    Integer year,
    Integer mileageKm,
    String fuelType,
    String location,
    String url
) {
}
