package com.cartracker.scoring.model;

import java.math.BigDecimal;

/**
 * A single listing scored against its comparable cohort.
 * {@code deltaPercent} is positive when the listing is CHEAPER than the cohort average.
 */
public record FairPriceVerdict(
    String model,
    Integer year,
    Integer mileageKm,
    BigDecimal price,
    String priceLabel,        // "underpriced" | "overpriced" | "fair_price"
    BigDecimal cohortAverage,
    BigDecimal cohortMedian,
    long cohortCount,
    BigDecimal deltaPercent,  // positive => cheaper than avg; negative => pricier
    boolean goodDeal,         // true when deltaPercent >= threshold (meaningfully underpriced)
    int mileageBracketKm
) {
}
