package com.cartracker.report;

import com.cartracker.api.dto.response.ReportResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Renders a {@link ReportResponse} as a plain-text email and sends it via the configured
 * SMTP server (Mailhog in dev). Failures are logged, never thrown into the scheduler loop.
 */
@Component
public class DigestEmailSender {

  private static final Logger log = LoggerFactory.getLogger(DigestEmailSender.class);

  private final JavaMailSender mailSender;
  private final String from;
  private final String to;


  public DigestEmailSender(JavaMailSender mailSender,
                           @Value("${app.mail.from:auto-tracker@localhost}") String from,
                           @Value("${app.mail.to:}") String to) {
    this.mailSender = mailSender;
    this.from = from;
    this.to = (to == null || to.isBlank()) ? from : to;
  }

  public void send(ReportResponse report) {
    try {
      SimpleMailMessage msg = new SimpleMailMessage();
      msg.setFrom(from);
      msg.setTo(to);
      msg.setSubject(report.title());
      msg.setText(render(report));
      mailSender.send(msg);
      log.info("Digest email sent ({} good deals, {} price drops) -> {}",
          report.goodDealsCount(), report.notablePriceDrops().size(), to);
    } catch (Exception e) {
      log.error("Failed to send digest email: {}", e.getMessage(), e);
    }
  }

  String render(ReportResponse r) {
    StringBuilder sb = new StringBuilder();
    sb.append(r.title()).append('\n');
    sb.append("Generated: ").append(r.generatedAt()).append('\n');
    sb.append("Underpriced threshold: ").append(r.underpricedThresholdPct()).append("%\n\n");

    sb.append("GOOD DEALS (underpriced vs comparable cohort)\n");
    sb.append("---------------------------------------------\n");
    if (r.goodDeals().isEmpty()) {
      sb.append("No new good deals since the last digest.\n");
    } else {
      for (var d : r.goodDeals()) {
        sb.append(String.format(
            "- %s %s (%d, %d km) — %s KM, %s%% below cohort avg %s KM [%s]\n",
            d.model(), d.year(), d.year(), d.mileageKm(), d.price(),
            d.deltaPercent(), d.cohortAverage(), d.priceLabel()));
      }
    }

    sb.append("\nNOTABLE PRICE DROPS\n");
    sb.append("-------------------\n");
    if (r.notablePriceDrops().isEmpty()) {
      sb.append("No notable price drops.\n");
    } else {
      for (String s : r.notablePriceDrops()) {
        sb.append("- ").append(s).append('\n');
      }
    }
    return sb.toString();
  }
}
