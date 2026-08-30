# My 10x Solution — Auto Tracker

**Used Car Listings & Price Alert Engine**

- **Author:** Dragan Bjelica
- **Track:** FlyRank Internship · Backend Track · Capstone
- **Repository (public):** https://github.com/draganb24/car-tracker
- **Stack:** Spring Boot 3.5.16 (Java 25) · PostgreSQL 16 · Liquibase · Spring Cache · MapStruct · Jsoup · Mailhog
- **Cost:** $0 · no credit card · runs locally via Docker

---

## 1. The problem

People shopping for a used car refresh listing sites two or three times a day, hoping to catch a fair
deal before someone else does — and have **no real way to know if a price is actually fair** for that
specific model / year / mileage combination. Either they miss the good listing, or they overpay because
they can't benchmark it against comparable cars.

## 2. The 10x claim

> Finding a fairly-priced listing goes from *manually refreshing a site 3× a day for a week* to *an
> automatic email the moment a good one appears.*

The system scrapes new listings on a schedule, stores their **price history**, **scores** each listing
against a comparable cohort (same model, ±2 years, similar mileage), and **notifies** the user by email
the instant something worth their attention shows up. That is the full loop — and it runs unattended.

**Who has this problem:** anyone actively shopping a specific car model, or someone monitoring market
prices for a car they plan to sell.

---

## 3. Concept mapping (the 5+ rule)

The capstone grading rule allows **max 2 concept swaps**, at least 3 from the original list. Auto
Tracker uses exactly **one swap** (web scraping) against the backend core:

| # | Concept                            | Where it lives in this repo                                                                                     |
|---|------------------------------------|-----------------------------------------------------------------------------------------------------------------|
| 1 | **Web scraping pipeline** *(swap)* | `ScraperService` + `OlxScraper` — polite JSON-API fetch of olx.ba, idempotent upsert by `external_id`           |
| 2 | **Database**                       | PostgreSQL — `listing`, `price_history`, `digest_state` (Liquibase-managed), survives restart                   |
| 3 | **Cron / background job**          | `@Scheduled` `ScrapeScheduler` (every 2h) + `DigestScheduler` (daily 07:00), off the request path               |
| 4 | **Reporting — email**              | `DigestEmailSender` (Mailhog SMTP) + `/report` endpoint, identical content                                      |
| 5 | **Caching**                        | `PricingService.cohortStats()` `@Cacheable` — cohort averages computed once, reused                             |
| 6 | **API endpoints**                  | `ListingController` / `ReportController` — correct status codes + validation (`spring-boot-starter-validation`) |
| 7 | *(stretch)* **Authentication**     | Watchlists tied to a logged-in user (out of MVP scope; core stands alone)                                       |

**5 core concepts, exactly 1 swap** → satisfies "max 2 swaps, ≥3 from the original list."

---

## 4. Architecture

```
Cron trigger (@Scheduled, every 2h / daily 07:00)
   → ScraperService.runScrape()
       • OlxScraper.fetchCars(knownIds) — paginate olx.ba JSON API,
         enrich NEW external_ids via the detail API (polite, delayed)
       • dedupe by external_id → idempotent upsert (never duplicate)
       • on price change → append PriceHistory row (never overwrite)
       → save Listing (+ PriceHistory) into Postgres

PricingService.score(listing)
   → CohortSignature(model, year±tolerance, mileageBracket)
   → cached CohortStats (avg/median/min/max/count) — recompute only on cache miss
   → % under/over vs cohort avg → flag "good deal" when ≥ threshold (default 10% under)

Scheduled digest job (daily 07:00)
   → ReportService.build() picks flagged "good deals" + notable price drops
       (watermark in digest_state ensures only NEW deals since last run)
   → DigestEmailSender → Mailhog (dev SMTP) → view at :8025

REST API (Spring Web):
   GET /listings   GET /listings/{id}   GET /stats   GET /report
   POST /scrape    POST /digest/send
```

**Politeness contract** (`OlxScraper` / `ScraperService`): single-threaded; ≥ `SCRAPE_DELAY_MS`
(2000 ms default) between requests; descriptive `User-Agent`; bounded page count per run. The source is
the public olx.ba vehicles category, used only as a personal, low-volume price-tracking tool.

---

## 5. What was built (phase recap)

- **Phase 1 — Design:** `DESIGN.md` one-pager (problem, 10x claim, non-goal, data model, API surface),
  source confirmation (olx.ba `robots.txt` / ToS), and the exact category/JSON-API targets.
