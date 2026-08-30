package com.cartracker.report;

import com.cartracker.api.dto.response.ReportResponse;
import com.cartracker.entity.DigestStateEntity;
import com.cartracker.entity.ListingEntity;
import com.cartracker.entity.PriceHistoryEntity;
import com.cartracker.repository.DigestStateRepository;
import com.cartracker.repository.ListingRepository;
import com.cartracker.scoring.PricingService;
import com.cartracker.scoring.model.FairPriceVerdict;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Builds the digest content shared by the scheduled email and the on-demand GET /report.
 * <p>
 * "Good deals" = listings flagged underpriced by {@link PricingService} (cheaper than their
 * cohort average by at least the configured threshold). Price drops = listings whose current
 * price is meaningfully below an older price in their history. A watermark in {@code digest_state}
 * tracks what was already reported, so the digest only surfaces <i>new</i> good deals since the
 * last run and survives restarts.
 */
@Service
public class ReportService {

  private static final Logger log = LoggerFactory.getLogger(ReportService.class);
  private static final String WATERMARK_KEY = "digest.lastSentAt";

  private final ListingRepository listingRepository;
  private final PricingService pricingService;
  private final DigestStateRepository digestStateRepository;
  private final DigestEmailSender emailSender;
  private final int underpricedThresholdPct;

  public ReportService(ListingRepository listingRepository,
                       PricingService pricingService,
                       DigestStateRepository digestStateRepository,
                       DigestEmailSender emailSender,
                       @Value("${app.scoring.underpriced-threshold-pct:10}") int underpricedThresholdPct) {
    this.listingRepository = listingRepository;
    this.pricingService = pricingService;
    this.digestStateRepository = digestStateRepository;
    this.emailSender = emailSender;
    this.underpricedThresholdPct = underpricedThresholdPct;
  }

  /**
   * Read the last digest watermark (null if never sent).
   */
  public Instant lastDigestAt() {
    return digestStateRepository.findById(WATERMARK_KEY)
        .map(DigestStateEntity::getStateValue)
        .map(Instant::parse)
        .orElse(null);
  }

  @Transactional(readOnly = true)
  public ReportResponse build() {
    Instant now = Instant.now();
    Instant since = lastDigestAt();

    long totalActive = listingRepository.countByIsActiveTrue();

    List<FairPriceVerdict> goodDeals = new ArrayList<>();
    List<String> priceDrops = new ArrayList<>();

    for (ListingEntity l : listingRepository.findByIsActiveTrue()) {
      FairPriceVerdict v = pricingService.score(l);
      if (v == null) continue;

      if (v.goodDeal()) {
        if (since == null || l.getFirstSeenAt() == null || l.getFirstSeenAt().isAfter(since)) {
          goodDeals.add(v);
        }
      }
    }

    // Notable price drops across active listings (current price >= 5% below a prior recorded price).
    for (ListingEntity l : listingRepository.findByIsActiveTrue()) {
      if (l.getPrice() == null) continue;
      List<PriceHistoryEntity> history = l.getPriceHistory();
      if (history == null || history.size() < 2) continue;
      BigDecimal lowestPrior = history.stream()
          .map(PriceHistoryEntity::getPrice)
          .filter(p -> p != null && p.compareTo(l.getPrice()) > 0)
          .min(Comparator.naturalOrder())
          .orElse(null);
      if (lowestPrior != null) {
        BigDecimal dropPct = lowestPrior.subtract(l.getPrice())
            .multiply(BigDecimal.valueOf(100))
            .divide(
                lowestPrior,
                0,
                RoundingMode.HALF_UP
            );
        if (dropPct.compareTo(BigDecimal.valueOf(5)) >= 0) {
          priceDrops.add(String.format(
                  "%s — now %s KM (was %s KM, -%s%%)",
                  l.getTitle(),
                  l.getPrice(),
                  lowestPrior,
                  dropPct
              )
          );
        }
      }
    }

    goodDeals.sort(Comparator.comparing(
            FairPriceVerdict::deltaPercent,
            Comparator.nullsLast(Comparator.reverseOrder())
        )
    );

    String title = String.format(
        "Auto Tracker digest — %d active, %d good deals",
        totalActive,
        goodDeals.size()
    );

    return new ReportResponse(
        title,
        now,
        totalActive,
        goodDeals.size(),
        goodDeals.size(),
        underpricedThresholdPct,
        goodDeals,
        priceDrops
    );
  }

  /**
   * Build, email, and advance the watermark — used by the scheduler and the manual trigger.
   */
  @Transactional
  public ReportResponse sendNow() {
    ReportResponse report = build();
    emailSender.send(report);
    markSent(Instant.now());
    log.info("Digest sent ({} good deals, {} price drops)", report.goodDealsCount(), report.notablePriceDrops().size());
    return report;
  }

  /**
   * Advance the watermark to now (call after a digest is sent).
   */
  public void markSent(Instant at) {
    digestStateRepository.save(DigestStateEntity.builder()
        .stateKey(WATERMARK_KEY)
        .stateValue(at.toString())
        .updatedAt(at)
        .build());
  }
}
