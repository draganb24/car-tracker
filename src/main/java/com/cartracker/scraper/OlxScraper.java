package com.cartracker.scraper;

import com.cartracker.scraper.dto.response.ScrapeResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
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

/**
 * Polite scraper for the olx.ba vehicles category (cars).
 * <p>
 * olx.ba is a client-rendered site whose category grid is capped at 60 server-
 * rendered cards and whose {@code ?page=N} is ignored by the SPA. The real,
 * paginated data source is the public JSON API the SPA itself uses:
 * <ul>
 *   <li>{@code GET /api/search?category_id=18&page=N&per_page=60} — discovery +
 *       pagination; each item has id, title, price (KM), status.</li>
 *   <li>{@code GET /api/listings/{id}} — full structured detail (brand, model,
 *       year, mileage, fuel, location) via its {@code attributes} array.</li>
 * </ul>
 * We paginate the search endpoint and enrich only listings we don't already
 * have (the caller passes the known external IDs), so re-scrapes stay cheap and
 * polite. {@link ModelNormalizer} turns the free-text title into a canonical
 * model for cohort scoring.
 * <p>
 * Politeness: single-threaded, configurable delay between requests, descriptive
 * User-Agent, and we only hit detail pages for genuinely new listings.
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
                    @Value("${app.scraping.delay-ms:2000}") int delayMillis,
                    @Value("${app.scraping.max-pages:5}") int maxPages) {
    // derive API host from the configured base url (default https://olx.ba/vozila)
    String host = baseUrl;
    int q = host.indexOf('?');
    if (q >= 0) host = host.substring(0, q);
    int slash = host.indexOf('/', 8);
    this.apiBase = (slash >= 0 ? host.substring(0, slash) : host).replaceAll("/+$", "");
    this.categoryId = categoryId;
    this.delayMillis = Math.max(delayMillis, 500);
    this.maxPages = Math.max(maxPages, 1);
    this.userAgent = "AutoTracker/0.1 (FlyRank capstone; +https://github.com/draganb24/car-tracker)";
    // Bounded timeouts so a single slow/hanging listing can't stall the whole run.
    this.client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    this.mapper = new ObjectMapper();
  }

  /**
   * Fetch and parse car listings across up to {@code maxPages} search pages.
   * Only listings whose external ID is NOT in {@code knownExternalIds} are
   * enriched with detail data (year/mileage/fuel/location).
   */
  public List<ScrapeResponse> fetchCars(Set<String> knownExternalIds) throws IOException, InterruptedException {
    Set<String> known = knownExternalIds == null ? new HashSet<>() : knownExternalIds;
    List<ScrapeResponse> out = new ArrayList<>();
    int pageNo = 1;

    if (delayMillis > 0) Thread.sleep(delayMillis);

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

      int added = 0;
      for (JsonNode item : data) {
        String externalId = item.path("id").asText(null);
        if (externalId == null || externalId.isBlank()) continue;
        if (known.contains(externalId)) continue; // already have it; skip detail call

        String title = item.path("title").asText(null);
        BigDecimal price = toPrice(item.path("price"));
        if (title == null || price == null) continue;

        // Enrich with detail (year/mileage/fuel/location/brand/model)
        JsonNode detail = fetchDetail(externalId);
        ScrapeResponse r = buildResponse(externalId, title, price, detail);
        if (r != null) {
          out.add(r);
          added++;
        }
        if (delayMillis > 0) Thread.sleep(delayMillis);
      }

      log.info("Page {} enriched {} new listings (total {})", pageNo, added, out.size());
      if (added == 0) {
        log.info("No new listings on page {}; stopping pagination.", pageNo);
        break;
      }
      pageNo++;
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

  private ScrapeResponse buildResponse(String externalId, String title, BigDecimal price, JsonNode detail) {
    String brand = null;
    String model = null;
    Integer year = null;
    Integer mileageKm = null;
    String fuelType = null;
    String location = null;
    String url = apiBase + "/artikal/" + externalId;

    if (detail != null) {
      JsonNode brandNode = detail.path("brand");
      if (brandNode.isObject()) brand = brandNode.path("name").asText(null);
      JsonNode modelNode = detail.path("model");
      if (modelNode.isObject()) model = modelNode.path("name").asText(null);
      JsonNode cities = detail.path("cities");
      if (cities.isArray() && !cities.isEmpty()) location = cities.get(0).path("name").asText(null);
      if (location == null) location = detail.path("location").asText(null);
      if (detail.has("slug")) url = apiBase + "/artikal/" + detail.path("slug").asText(externalId);

      for (JsonNode a : detail.path("attributes")) {
        String code = a.path("attr_code").asText(null);
        if (code == null) continue;
        switch (code) {
          case "godiste" -> { if (a.path("value").isNumber()) year = a.path("value").asInt(); }
          case "godina-prve-registracije" -> {
            if (year == null) { String v = a.path("value").asText(null); year = parseYear(v); }
          }
          case "kilometra-a", "kilometraza", "kilometara" -> {
            if (a.path("value").isNumber()) mileageKm = a.path("value").asInt();
            else mileageKm = parseKm(a.path("value").asText(null));
          }
          case "gorivo" -> fuelType = a.path("value").asText(null);
        }
      }
    }

    // Fallbacks from the title when detail is missing
    if (brand == null) brand = deriveBrand(title);
    if (model == null) model = ModelNormalizer.normalize(title);
    else model = ModelNormalizer.normalize(title);

    return new ScrapeResponse(
        externalId, title,
        brand == null ? null : brand.toUpperCase(),
        model, price, "KM", year, mileageKm, fuelType, location, url
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
      throw new RuntimeException("request to " + url + " failed: " + ex.getMessage());
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("request to " + url + " interrupted");
    } catch (Exception ex) {
      throw new RuntimeException("failed to parse JSON from " + url + ": " + ex.getMessage());
    }
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
