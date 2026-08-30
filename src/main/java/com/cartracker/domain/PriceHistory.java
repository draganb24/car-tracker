package com.cartracker.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "price_history")
@Getter
@Setter
@NoArgsConstructor
public class PriceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;

    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "currency", length = 8)
    private String currency = "KM";

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    public PriceHistory(Listing listing, BigDecimal price, String currency, Instant recordedAt) {
        this.listing = listing;
        this.price = price;
        this.currency = currency;
        this.recordedAt = recordedAt;
    }
}
