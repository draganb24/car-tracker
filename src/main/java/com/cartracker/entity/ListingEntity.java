package com.cartracker.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "listing",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_listing_external_id",
        columnNames = "external_id"
    )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListingEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "listing_id_seq")
  @SequenceGenerator(
      name = "listing_id_seq",
      sequenceName = "listing_id_seq",
      allocationSize = 1
  )
  private Integer id;

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

  @Column(name = "is_active")
  private Boolean isActive = true;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "updated_at")
  private Instant updatedAt;
}
