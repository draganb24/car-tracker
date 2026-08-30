package com.cartracker.scoring.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Computed statistics for a comparable cohort of listings
 * (same model, similar year, similar mileage bracket).
 * <p>
 * Not persisted — recomputed on demand and cached by {@link com.cartracker.scoring.PricingService}.
 */
public record CohortStats(
    String model,
    Integer year,
    int mileageBracketKm,
    long count,
    BigDecimal averagePrice,
    BigDecimal medianPrice,
    BigDecimal minPrice,
    BigDecimal maxPrice,
    List<BigDecimal> prices
) {

  /**
   * % a given price sits BELOW the cohort average; positive => cheaper than the cohort.
   */
  public BigDecimal percentBelow(BigDecimal price) {
    if (averagePrice == null || price == null || averagePrice.signum() == 0) {
      return null;
    }
    return averagePrice.subtract(price)
        .multiply(BigDecimal.valueOf(100))
        .divide(averagePrice, 2, java.math.RoundingMode.HALF_UP);
  }

}
