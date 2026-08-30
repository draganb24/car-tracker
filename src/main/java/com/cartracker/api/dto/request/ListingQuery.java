package com.cartracker.api.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class ListingQuery {
  private String model;
  private Integer minYear;
  private Integer maxYear;
  private String fuelType;
  private BigDecimal minPrice;
  private BigDecimal maxPrice;
}
