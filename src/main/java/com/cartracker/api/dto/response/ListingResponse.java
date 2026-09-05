package com.cartracker.api.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ListingResponse {
  private final Integer id;
  private final String externalId;
  private final String source;
  private final String title;
  private final String brand;
  private final String model;
  private final BigDecimal price;
  private final String currency;
  private final Integer year;
  private final Integer mileageKm;
  private final String fuelType;
  private final String location;
  private final String url;
  private final Instant firstSeenAt;
  private final Instant lastSeenAt;
  private final Boolean isActive;
  private final List<PriceHistoryResponse> priceHistory;
  private final String priceLabel;
  private final BigDecimal cohortAverage;
  private final BigDecimal cohortMedian;
  private final long cohortCount;
  private final BigDecimal deltaPercent;
  private final boolean goodDeal;
  private final int mileageBracketKm;
}
