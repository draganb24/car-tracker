package com.cartracker.scraper;

import java.math.BigDecimal;

/**
 * Normalized car listing extracted from a single olx.ba card.
 * Pure data holder — no JPA, no persistence concern.
 */
public class ScrapeResult {

    private final String externalId;
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

    public ScrapeResult(String externalId, String title, String brand, String model,
                        BigDecimal price, String currency, Integer year, Integer mileageKm,
                        String fuelType, String location, String url) {
        this.externalId = externalId;
        this.title = title;
        this.brand = brand;
        this.model = model;
        this.price = price;
        this.currency = currency;
        this.year = year;
        this.mileageKm = mileageKm;
        this.fuelType = fuelType;
        this.location = location;
        this.url = url;
    }

    public String getExternalId() { return externalId; }
    public String getTitle() { return title; }
    public String getBrand() { return brand; }
    public String getModel() { return model; }
    public BigDecimal getPrice() { return price; }
    public String getCurrency() { return currency; }
    public Integer getYear() { return year; }
    public Integer getMileageKm() { return mileageKm; }
    public String getFuelType() { return fuelType; }
    public String getLocation() { return location; }
    public String getUrl() { return url; }
}
