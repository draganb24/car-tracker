package com.cartracker.scraper;

import com.cartracker.entity.ListingEntity;
import com.cartracker.entity.PriceHistoryEntity;
import com.cartracker.repository.ListingRepository;
import com.cartracker.repository.PriceHistoryRepository;
import com.cartracker.scraper.dto.response.ScrapeResponse;
import com.cartracker.scraper.dto.response.ScrapeSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns parsed {@link ScrapeResponse}s into persisted {@link ListingEntity}s.
 * <p>
 * Idempotency: keyed by {@code externalId}. Re-running a scrape never duplicates a row.
 * Price history: when an existing listing's price changes, the OLD price is kept as a
 * new {@link PriceHistoryEntity} row — current price lives on {@link ListingEntity#getPrice()}.
 */
@Service
@RequiredArgsConstructor
public class ScraperService {

  private static final Logger log = LoggerFactory.getLogger(ScraperService.class);

  private final OlxScraper scraper;
  private final ListingRepository listingRepository;
  private final PriceHistoryRepository priceHistoryRepository;


  @Transactional
  public ScrapeSummaryResponse runScrape() {
    Set<String> knownIds = new HashSet<>(listingRepository.findAllExternalIds());
    List<ScrapeResponse> results;
    try {
      results = scraper.fetchCars(knownIds);
    } catch (Exception e) {
      throw new RuntimeException("scrape failed: " + e.getMessage(), e);
    }
    int inserted = 0;
    int updated = 0;
    int priceChanges = 0;

    Instant now = Instant.now();
    for (ScrapeResponse r : results) {
      ListingEntity existing = listingRepository.findByExternalId(r.externalId()).orElse(null);
      if (existing == null) {
        ListingEntity created = newListing(r, now);
        listingRepository.save(created);
        priceHistoryRepository.save(PriceHistoryEntity.builder()
            .listing(created)
            .price(r.price())
            .currency(r.currency())
            .recordedAt(now)
            .build());
        inserted++;
        continue;
      }

      boolean priceChanged = r.price() != null
          && existing.getPrice() != null
          && r.price().compareTo(existing.getPrice()) != 0;

      existing.setTitle(r.title());
      existing.setBrand(r.brand());
      existing.setModel(r.model());
      existing.setCurrency(r.currency());
      existing.setYear(r.year());
      existing.setMileageKm(r.mileageKm());
      existing.setFuelType(r.fuelType());
      existing.setLocation(r.location());
      existing.setUrl(r.url());
      existing.setLastSeenAt(now);
      existing.setIsActive(true);
      existing.setUpdatedAt(now);

      if (priceChanged) {
        priceHistoryRepository.save(PriceHistoryEntity.builder()
            .listing(existing)
            .price(r.price())
            .currency(r.currency())
            .recordedAt(now)
            .build());
        existing.setPrice(r.price());
        priceChanges++;
      }
      listingRepository.save(existing);
      updated++;
    }

    log.info("Scrape complete: {} new, {} updated, {} price changes", inserted, updated, priceChanges);
    return new ScrapeSummaryResponse(
        results.size(),
        inserted,
        updated,
        priceChanges,
        now
    );
  }

  private ListingEntity newListing(ScrapeResponse r,
                                   Instant now) {
    return ListingEntity.builder()
        .externalId(r.externalId())
        .source("olx.ba")
        .title(r.title())
        .brand(r.brand())
        .model(r.model())
        .price(r.price())
        .currency(r.currency())
        .year(r.year())
        .mileageKm(r.mileageKm())
        .fuelType(r.fuelType())
        .location(r.location())
        .url(r.url())
        .firstSeenAt(now)
        .lastSeenAt(now)
        .isActive(true)
        .createdAt(now)
        .updatedAt(now)
        .build();
  }
}
