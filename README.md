# Auto Tracker — Used Car Listings & Price Alert Engine

**A "10x Solution" capstone** — Spring Boot backend that scrapes used-car listings from
[olx.ba](https://olx.ba), stores their price history, scores each listing as under-/over-/fair-priced
against comparable cars, and emails you the moment a good deal appears.

> **The 10x claim.** Finding a fairly-priced listing goes from *manually refreshing a site 3×/day for a
> week* to *an automatic email the moment a good one appears.*

- **Stack:** Spring Boot 3.5.16 (Java 25) · PostgreSQL 16 · Liquibase · Spring Cache · MapStruct · Jsoup
- **$0, no credit card:** everything runs locally via Docker (`docker compose up`). Mailhog captures
  emails for inspection — no real SMTP account needed.
- **Author:** Dragan Bjelica · FlyRank Internship · Backend Track · Public GitHub repo

---

## 1. What it does

| Capability                                                                              | Where it lives                                            |
|-----------------------------------------------------------------------------------------|-----------------------------------------------------------|
| Scheduled, **polite, idempotent** scrape of olx.ba (every 2h)                           | `ScraperService` / `OlxScraper` / `ScrapeScheduler`       |
| **Price history** — every price change is appended, never overwritten                   | `price_history` table + `ScraperService`                  |
| **Fair-price scoring** — cohort average (same model, ±2 years, ±25k km) cached & reused | `PricingService` (`@Cacheable`)                           |
| **Email digest** of new underpriced listings + notable price drops                      | `DigestScheduler` / `ReportService` / `DigestEmailSender` |
| **On-demand REST API** (`/listings`, `/stats`, `/report`)                               | `ListingController` / `ReportController`                  |

**Non-goals (by design):** no ML price prediction (cohort averages are enough and easier to defend in an
interview); one source site (olx.ba) in the MVP; no auth/watchlists, no mobile app, no payments.
These are explicit scope decisions, not omissions.

---

## 2. Run it from a clean machine (the 5-minute path)

You need **Docker + Docker Compose** and a **JDK 25**. Then:

```bash
# 1. Start Postgres + Mailhog
docker compose up -d

# 2. Build & run. A clone has NO .env or local Maven, so use the Maven wrapper:
./mvnw spring-boot:run        # *nix / macOS / Git Bash
#   (Windows PowerShell:  .\mvnw.cmd spring-boot:run )

# 3. Load demo data (seed rows for VW Golf 7 + Škoda Octavia with a planted good deal)
./scripts/seed.sh

# 4. Browse the results
curl http://localhost:8080/listings
curl "http://localhost:8080/stats?model=Golf%207&year=2017"
curl http://localhost:8080/report
open http://localhost:8025        # Mailhog UI — see captured digest emails
```

That's it. The app boots, Liquibase creates the schema, `.env` is auto-loaded (see §4), and the seed
gives you instant, reproducible results **without waiting for a real scrape cycle**.

> No `./mvnw` on your machine? Any Maven 3.9+ works: `mvn spring-boot:run`.

---

## 3. First-time setup (one time)

### 3.1 Prerequisites
- **Docker Desktop** (Docker Engine + Compose v2) — https://docs.docker.com/get-docker/
- **JDK 25** — https://jdk.java.net/25/ (or your vendor's build)
- **Maven** — *not required*: this repo ships the [Maven Wrapper](https://maven.apache.org/wrapper/)
  (`./mvnw`), which downloads the correct Maven version automatically.

### 3.2 Environment file
Copy the template and adjust if you like the defaults (they match `docker-compose.yml`):

```bash
cp .env.example .env
# .env is git-ignored; nothing secret is committed.
```

Key values (all have safe defaults, so you can leave them as-is):

| Variable                                                      | Default                                                                 | Purpose                                       |
|---------------------------------------------------------------|-------------------------------------------------------------------------|-----------------------------------------------|
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` | `localhost` / `5432` / `auto_tracker` / `auto_tracker` / `auto_tracker` | Postgres connection                           |
| `MAIL_HOST` / `MAIL_PORT`                                     | `localhost` / `1025`                                                    | Mailhog SMTP (captured, not delivered)        |
| `MAIL_TO`                                                     | `auto-tracker@localhost`                                                | Digest recipient (Mailhog captures it)        |
| `SCRAPE_CATEGORY_ID`                                          | `18`                                                                    | olx.ba "Automobili" category for the JSON API |
| `SCRAPE_DELAY_MS`                                             | `2000`                                                                  | Politeness delay between requests             |
| `SCRAPE_MAX_PAGES`                                            | `5`                                                                     | Pages walked per scrape run                   |
| `SCRAPE_CRON`                                                 | `0 0 0/2 * * ?`                                                         | Scrape schedule (every 2h)                    |
| `DIGEST_CRON`                                                 | `0 7 * * * ?`                                                           | Daily digest at 07:00                         |
| `SCORE_YEAR_TOLERANCE`                                        | `2`                                                                     | Cohort = model, year ± this                   |
| `SCORE_UNDERPRICED_PCT`                                       | `10`                                                                    | Flag listings ≥ this % below cohort avg       |

> **Why `.env` "just works":** Spring Boot does **not** read `.env` on its own. This project registers a
> `DotEnvEnvironmentPostProcessor` (`src/main/java/com/cartracker/config/`) at `HIGHEST_PRECEDENCE`, so
> `java -jar` picks up `.env` with zero `export`/`-D` ceremony. If you prefer, you can also pass any of
> the above as OS env vars or `-Dkey=value` system properties — they override `.env`.

### 3.3 Start the infrastructure
```bash
docker compose up -d          # Postgres on :5432, Mailhog SMTP :1025 / UI :8025
docker compose ps             # confirm both are "Up"
```

---

## 4. Build & run (every time)

```bash
# Run (hot): recompiles on change, picks up .env automatically
./mvnw spring-boot:run

# Or build a production jar and run it
./mvnw package -DskipTests
java -jar target/auto-tracker-0.1.0.jar
```

The app logs `Started AutoTrackerApplication` when ready. It connects to the Docker Postgres, Liquibase
applies `db/changelog/db.changelog-master.yaml` (creates `listing`, `price_history`, `digest_state`),
and the schedulers arm.

> **Windows note:** use `.\mvnw.cmd` instead of `./mvnw`. On Git Bash/MSYS the wrapper downloads Maven
> into `~/.m2/wrapper/dists` on first run.

---

## 5. Seed / demo data

The repo ships `src/main/resources/db/seed.sql` with **1–2 car models** (VW Golf 7, Škoda Octavia):
fair-priced comparables, a deliberately **underpriced "good deal"** (Golf 7 at 19.900 KM vs ~23.6k avg),
and **planted price drops** (Octavia 30.500 → 27.900). It is **idempotent** (re-runs delete
`seed-%` rows first), so running it repeatedly is safe.

```bash
# Helper that copies the SQL into the running Postgres container and applies it:
./scripts/seed.sh

# Or by hand (psql on host):
psql "postgresql://auto_tracker:auto_tracker@localhost:5432/auto_tracker" -f src/main/resources/db/seed.sql
```

After seeding, hit the endpoints in §6 to see scoring + the digest immediately.

### 5.1 Reset the digest watermark (so the demo always shows the good deal)

The digest uses a **watermark** in `digest_state` so each run only reports listings *new since the last
digest*. After you've sent a digest, `/report` and the email will correctly show fewer (or zero) good
deals until new underpriced listings appear. To re-show the seeded good deal during a demo:

```bash
docker exec -e PGPASSWORD=auto_tracker auto-tracker-db \
  psql -U auto_tracker -d auto_tracker \
  -c "DELETE FROM digest_state WHERE state_key = 'digest.lastSentAt';"
# now /report and the next digest will include the seeded Golf 7 good deal again
```

Then re-run the demo:

```bash
./scripts/demo.sh
```

Base URL: `http://localhost:8080`

| Method | Path             | Query params                                                                      | Success                                                | Error                                   |
|--------|------------------|-----------------------------------------------------------------------------------|--------------------------------------------------------|-----------------------------------------|
| `GET`  | `/listings`      | `model`, `minYear`, `maxYear`, `fuelType`, `minPrice`, `maxPrice`, `page`, `size` | `200` (Spring `Page<ListingResponse>`)                 | `400` on invalid params                 |
| `GET`  | `/listings/{id}` | —                                                                                 | `200` (detail + `fairPrice` verdict + `priceHistory`)  | `404` if missing                        |
| `GET`  | `/stats`         | `model` (required), `year`, `mileageKm`                                           | `200` (`CohortStatsResponse`)                          | `400` if model missing / no cohort data |
| `GET`  | `/report`        | —                                                                                 | `200` (digest JSON — same content as the email)        | —                                       |
| `POST` | `/scrape`        | —                                                                                 | `200` (`{fetched, inserted, updated, priceChanges}`)   | — (runs a live scrape)                  |
| `POST` | `/digest/send`   | —                                                                                 | `200` (builds + emails the digest, advances watermark) | —                                       |

**Example calls**

```bash
B=http://localhost:8080

# Filterable listing list (Golf 7, page 0, size 5)
curl "$B/listings?model=Golf%207&size=5"

# Detail of a listing id (shows underpriced/fair/overpriced verdict + price history)
curl "$B/listings/1"

# Cohort statistics for Golf 7, 2017 — cached after the first call
curl "$B/stats?model=Golf%207&year=2017"

# On-demand digest (identical payload to the scheduled email)
curl "$B/report"

# Manual triggers (handy for demos without waiting for the cron)
curl -X POST "$B/scrape"        # pull latest live listings
curl -X POST "$B/digest/send"   # send a digest now
```

**`/stats` response shape**
```json
{
  "model": "Golf 7", "year": 2017, "mileageBracketKm": 0,
  "count": 7, "averagePrice": 23627.14, "medianPrice": 23900.00,
  "minPrice": 19900.00, "maxPrice": 26990.00
}
```

**`/listings/{id}` verdict shape** (nested under `fairPrice`)
```json
{
  "priceLabel": "underpriced", "cohortAverage": 23650.00,
  "deltaPercent": 15.86, "goodDeal": true, "cohortCount": 7,
  "priceHistory": [ { "price": 23500.00, "recordedAt": "..." },
                    { "price": 19900.00, "recordedAt": "..." } ]
}
```
`priceLabel` ∈ `underpriced` | `overpriced` | `fair_price` | `insufficient_data`.
A listing is a **good deal** when it sits ≥ `SCORE_UNDERPRICED_PCT`% below its cohort average.

---

## 7. How the concepts map to the capstone (5+ rule)

| # | Concept                            | Where it lives in this repo                                                                                     |
|---|------------------------------------|-----------------------------------------------------------------------------------------------------------------|
| 1 | **Web scraping pipeline** *(swap)* | `ScraperService` + `OlxScraper` — polite JSON-API fetch of olx.ba, idempotent upsert by `external_id`           |
| 2 | **Database**                       | PostgreSQL — `listing`, `price_history`, `digest_state` (Liquibase-managed), survives restart                   |
| 3 | **Cron / background job**          | `@Scheduled` `ScrapeScheduler` (every 2h) + `DigestScheduler` (daily 07:00), off the request path               |
| 4 | **Reporting — email**              | `DigestEmailSender` (Mailhog SMTP) + `/report` endpoint, identical content                                      |
| 5 | **Caching**                        | `PricingService.cohortStats()` `@Cacheable` — cohort averages computed once, reused                             |
| 6 | **API endpoints**                  | `ListingController` / `ReportController` — correct status codes + validation (`spring-boot-starter-validation`) |
| 7 | *(stretch)* **Authentication**     | Watchlists tied to a logged-in user (out of MVP scope; core stands alone)                                       |

5 core concepts, exactly **one swap** (web scraping), satisfying "max 2 swaps, ≥3 from the original list."

---

## 8. Architecture

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
(2000 ms default) between requests; descriptive `User-Agent`; bounded page count per run
(`SCRAPE_MAX_PAGES`). The source is the public olx.ba vehicles category; scraping is used only as a
personal, low-volume price-tracking tool (see `DESIGN.md` §3 for the robots.txt / ToS confirmation).

---

## 9. Troubleshooting

| Symptom                                                         | Cause                                                                                                      | Fix                                                                                                                    |
|-----------------------------------------------------------------|------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------|
| `FATAL: password authentication failed for user "auto_tracker"` | App connected to the **wrong** Postgres (native `:5432`, not Docker `:15432`) because `.env` wasn't loaded | Ensure `.env` exists at project root; it is auto-loaded. Or pass `-DDB_PORT=5432`.                                     |
| `Port 8080 was already in use`                                  | Orphaned boot from a previous run                                                                          | `docker compose` is fine; kill the Java process: `lsof -ti:8080 \| xargs kill -9` (or Task Manager).                   |
| `No cohort data` from `/stats`                                  | Model/year has too few comparables, or `mileageKm` filtered the cohort out                                 | Use a model that has ≥2 seeded comparables (e.g. `Golf 7` 2017, `Octavia` 2019). Omit `mileageKm` to widen the cohort. |
| Emails not appearing in Mailhog                                 | App not pointed at Mailhog, or Mailhog down                                                                | Confirm `MAIL_PORT=1025` and `docker compose ps` shows `auto-tracker-mail` Up; open `http://localhost:8025`.           |
| Maven wrapper (`mvnw`) can't download Maven                     | No internet at first run, or corporate proxy                                                               | Install Maven 3.9+ and use `mvn spring-boot:run`, or pre-seed `~/.m2`.                                                 |

---

## 10. Project layout

```
car-tracker-capstone/
├── docker-compose.yml              # Postgres 16 + Mailhog
├── .env.example                    # copy → .env (git-ignored)
├── mvnw / mvnw.cmd / .mvn/        # Maven Wrapper (committed)
├── pom.xml                         # Spring Boot 3.5.16, Java 25, MapStruct, Jsoup, Liquibase
├── scripts/
│   ├── seed.sh                     # apply src/main/resources/db/seed.sql into the container
│   └── demo.sh                     # 5-minute end-to-end demo of the whole system
├── src/main/java/com/cartracker/
│   ├── AutoTrackerApplication.java # @SpringBootApplication + @EnableScheduling + @EnableCaching
│   ├── config/                     # DotEnvEnvironmentPostProcessor (auto-loads .env)
│   ├── scraper/                    # OlxScraper, ScraperService, ScrapeScheduler, ModelNormalizer
│   ├── entity/                     # ListingEntity, PriceHistoryEntity, DigestStateEntity
│   ├── repository/                 # JPA repositories + cohort queries
│   ├── scoring/                    # PricingService (cohort stats + @Cacheable), FairPriceVerdict
│   ├── report/                     # ReportService, DigestScheduler, DigestEmailSender
│   ├── api/                        # ListingController, ReportController, DTOs, mapper, spec
│   └── common/error/               # RestExceptionHandler → uniform {code, message} + 4xx/5xx
└── src/main/resources/
    ├── application.yml             # datasource, liquibase, cache, mail, scoring config
    └── db/
        ├── seed.sql                # idempotent demo data (Golf 7 + Octavia)
        └── changelog/              # Liquibase master + 0001 listing/price_history + 0002 digest_state
```

---

## 11. Source & design docs

- **Design one-pager:** [`DESIGN.md`](./DESIGN.md) — problem, 10x claim, non-goals, source confirmation
  (robots.txt / ToS), data model, API surface, concept mapping.
- **Source check:** `references/olx-ba-source-check.md` — robots.txt + ToS findings and the exact category URL.
- **Public repo:** *(link added in Phase 5 submission)*
