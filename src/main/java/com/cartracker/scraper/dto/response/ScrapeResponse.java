package com.cartracker.scraper.dto.response;

import java.math.BigDecimal;

import lombok.Builder;

/**
 * Normalized car listing extracted from a single olx.ba card.
 * Pure data holder — no JPA, no persistence concern.
 */
@Builder
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