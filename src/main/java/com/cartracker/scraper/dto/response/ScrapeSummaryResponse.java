package com.cartracker.scraper.dto.response;

import java.time.Instant;

public record ScrapeSummaryResponse(
    int fetched,
    int inserted,
    int updated,
    int priceChanges,
    Instant ranAt
) {
}
