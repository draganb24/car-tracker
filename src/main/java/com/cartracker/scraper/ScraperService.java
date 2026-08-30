package com.cartracker.scraper;

import com.cartracker.domain.Listing;
import com.cartracker.domain.PriceHistory;
import com.cartracker.repository.ListingRepository;
import com.cartracker.repository.PriceHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Turns parsed {@link ScrapeResult}s into persisted {@link Listing}s.
 *
 * Idempotency: keyed by {@code externalId}. Re-running a scrape never duplicates a row.
 * Price history: when an existing listing's price changes, the OLD price is kept as a
 * new {@link PriceHistory} row — current price lives on {@link Listing#getPrice()}.
 */
@Service
public class ScraperService {

    private static final Logger log = LoggerFactory.getLogger(ScraperService.class);

    private final OlxScraper scraper;
    private final ListingRepository listingRepository;
    private final PriceHistoryRepository priceHistoryRepository;

    public ScraperService(OlxScraper scraper,
                          ListingRepository listingRepository,
                          PriceHistoryRepository priceHistoryRepository) {
        this.scraper = scraper;
        this.listingRepository = listingRepository;
        this.priceHistoryRepository = priceHistoryRepository;
    }

    @Transactional
    public ScrapeSummary runScrape() throws Exception {
        List<ScrapeResult> results = scraper.fetchCars();
        int inserted = 0;
        int updated = 0;
        int priceChanges = 0;

        Instant now = Instant.now();
        for (ScrapeResult r : results) {
            Listing existing = listingRepository.findByExternalId(r.getExternalId()).orElse(null);
            if (existing == null) {
                Listing created = newListing(r, now);
                listingRepository.save(created);
                priceHistoryRepository.save(new PriceHistory(created, r.getPrice(), r.getCurrency(), now));
                inserted++;
                continue;
            }

            boolean priceChanged = r.getPrice() != null
                    && existing.getPrice() != null
                    && r.getPrice().compareTo(existing.getPrice()) != 0;

            // Refresh mutable fields
            existing.setTitle(r.getTitle());
            existing.setBrand(r.getBrand());
            existing.setModel(r.getModel());
            existing.setCurrency(r.getCurrency());
            existing.setYear(r.getYear());
            existing.setMileageKm(r.getMileageKm());
            existing.setFuelType(r.getFuelType());
            existing.setLocation(r.getLocation());
            existing.setUrl(r.getUrl());
            existing.setLastSeenAt(now);
            existing.setActive(true);
            existing.setUpdatedAt(now);

            if (priceChanged) {
                priceHistoryRepository.save(new PriceHistory(existing, r.getPrice(), r.getCurrency(), now));
                existing.setPrice(r.getPrice());
                priceChanges++;
            }
            listingRepository.save(existing);
            updated++;
        }

        log.info("Scrape complete: {} new, {} updated, {} price changes", inserted, updated, priceChanges);
        return new ScrapeSummary(results.size(), inserted, updated, priceChanges, now);
    }

    private Listing newListing(ScrapeResult r, Instant now) {
        Listing l = new Listing(r.getExternalId());
        l.setSource("olx.ba");
        l.setTitle(r.getTitle());
        l.setBrand(r.getBrand());
        l.setModel(r.getModel());
        l.setPrice(r.getPrice());
        l.setCurrency(r.getCurrency());
        l.setYear(r.getYear());
        l.setMileageKm(r.getMileageKm());
        l.setFuelType(r.getFuelType());
        l.setLocation(r.getLocation());
        l.setUrl(r.getUrl());
        l.setFirstSeenAt(now);
        l.setLastSeenAt(now);
        l.setActive(true);
        l.setCreatedAt(now);
        l.setUpdatedAt(now);
        return l;
    }

    public record ScrapeSummary(int fetched, int inserted, int updated, int priceChanges, Instant ranAt) {
    }
}
