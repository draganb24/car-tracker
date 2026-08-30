package com.cartracker.scoring;

import com.cartracker.entity.ListingEntity;
import com.cartracker.repository.ListingRepository;
import com.cartracker.scoring.model.CohortStats;
import com.cartracker.scoring.model.FairPriceVerdict;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Fair-price scoring engine.
 * <p>
 * A <b>cohort</b> is the set of active, priced listings sharing
 * (model, year ± yearTolerance, mileageBracket). Its statistics (count, average,
 * median, min, max) are computed on demand and <b>cached</b> via Spring Cache keyed by a
 * {@link CohortSignature} — never recomputed per request.
 * <p>
 * Scoring: a listing's price is compared to its cohort average. When it sits
 * {@code >= underpricedThresholdPct} below the average it is flagged a "good deal".
 * The logic is simple, explainable statistics (no ML) — exactly what the capstone calls for.
 */
@Service
public class PricingService {

  private static final Logger log = LoggerFactory.getLogger(PricingService.class);

  /**
   * Width of the mileage bracket used to define a comparable cohort.
   */
  private static final int MILEAGE_BRACKET = 25_000;

  private final ListingRepository listingRepository;
  private final int yearTolerance;
  private final int underpricedThresholdPct;


  public PricingService(ListingRepository listingRepository,
                        @Value("${app.scoring.year-tolerance:2}") int yearTolerance,
                        @Value("${app.scoring.underpriced-threshold-pct:10}") int underpricedThresholdPct) {
    this.listingRepository = listingRepository;
    this.yearTolerance = Math.max(
        yearTolerance,
        0
    );
    this.underpricedThresholdPct = Math.max(
        underpricedThresholdPct,
        1
    );
  }

  /**
   * Cached cohort statistics for a model/year/mileage combination.
   */
  @Cacheable(cacheNames = "cohortStats", key = "#root.args[0] + '|' + #root.args[1] + '|' + #root.args[2]")
  public CohortStats cohortStats(String model,
                                 Integer year,
                                 Integer mileageKm) {
    if (model == null || model.isBlank() || year == null) {
      return null;
    }
    int bracket = mileageBracket(mileageKm);
    List<ListingEntity> cohort;
    if (mileageKm == null) {
      // /stats with no mileage: compare across the whole model+year cohort.
      cohort = listingRepository.findCohortIgnoringMileage(
          model,
          year - yearTolerance,
          year + yearTolerance
      );
    } else {
      cohort = listingRepository.findCohort(
          model,
          year - yearTolerance,
          year + yearTolerance,
          bracket,
          bracket + (MILEAGE_BRACKET - 1)
      );
    }
    return computeStats(
        model,
        year,
        bracket,
        cohort
    );
  }

  /**
   * Score a single listing against its cohort. Returns null when it can't be scored.
   */
  public FairPriceVerdict score(ListingEntity listing) {
    if (listing == null || listing.getModel() == null || listing.getYear() == null
        || listing.getPrice() == null || listing.getMileageKm() == null) {
      return null;
    }
    CohortStats stats = cohortStats(
        listing.getModel(),
        listing.getYear(),
        listing.getMileageKm()
    );
    if (stats == null || stats.count() < 2) {
      // Need at least 2 comparables to say anything meaningful.
      return new FairPriceVerdict(
          listing.getModel(),
          listing.getYear(),
          listing.getMileageKm(),
          listing.getPrice(),
          "insufficient_data",
          null,
          null,
          stats == null ? 0 : stats.count(),
          null,
          false,
          mileageBracket(listing.getMileageKm())
      );
    }

    BigDecimal delta = stats.percentBelow(listing.getPrice()); // positive => cheaper than avg
    boolean goodDeal = delta != null && delta.compareTo(BigDecimal.valueOf(underpricedThresholdPct)) >= 0;
    String label = goodDeal ? "underpriced"
        : (delta != null && delta.compareTo(BigDecimal.ZERO) < 0 ? "overpriced" : "fair_price");

    return new FairPriceVerdict(
        listing.getModel(),
        listing.getYear(),
        listing.getMileageKm(),
        listing.getPrice(),
        label,
        stats.averagePrice(),
        stats.medianPrice(),
        stats.count(),
        delta,
        goodDeal,
        stats.mileageBracketKm()
    );
  }

  private CohortStats computeStats(String model,
                                   Integer year,
                                   int bracket,
                                   List<ListingEntity> cohort) {
    if (cohort.isEmpty()) {
      return new CohortStats(
          model,
          year,
          bracket,
          0,
          null,
          null,
          null,
          null,
          List.of()
      );
    }
    List<BigDecimal> prices = new ArrayList<>();
    BigDecimal min = null, max = null, sum = BigDecimal.ZERO;
    for (ListingEntity l : cohort) {
      BigDecimal p = l.getPrice();
      if (p == null) continue;
      prices.add(p);
      sum = sum.add(p);
      if (min == null || p.compareTo(min) < 0) min = p;
      if (max == null || p.compareTo(max) > 0) max = p;
    }
    if (prices.isEmpty()) {
      return new CohortStats(
          model,
          year,
          bracket,
          0,
          null,
          null,
          null,
          null,
          List.of()
      );
    }
    BigDecimal avg = sum.divide(
        BigDecimal.valueOf(prices.size()),
        2,
        RoundingMode.HALF_UP
    );
    BigDecimal median = median(prices);
    return new CohortStats(
        model,
        year,
        bracket,
        prices.size(),
        avg,
        median,
        min,
        max,
        prices
    );
  }

  private BigDecimal median(List<BigDecimal> sortedInput) {
    List<BigDecimal> sorted = new ArrayList<>(sortedInput);
    sorted.sort(BigDecimal::compareTo);
    int n = sorted.size();
    if (n % 2 == 1) {
      return sorted.get(n / 2).setScale(
          2,
          RoundingMode.HALF_UP
      );
    }
    BigDecimal a = sorted.get(n / 2 - 1);
    BigDecimal b = sorted.get(n / 2);
    return a.add(b).divide(
        BigDecimal.valueOf(2),
        2,
        RoundingMode.HALF_UP
    );
  }

  /**
   * Cohort mileage bracket = floor(mileage / 25_000) * 25_000.
   */
  int mileageBracket(Integer mileageKm) {
    if (mileageKm == null) return 0;
    return (mileageKm / MILEAGE_BRACKET) * MILEAGE_BRACKET;
  }
}
