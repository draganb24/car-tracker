package com.cartracker.api.dto.response;

import com.cartracker.scoring.model.FairPriceVerdict;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Single-listing detail: entity fields + fair-price verdict + price history.
 */
public record ListingDetailResponse(
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
    Boolean isActive,
    FairPriceVerdict fairPrice,
    List<PriceHistoryResponse> priceHistory
) {
}
