package com.cartracker.scraper;

import com.cartracker.scraper.dto.response.ScrapeResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Polite scraper for the olx.ba vehicles category (cars).
 *
 * <p>Search pages are fetched sequentially. Detail fetches for new listings are
 * parallelized with bounded concurrency and a reduced per-request delay, while
 * preserving rate limiting between search-page batches.
 */
@Component
public class OlxScraper {

  private static final Logger log = LoggerFactory.getLogger(OlxScraper.class);

  private final String apiBase;
  private final int categoryId;
  private final int delayMillis;
  private final int maxPages;
  private final String userAgent;
  private final HttpClient client;
  private final ObjectMapper mapper;

  public OlxScraper(@Value("${app.scraping.base-url}") String baseUrl,
                    @Value("${app.scraping.category-id:18}") int categoryId,
                    @Value("${app.scraping.delay-ms:500}") int delayMillis,
                    @Value("${app.scraping.max-pages:5}") int maxPages) {
    String host = baseUrl;
    int q = host.indexOf('?');
    if (q >= 0) host = host.substring(0, q);
    int slash = host.indexOf('/', 8);
    this.apiBase = (slash >= 0 ? host.substring(0, slash) : host).replaceAll("/+$", "");
    this.categoryId = categoryId;
    this.delayMillis = Math.max(delayMillis, 0);
    this.maxPages = Math.max(maxPages, 1);
    this.userAgent = "AutoTracker/0.1 (FlyRank capstone; +https://github.com/draganb24/car-tracker)";
    this.client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    this.mapper = new ObjectMapper();
  }

  /**
   * Fetch and parse car listings across up to {@code maxPages} search pages.
   * Detail fetches for new listings run in parallel with bounded concurrency.
   */
  public List<ScrapeResponse> fetchCars(Set<String> knownExternalIds) throws InterruptedException {
    Set<String> known = knownExternalIds == null ? new HashSet<>() : knownExternalIds;
    List<ScrapeResponse> out = new ArrayList<>();
    int pageNo = 1;

    if (delayMillis > 0) Thread.sleep(delayMillis);

    int parallelism = Math.max(2, Math.min(8, maxPages >= 5 ? 6 : maxPages * 2));
    ExecutorService executor = Executors.newFixedThreadPool(parallelism);
    int detailDelay = Math.max(200, delayMillis / 3);

    try {
      while (pageNo <= maxPages) {
        String url = apiBase + "/api/search?category_id=" + categoryId
            + "&page=" + pageNo + "&per_page=60";
        log.info("Scraping search page {}: {}", pageNo, url);

        JsonNode search;
        try {
          search = getJson(url);
        } catch (RuntimeException ex) {
          log.warn("Search page {} failed: {}", pageNo, ex.getMessage(), ex);
          break;
        }

        JsonNode data = search.path("data");
        if (!data.isArray() || data.isEmpty()) {
          log.info("Search page {} returned no items; stopping.", pageNo);
          break;
        }

        List<CompletableFuture<ScrapeResponse>> futures = new ArrayList<>();
        for (JsonNode item : data) {
          String externalId = item.path("id").asText(null);
          if (externalId == null || externalId.isBlank()) continue;
          if (known.contains(externalId)) continue;

          String title = item.path("title").asText(null);
          BigDecimal price = toPrice(item.path("price"));
          if (title == null || price == null) continue;

          CompletableFuture<ScrapeResponse> cf = CompletableFuture.supplyAsync(() -> {
            try {
              if (detailDelay > 0) Thread.sleep(detailDelay);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
            JsonNode detail = fetchDetail(externalId);
            return buildResponse(externalId, title, price, detail);
          }, executor);
          futures.add(cf);
        }

        int added = 0;
        for (CompletableFuture<ScrapeResponse> future : futures) {
          try {
            ScrapeResponse r = future.get(25, TimeUnit.SECONDS);
            if (r != null) {
              out.add(r);
              added++;
            }
          } catch (TimeoutException e) {
            log.warn("Detail fetch timeout on page {}", pageNo);
          } catch (Exception e) {
            log.warn("Detail fetch failed on page {}: {}", pageNo, e.getMessage());
          }
        }

        log.info("Page {} enriched {} new listings (total {})", pageNo, added, out.size());
        if (added == 0) {
          log.info("No new listings on page {}; stopping pagination.", pageNo);
          break;
        }

        pageNo++;
        if (delayMillis > 0 && pageNo <= maxPages) {
          Thread.sleep(detailDelay);
        }
      }
    } finally {
      executor.shutdownNow();
    }

    log.info("Parsed {} new automobile listings across {} page(s)", out.size(), Math.min(pageNo, maxPages));
    return out;
  }

  private JsonNode fetchDetail(String externalId) {
    try {
      return getJson(apiBase + "/api/listings/" + externalId);
    } catch (RuntimeException ex) {
      log.warn("Detail fetch failed for {}: {}", externalId, ex.getMessage());
      return null;
    }
  }

  private ScrapeResponse buildResponse(String externalId,
                                       String title,
                                       BigDecimal price,
                                       JsonNode detail) {
    String brand = null;
    String model = null;
    Integer year = null;
    Integer mileageKm = null;
    String fuelType = null;
    String location = null;
    String url = listingUrl(externalId, detail);

    if (brand == null) brand = deriveBrand(title);
    if (model == null) model = ModelNormalizer.normalize(title);
    else model = ModelNormalizer.normalize(title);

    return new ScrapeResponse(
        externalId, title,
        brand == null ? null : brand.toUpperCase(),
        model,
        price,
        "KM",
        year,
        mileageKm,
        fuelType,
        location,
        url
    );
  }

  private JsonNode getJson(String url) {
    HttpRequest req = HttpRequest.newBuilder(URI.create(url))
        .timeout(Duration.ofSeconds(20))
        .header("User-Agent", userAgent)
        .header("Accept", "application/json")
        .header("Accept-Language", "bs-BA,hr-HR,sr-RS")
        .GET()
        .build();
    try {
      HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
      if (resp.statusCode() != 200) {
        throw new RuntimeException("HTTP " + resp.statusCode() + " from " + url);
      }
      return mapper.readTree(resp.body());
    } catch (java.io.IOException ex) {
      throw new RuntimeException("request to " + url + " failed: " + ex.getMessage(), ex);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("request to " + url + " interrupted", ex);
    } catch (Exception ex) {
      throw new RuntimeException("failed to parse JSON from " + url + ": " + ex.getMessage(), ex);
    }
  }

  private String listingUrl(String externalId, JsonNode detail) {
    return apiBase + "/artikal/" + externalId;
  }

  private BigDecimal toPrice(JsonNode n) {
    if (n == null || n.isNull()) return null;
    if (n.isNumber()) return BigDecimal.valueOf(n.asDouble());
    String digits = n.asText().replaceAll("[^0-9]", "");
    return digits.isEmpty() ? null : new BigDecimal(digits);
  }

  private String deriveBrand(String title) {
    String[] parts = title.split("\\s+");
    return parts.length > 0 ? parts[0] : null;
  }

  private Integer parseYear(String raw) {
    if (raw == null) return null;
    String digits = raw.replaceAll("[^0-9]", "");
    if (digits.length() != 4) return null;
    int y = Integer.parseInt(digits);
    return (y >= 1900 && y <= java.time.Year.now().getValue() + 1) ? y : null;
  }

  private Integer parseKm(String raw) {
    if (raw == null) return null;
    String digits = raw.replaceAll("[^0-9]", "");
    return digits.isEmpty() ? null : Integer.parseInt(digits);
  }
}
