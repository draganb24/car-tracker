package com.cartracker.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "listing", uniqueConstraints = @UniqueConstraint(name = "uk_listing_external_id", columnNames = "external_id"))
@Getter
@Setter
@NoArgsConstructor
public class Listing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, updatable = false)
    private String externalId;

    @Column(name = "source", nullable = false)
    private String source = "olx.ba";

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "brand")
    private String brand;

    @Column(name = "model")
    private String model;

    @Column(name = "price", precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "currency", length = 8)
    private String currency = "KM";

    @Column(name = "year")
    private Integer year;

    @Column(name = "mileage_km")
    private Integer mileageKm;

    @Column(name = "fuel_type", length = 32)
    private String fuelType;

    @Column(name = "location", length = 128)
    private String location;

    @Column(name = "url", length = 1024)
    private String url;

    @Column(name = "first_seen_at")
    private Instant firstSeenAt;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Column(name = "active")
    private boolean active = true;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public Listing(String externalId) {
        this.externalId = externalId;
    }
}
