package com.cartracker.api;

import com.cartracker.api.dto.ListingDto;
import com.cartracker.api.mapper.ListingMapper;
import com.cartracker.domain.Listing;
import com.cartracker.repository.ListingRepository;
import com.cartracker.scraper.ScraperService;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/listings")
public class ListingController {

    private final ListingRepository listingRepository;
    private final ScraperService scraperService;
    private final ListingMapper mapper = ListingMapper.INSTANCE;

    public ListingController(ListingRepository listingRepository, ScraperService scraperService) {
        this.listingRepository = listingRepository;
        this.scraperService = scraperService;
    }

    @GetMapping
    public Page<ListingDto> list(
            @RequestParam(required = false) String model,
            @RequestParam(required = false) Integer minYear,
            @RequestParam(required = false) Integer maxYear,
            @RequestParam(required = false) String fuelType,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            Pageable pageable) {

        Specification<Listing> spec = (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            if (model != null) preds.add(cb.like(cb.lower(root.get("model")), "%" + model.toLowerCase() + "%"));
            if (minYear != null) preds.add(cb.greaterThanOrEqualTo(root.get("year"), minYear));
            if (maxYear != null) preds.add(cb.lessThanOrEqualTo(root.get("year"), maxYear));
            if (fuelType != null) preds.add(cb.equal(cb.lower(root.get("fuelType")), fuelType.toLowerCase()));
            if (minPrice != null) preds.add(cb.greaterThanOrEqualTo(root.get("price"), minPrice));
            if (maxPrice != null) preds.add(cb.lessThanOrEqualTo(root.get("price"), maxPrice));
            return cb.and(preds.toArray(new Predicate[0]));
        };

        return listingRepository.findAll(spec, pageable).map(mapper::toDto);
    }

    @PostMapping("/scrape")
    public ResponseEntity<Map<String, Object>> triggerScrape() {
        try {
            ScraperService.ScrapeSummary s = scraperService.runScrape();
            return ResponseEntity.ok(Map.of(
                    "status", "ok",
                    "fetched", s.fetched(),
                    "inserted", s.inserted(),
                    "updated", s.updated(),
                    "priceChanges", s.priceChanges(),
                    "ranAt", s.ranAt().toString()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}
