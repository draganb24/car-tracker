package com.cartracker.scraper;

import com.cartracker.scraper.dto.response.ScrapeSummaryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Off-request-path trigger. Runs the scrape on a cron (default every 2 hours).
 * The cron expression is overridable via app.scraping.cron.
 */
@Component
public class ScrapeScheduler {

  private static final Logger log = LoggerFactory.getLogger(ScrapeScheduler.class);

  private final ScraperService scraperService;
  private final String cron;


  public ScrapeScheduler(ScraperService scraperService,
                         @Value("${app.scraping.cron:0 0 */2 * * *}") String cron) {
    this.scraperService = scraperService;
    this.cron = cron;
    log.info("ScrapeScheduler initialized with cron='{}'", cron);
  }

  @Scheduled(cron = "${app.scraping.cron:0 0 */2 * * *}")
  public void scheduledScrape() {
    try {
      log.info("Scheduled scrape starting (cron='{}')", cron);
      ScrapeSummaryResponse s = scraperService.runScrape();
      log.info("Scheduled scrape done: {}", s);
    } catch (Exception e) {
      log.error("Scheduled scrape failed: {}", e.getMessage(), e);
    }
  }
}
