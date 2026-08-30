package com.cartracker.api;

import com.cartracker.api.dto.request.ListingQuery;
import com.cartracker.api.dto.response.ListingResponse;
import com.cartracker.api.mapper.ListingMapper;
import com.cartracker.api.service.ListingService;
import com.cartracker.scraper.ScraperService;
import com.cartracker.scraper.dto.response.ScrapeSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/listings")
public class ListingController {

  private final ListingService listingService;
  private final ScraperService scraperService;
  private final ListingMapper mapper = ListingMapper.INSTANCE;


  @GetMapping
  public Page<ListingResponse> list(@ModelAttribute ListingQuery query,
                                    Pageable pageable) {
    return listingService.search(
        query,
        pageable
    ).map(mapper::fromEntityToResponse);
  }

  @PostMapping("/scrape")
  public ResponseEntity<Map<String, Object>> triggerScrape() {
    try {
      ScrapeSummaryResponse s = scraperService.runScrape();
      return ResponseEntity.ok(Map.of(
          "status", "ok",
          "fetched", s.fetched(),
          "inserted", s.inserted(),
          "updated", s.updated(),
          "priceChanges", s.priceChanges(),
          "ranAt", s.ranAt().toString()
      ));
    } catch (Exception e) {
      return ResponseEntity.status(500).body(Map.of(
              "status",
              "error",
              "message",
              e.getMessage()
          )
      );
    }
  }
}
