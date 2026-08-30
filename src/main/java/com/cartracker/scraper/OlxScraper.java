package com.cartracker.scraper;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Polite, read-only HTML scraper for the olx.ba vehicles category (cars).
 * <p>
 * Target: {@code https://olx.ba/vozila} (cars are the default first tab; filtering is
 * client-side, so we request {@code /vozila} and keep only automobile cards).
 * <p>
 * Selectors reverse-engineered from a live /vozila page (see DESIGN.md §3):
 *   - card anchor : a[href^=/artikal/]
 *   - title       : h1.main-heading
 *   - facts       : span.standard-tag > div.capitalize  -> [fuel, mileage_km, year]
 *   - price       : .price-wrap .font-bold span.smaller  -> "34.000 KM"
 * <p>
 * Politeness: single-threaded, configurable delay between requests, descriptive User-Agent.
 * The page is fetched once per run (all cards are server-rendered); no per-card extra hits.
 */
@Component
public class OlxScraper {

    private static final Logger log = LoggerFactory.getLogger(OlxScraper.class);

    private final String baseUrl;
    private final int delayMillis;
    private final String userAgent;

    public OlxScraper(
            @Value("${app.scraping.base-url}") String baseUrl,
            @Value("${app.scraping.delay-ms:2000}") int delayMillis) {
        this.baseUrl = baseUrl;
        this.delayMillis = Math.max(delayMillis, 1000);
        this.userAgent = "AutoTracker/0.1 (FlyRank capstone; +https://github.com/draganb24/car-tracker)";
    }

    public List<ScrapeResult> fetchCars() throws IOException, InterruptedException {
        // Politeness: pause before hitting the source so repeated/overlapping runs
        // never hammer the site. The page is fetched once per run (cards are server-rendered).
        if (delayMillis > 0) {
            Thread.sleep(delayMillis);
        }
        log.info("Scraping {}", baseUrl);
        Document doc = Jsoup.connect(baseUrl)
                .userAgent(userAgent)
                .timeout(30_000)
                .header("Accept-Language", "bs-BA,hr-HR,sr-RS")
                .get();

        Elements cards = doc.select("a[href^=/artikal/]");
        log.info("Found {} candidate cards", cards.size());

        List<ScrapeResult> out = new ArrayList<>();
        for (Element card : cards) {
            try {
                ScrapeResult r = parseCard(card);
                if (r == null) {
                    continue; // not an automobile / unparseable
                }
                out.add(r);
            } catch (RuntimeException ex) {
                log.warn("Skipping card (parse error): {}", ex.getMessage());
            }
        }
        log.info("Parsed {} automobile listings", out.size());
        return out;
    }

    private ScrapeResult parseCard(Element card) {
        String href = card.attr("href");
        String externalId = href.replace("/artikal/", "").trim();
        if (externalId.isBlank() || !externalId.chars().allMatch(Character::isDigit)) {
            return null;
        }

        Element heading = card.selectFirst("h1.main-heading");
        if (heading == null) {
            return null;
        }
        String title = heading.text().trim();

        Elements caps = card.select("span.standard-tag div.capitalize");
        // Expected order from markup: [fuel, mileage_km, year]
        if (caps.size() < 3) {
            return null; // not a full automobile card (e.g. a motorcycle/truck differs)
        }
        String fuelType = caps.get(0).text().trim();
        Integer mileageKm = parseKm(caps.get(1).text());
        Integer year = parseYear(caps.get(2).text());
        if (year == null) {
            return null;
        }

        Element priceEl = card.selectFirst(".price-wrap .font-bold span.smaller");
        BigDecimal price = priceEl == null ? null : parsePrice(priceEl.text());
        if (price == null) {
            return null;
        }

        String brand = deriveBrand(title);
        String model = deriveModel(title);
        String url = normalizeUrl(href);

        return new ScrapeResult(externalId, title, brand, model, price, "KM",
                year, mileageKm, fuelType, null, url);
    }

    private String deriveBrand(String title) {
        // crude but deterministic: first token is typically the make
        String[] parts = title.split("\\s+");
        return parts.length > 0 ? parts[0].toUpperCase() : null;
    }

    private String deriveModel(String title) {
        // model = title minus brand; good enough as a cohort key
        String[] parts = title.split("\\s+", 2);
        return parts.length > 1 ? parts[1].trim() : title;
    }

    private Integer parseKm(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? null : Integer.parseInt(digits);
    }

    private Integer parseYear(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.length() != 4) return null;
        int y = Integer.parseInt(digits);
        return (y >= 1900 && y <= java.time.Year.now().getValue() + 1) ? y : null;
    }

    private BigDecimal parsePrice(String raw) {
        if (raw == null) return null;
        String digits = raw.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? null : new BigDecimal(digits);
    }

    private String normalizeUrl(String href) {
        try {
            URI u = new URI(href);
            if (u.isAbsolute()) return href;
            return "https://olx.ba" + (href.startsWith("/") ? "" : "/") + href;
        } catch (URISyntaxException e) {
            return "https://olx.ba" + href;
        }
    }

    public String getBaseUrl() { return baseUrl; }
}