- **Phase 2 — Walking skeleton:** scheduled scrape → Liquibase-created schema → `GET /listings` JSON.
- **Phase 3 — Concepts one at a time:** append-only price history → cohort caching + fair-price scoring
  → email digest → `/report` endpoint. Each finished and committed before the next.
- **Phase 4 — Runnable by a stranger:** `README.md` (exact run steps, seed, 5-minute demo, API ref,
  concept table, troubleshooting), `scripts/seed.sh` + `scripts/demo.sh`, and a **Maven Wrapper**
  (`./mvnw`) so a clean clone builds without local tooling.
- **Phase 5 — Package & submit:** this overview doc, the README concept table, and the public repo.

---

## 6. Key decisions & pitfalls solved

- **`.env` "just works".** Spring Boot does not read `.env` on its own, so a bare `java -jar` fell back
  to defaults and hit the *wrong* Postgres (native `:5432` vs Docker `:15432`). Fixed once, in the app,
  via a `DotEnvEnvironmentPostProcessor` (`HIGHEST_PRECEDENCE`) — now `java -jar` and `spring-boot:run`
  pick up `.env` with zero ceremony.
- **Model must be a normalized short name, not the title.** Storing the whole title as `model` made every
  listing its own cohort → universal `insufficient_data`. Solved with `scraper/ModelNormalizer.java`
  (curated keyword table + boundary-aware regex) so cohorts actually form.
- **HTTP timeouts.** Switched from `RestClient` (unreliable timeout wiring here) to the built-in
  `java.net.http.HttpClient` with explicit connect/read timeouts, so a single slow olx request can't
  silently stall the whole scrape.
- **Central error handling.** A `@ControllerAdvice` `RestExceptionHandler` returns a uniform
  `{code, message}` body — `EntityNotFoundException` → 404, `InvalidParamException` → 400, catch-all →
  500 (with logged stack). Invalid input yields clean 4xx, never 500.
- **Specification pattern** for listing filters (dedicated `ListingFilterSpecification` over string-path
  `root.get(...)`), keeping the controller thin and the repository call uniform.
- **Idempotent scraping.** Upsert keyed by `external_id`; re-running never duplicates. Verified:
  `count(*) - count(distinct external_id) = 0`.
- **Caching.** Cohort averages are `@Cacheable` — computed on cache miss, never per-request.

---

## 7. How to run (5-minute path)

Full detail in `README.md`. Short version:

```bash
docker compose up -d          # Postgres + Mailhog
./mvnw spring-boot:run        # (Windows: .\mvnw.cmd) — builds via the Maven Wrapper
./scripts/seed.sh             # idempotent demo data (VW Golf 7 + Škoda Octavia)
curl http://localhost:8080/listings
curl "http://localhost:8080/stats?model=Golf%207&year=2017"
curl http://localhost:8080/report
# open http://localhost:8025 to see captured digest emails
```

A stranger gets results **without waiting for a real scrape cycle**, thanks to the seed data and the
`scripts/demo.sh` walkthrough.

---

## 8. Non-goals (explicit scope)

- **No ML price prediction** — cohort averages are enough and easier to defend in an interview.
- **One source site (olx.ba) in the MVP** — no general marketplace aggregation.
- **No multi-source, no mobile app, no payments, no real user base.**
- **Auth / watchlists are optional stretch** — the core (scrape → store → score → notify) stands alone.

---

## 9. Verification evidence

Verified end-to-end against the running app + Docker stack (JDK 25, Postgres 16, Mailhog):

- Boot: `Started AutoTrackerApplication`; Liquibase applied; connected to Postgres; schedulers armed.
- `GET /listings` → 200 with seeded rows; `GET /stats?model=Golf 7&year=2017` → cohort avg ~23,627 KM.
- `GET /listings/{id}` for the seeded good deal → `priceLabel=underpriced`, `delta=15.86%`,
  `goodDeal=true`, 2 price-history rows (price history works).
- `GET /stats` with no `model` → clean **400** (not 500); `/listings/{missing}` → 404.
- `POST /digest/send` → digest built + emailed; Mailhog captured the message.
- Idempotency: 241 rows = 241 distinct `external_id`, 0 nulls (no duplicates on re-run / re-seed).
- `POST /scrape` twice → second run `inserted:0` (idempotent).

---

## 10. Submission

- **Overview:** this document.
- **README concept table:** `README.md` §7 (the 5+ mapping above, mirrored).
- **Public repository:** https://github.com/draganb24/car-tracker

*Auto Tracker — scrape → store → score → notify. The 10x loop, running unattended.*
