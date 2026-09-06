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
import java.util.Locale;
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

        List<ScrapeResponse> base = new ArrayList<>();
        List<CompletableFuture<ScrapeResponse>> futures = new ArrayList<>();
        for (JsonNode item : data) {
          String externalId = item.path("id").asText(null);
          if (externalId == null || externalId.isBlank()) continue;

          String title = item.path("title").asText(null);
          BigDecimal price = toPrice(item.path("price"));
          if (title == null || price == null) continue;

          SearchAttrs attrs = parseSearchAttrs(item);
          base.add(ScrapeResponse.builder()
              .externalId(externalId)
              .title(title)
              .brand(deriveBrand(title))
              .model(ModelNormalizer.normalize(title))
              .price(price)
              .currency("KM")
              .year(attrs.year())
              .mileageKm(attrs.mileageKm())
              .fuelType(attrs.fuelType())
              .location(null)
              .url(listingUrl(externalId, null))
              .build());

          CompletableFuture<ScrapeResponse> cf = CompletableFuture.supplyAsync(() -> {
            try {
              if (detailDelay > 0) Thread.sleep(detailDelay);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
            JsonNode detail = fetchDetail(externalId, detailDelay);
            return buildResponse(externalId, title, price, detail);
          }, executor);
          futures.add(cf);
        }

        out.addAll(base);

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

  private JsonNode fetchDetail(String externalId, int detailDelay) {
    try {
      return getJson(apiBase + "/api/listings/" + externalId);
    } catch (RuntimeException first) {
      log.warn("Detail fetch failed for {}, retrying: {}", externalId, first.getMessage());
      try {
        if (detailDelay > 0) Thread.sleep(detailDelay);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      try {
        return getJson(apiBase + "/api/listings/" + externalId);
      } catch (RuntimeException retry) {
        log.warn("Detail fetch retry failed for {}: {}", externalId, retry.getMessage());
        return null;
      }
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

    if (detail != null && !detail.isMissingNode()) {
      if (log.isDebugEnabled()) log.debug("Detail for {}: {}", externalId, detail.toPrettyString());

      JsonNode brandNode = detail.path("brand");
      if (brandNode.isObject()) brand = brandNode.path("name").asText(null);
      JsonNode modelNode = detail.path("model");
      if (modelNode.isObject()) model = modelNode.path("name").asText(null);

      JsonNode cities = detail.path("cities");
      if (cities.isArray() && !cities.isEmpty()) location = cities.get(0).path("name").asText(null);
      if (location == null) location = detail.path("location").asText(null);
      if (location == null) location = extractLocationFromDescription(detail);

      for (JsonNode a : detail.path("attributes")) {
        String code = a.path("attr_code").asText(null);
        if (code == null) continue;
        switch (code) {
          case "godiste" -> {
            if (a.path("value").isNumber()) year = a.path("value").asInt();
          }
          case "godina-prve-registracije" -> {
            if (year == null) {
              String v = a.path("value").asText(null);
              year = parseYear(v);
            }
          }
          case "kilometra-a", "kilometraza", "kilometara" -> {
            if (a.path("value").isNumber()) mileageKm = a.path("value").asInt();
            else mileageKm = parseKm(a.path("value").asText(null));
          }
          case "gorivo" -> fuelType = a.path("value").asText(null);
        }
        if (log.isDebugEnabled())
          log.debug("  attr_code='{}' value='{}'", code, a.path("value").toPrettyString());
      }
      if (log.isDebugEnabled())
        log.debug("Parsed attributes for {}: year={}, mileage={}, fuel={}", externalId, year, mileageKm, fuelType);
    }

    if (brand == null) brand = deriveBrand(title);
    if (model == null) model = ModelNormalizer.normalize(title);

    String url = listingUrl(
        externalId,
        detail
    );

    return ScrapeResponse.builder()
        .externalId(externalId)
        .title(title)
        .brand(brand == null ? null : brand.toUpperCase())
        .model(model)
        .price(price)
        .currency("KM")
        .year(year)
        .mileageKm(mileageKm)
        .fuelType(fuelType)
        .location(location)
        .url(url)
        .build();
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

  private record SearchAttrs(Integer year, Integer mileageKm, String fuelType) {
  }

  private SearchAttrs parseSearchAttrs(JsonNode item) {
    Integer year = null;
    Integer mileageKm = null;
    String fuelType = null;

    JsonNode labels = item.path("labels");
    if (labels.isArray()) {
      for (JsonNode label : labels) {
        if (label.isTextual()) {
          String v = label.asText(null);
          if (v == null) continue;
          String digits = v.replaceAll("[^0-9]", "");
          if (digits.length() == 4) {
            try {
              year = Integer.parseInt(digits);
            } catch (NumberFormatException ignored) {
            }
          } else if (!digits.isEmpty()) {
            try {
              mileageKm = Integer.parseInt(digits);
            } catch (NumberFormatException ignored) {
            }
          }
        } else if (label.isObject()) {
          String labelText = label.path("label").asText(null);
          String labelValue = label.path("value").asText(null);
          if (labelText != null && labelText.toLowerCase(Locale.ROOT).contains("godište")) {
            if (year == null) year = parseYear(labelValue);
          } else if (labelText != null && labelText.toLowerCase(Locale.ROOT).contains("kilometraža")) {
            if (mileageKm == null) mileageKm = parseKm(labelValue);
          } else if (labelText != null && labelText.toLowerCase(Locale.ROOT).contains("gorivo")) {
            fuelType = labelValue;
          }
        }
      }
    }

    JsonNode special = item.path("special_labels");
    if (special.isArray()) {
      for (JsonNode spec : special) {
        String label = spec.path("label").asText(null);
        String value = spec.path("value").asText(null);
        if (label == null || value == null) continue;
        String code = label.toLowerCase(Locale.ROOT);
        if (code.contains("godište") || code.contains("godina")) {
          if (year == null) year = parseYear(value);
        } else if (code.contains("kilometraža")) {
          if (mileageKm == null) mileageKm = parseKm(value);
        } else if (code.contains("gorivo")) {
          fuelType = value;
        }
      }
    }

    if (log.isDebugEnabled() && (year != null || mileageKm != null || fuelType != null)) {
      log.debug("Search attrs: year={}, mileage={}, fuel={}", year, mileageKm, fuelType);
    }

    return new SearchAttrs(year, mileageKm, fuelType);
  }

  private String extractLocationFromDescription(JsonNode detail) {
    JsonNode desc = detail.path("additional").path("description");
    if (!desc.isTextual()) return null;
    String html = desc.asText(null);
    if (html == null) return null;
    java.util.regex.Pattern p = java.util.regex.Pattern.compile("<b>(GRAD\\s+[^<]+)</b>", java.util.regex.Pattern.CASE_INSENSITIVE);
    java.util.regex.Matcher m = p.matcher(html);
    if (m.find()) {
      String match = m.group(1).toUpperCase(Locale.ROOT);
      return match.replace("GRAD", "").trim().toUpperCase();
    }
    return null;
  }
}
