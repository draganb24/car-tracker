package com.cartracker.api.dto.response;

import java.math.BigDecimal;

/**
 * Cohort statistics surfaced by GET /stats.
 */
public record CohortStatsResponse(
    String model,
    Integer year,
    int mileageBracketKm,
    long count,
    BigDecimal averagePrice,
    BigDecimal medianPrice,
    BigDecimal minPrice,
    BigDecimal maxPrice
) {
}
