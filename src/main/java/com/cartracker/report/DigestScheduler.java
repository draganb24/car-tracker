package com.cartracker.report;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Off-request-path digest job. Builds the report, sends the email, and advances the
 * watermark so the next run only reports what's new. Cron overridable via app.digest.cron.
 */
@Component
public class DigestScheduler {

  private static final Logger log = LoggerFactory.getLogger(DigestScheduler.class);

  private final ReportService reportService;
  private final String cron;


  public DigestScheduler(ReportService reportService,
                         @Value("${app.digest.cron:0 7 * * * ?}") String cron) {
    this.reportService = reportService;
    this.cron = cron;
    log.info("DigestScheduler initialized with cron='{}'", cron);
  }

  @Scheduled(cron = "${app.digest.cron:0 7 * * * ?}")
  public void sendDigest() {
    try {
      log.info("Scheduled digest starting (cron='{}')", cron);
      reportService.sendNow();
      log.info("Scheduled digest done");
    } catch (Exception e) {
      log.error("Scheduled digest failed: {}", e.getMessage(), e);
    }
  }
}
