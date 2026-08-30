package com.cartracker.api;

import com.cartracker.api.dto.response.CohortStatsResponse;
import com.cartracker.api.dto.response.ReportResponse;
import com.cartracker.common.error.exception.InvalidParamException;
import com.cartracker.report.ReportService;
import com.cartracker.scraper.ScraperService;
import com.cartracker.scraper.dto.response.ScrapeSummaryResponse;
import com.cartracker.scoring.PricingService;
import com.cartracker.scoring.model.CohortStats;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Reporting + cohort endpoints at the API root:
 * GET /stats   — cached cohort average/median/count for a model/year
 * GET /report  — on-demand digest, identical content to the scheduled email
 * POST /scrape — manual scrape trigger
 * POST /digest/send — manual digest trigger (emails + advances watermark)
 *
 * Error responses (404/400/500) are produced centrally by {@code RestExceptionHandler}.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/")
public class ReportController {

  private final ScraperService scraperService;
  private final PricingService pricingService;
  private final ReportService reportService;

  @GetMapping("/stats")
  public CohortStatsResponse stats(@RequestParam(required = false) String model,
                                   @RequestParam(required = false) Integer year,
                                   @RequestParam(required = false) Integer mileageKm) {
    if (model == null || model.isBlank()) {
      throw new InvalidParamException("model is required");
    }
    CohortStats stats = pricingService.cohortStats(model, year, mileageKm);
    if (stats == null || stats.count() == 0) {
      throw new InvalidParamException("no cohort data for the given model/year");
    }
    return new CohortStatsResponse(
        stats.model(),
        stats.year(),
        stats.mileageBracketKm(),
        stats.count(),
        stats.averagePrice(),
        stats.medianPrice(),
        stats.minPrice(),
        stats.maxPrice()
    );
  }

  @GetMapping("/report")
  public ReportResponse report() {
    return reportService.build();
  }

  @PostMapping("/scrape")
  public ResponseEntity<Map<String, Object>> triggerScrape() {
    ScrapeSummaryResponse s = scraperService.runScrape();
    return ResponseEntity.ok(Map.of(
        "status", "ok",
        "fetched", s.fetched(),
        "inserted", s.inserted(),
        "updated", s.updated(),
        "priceChanges", s.priceChanges(),
        "ranAt", s.ranAt().toString()
    ));
  }

  @PostMapping("/digest/send")
  public ResponseEntity<Map<String, Object>> sendDigestNow() {
    var report = reportService.sendNow();
    return ResponseEntity.ok(Map.of(
        "status", "ok",
        "title", report.title(),
        "goodDeals", report.goodDealsCount(),
        "priceDrops", report.notablePriceDrops().size()
    ));
  }
}
