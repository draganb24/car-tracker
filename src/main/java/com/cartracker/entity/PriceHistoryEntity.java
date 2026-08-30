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
@Table(name = "price_history")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceHistoryEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "price_history_id_seq")
  @SequenceGenerator(
      name = "price_history_id_seq",
      sequenceName = "price_history_id_seq",
      allocationSize = 1
  )
  private Integer id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "listing_id", nullable = false)
  private ListingEntity listing;

  @Column(name = "price", nullable = false, precision = 12, scale = 2)
  private BigDecimal price;

  @Column(name = "currency", length = 8)
  private String currency = "KM";

  @Column(name = "recorded_at", nullable = false)
  private Instant recordedAt;
}
