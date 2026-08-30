package com.cartracker.api.dto.response;

import com.cartracker.scoring.model.FairPriceVerdict;

import java.time.Instant;
import java.util.List;

/**
 * On-demand digest (GET /report) — identical content to the scheduled email.
 */
public record ReportResponse(
    String title,
    Instant generatedAt,
    long totalActiveListings,
    long newSinceLastDigest,
    long goodDealsCount,
    int underpricedThresholdPct,
    List<FairPriceVerdict> goodDeals,
    List<String> notablePriceDrops
) {
}
