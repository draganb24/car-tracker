# Auto Tracker — Used Car Listings & Price Alert Engine
### FlyRank Internship · Backend Track · Capstone — Phase 1 (Design One-Pager)
Author: Dragan Bjelica · Stack: Spring Boot (Java) + PostgreSQL · Source: olx.ba · $0 · public GitHub repo

---

## 1. Problem & the 10x claim
People shopping for a used car refresh listing sites 2–3×/day, hoping to catch a fair deal
before someone else does, and have no real way to know if a price is fair for that
model/year/mileage combination.

**10x:** Finding a fairly-priced listing goes from manually refreshing a site 3×/day for a week
to an automatic email the moment a good one appears.

**Who:** anyone actively shopping a specific model, or someone monitoring market prices for a
car they plan to sell.

---

## 2. Non-goal (must be stated)
- **No ML price prediction.** Cohort averages (same model, ±2 years, similar mileage) are enough
  and are easier to defend in an interview.
- **One source site (olx.ba) in the MVP.** No general marketplace aggregation.
- **No multi-source, no mobile app, no payments, no real user base.**
- Auth/watchlists are OPTIONAL stretch; the core (scrape → store → score → notify) stands alone.

---

## 3. Source confirmation (robots.txt / ToS / exact URL)
**robots.txt** (`https://olx.ba/robots.txt`) — NOT a standard Disallow/Crawl-delay file. It uses
the EU Directive 2019/790 "content signals" scheme with three signals: `search`, `ai-input`,
`ai-train`. **There are no `Disallow:` rules for any category**, so the vehicles category is not
robots-prohibited. We will:
  - NOT use scraped content for AI training/input (signals `ai-train`/`ai-input` are out of scope).
  - Treat the scrape as a personal, low-volume price-tracking use, not a search-index build.
  - Self-impose politeness (see §7) regardless of the missing Crawl-delay directive.

**ToS:** A full ToS review is a Phase 2 pre-flight check. The vehicles category is public
listings. If any restriction is found, we scope the scraper to the allowed parts or fall back to
cached page snapshots fetched manually for the demo.

**Exact target URL(s):**
  - Primary: `https://olx.ba/vozila` — the "Vozila" (vehicles) category. Cars are the **default
    first tab** and listings are **server-rendered** (observed: real VW Golf / Nissan X-Trail /
    Škoda Octavia cards in the HTML).
  - Listing detail links: `/artikal/{id}` (e.g. `https://olx.ba/artikal/79186560`).
  - Price format in markup: `12.999 KM` (thousands separator `.`, currency `KM`).
  - Car-only filtering is applied **client-side** via a Vue `childCategories` config
    (`displayName:"Automobili"`, `search_component:"BrandsFilterCars"`); there is no static
    `/vozila/automobili` URL (returns 404). **Phase 2 pins the exact cars-only query** (most
    likely a `?tip=automobili` / category-query param or a POST search endpoint) and, as a robust
    fallback, scrapes `/vozila` and filters to automobiles by the category attribute / title
    heuristics (exclude motorcycles, trucks, etc.).

---

## 4. Data model
### 4.1 Listing (table `listing`) — idempotent by `external_id`
| field          | type            | notes |
|----------------|-----------------|-------|
| id             | bigint PK       | generated |
| external_id    | varchar(64) UNIQUE | olx.ba `/artikal/{id}` id — **idempotency key** |
| source         | varchar(32)     | e.g. `olx.ba` (prep for a 2nd source later) |
| title          | varchar(512)    | raw title |
| brand          | varchar(128)    | normalized make (VW, Škoda…) |
| model          | varchar(128)    | normalized model (Golf 7, Octavia…) — cohort key |
| price          | numeric(12,2)   | **current** price only |
| currency       | varchar(8)      | `KM` |
| year           | int             | cohort key |
| mileage_km     | int             | cohort key |
| fuel_type      | varchar(32)     | benzin / dizel / hibrid / electric |
| location       | varchar(128)    | city |
| url            | varchar(1024)   | detail page |
| first_seen_at  | timestamptz     | when first scraped |
| last_seen_at   | timestamptz     | last scrape it appeared in |
| active         | boolean         | still present in latest scrape (re-list detection) |
| created_at / updated_at | timestamptz | audit |

### 4.2 PriceHistory (table `price_history`) — append-only, never overwrites
| field        | type          | notes |
|--------------|---------------|-------|
| id           | bigint PK     | generated |
| listing_id   | bigint FK     | → listing.id (CASCADE) |
| price        | numeric(12,2) | the price at `recorded_at` |
| currency     | varchar(8)    | |
| recorded_at  | timestamptz  | default now() |
Rule: on every scrape, if a known listing's `price` differs from the stored `price`, INSERT a new
`price_history` row. Current price lives on `listing.price`; history is never mutated.

### 4.3 Cohort (computed + cached, NOT a stored entity in MVP)
A **cohort** = the set of active listings sharing `(model, year±yearTolerance, mileageBracket)`.
Its statistics (count, average, median, min, max) are computed on demand and **cached** via
Spring Cache (in-memory `ConcurrentMapCacheManager`) keyed by a `CohortSignature`. Recompute is
triggered on cache miss / invalidation after a scrape, never per-request.

### 4.4 Watchlist (stretch, only with auth)
`watchlist(id, owner_user, model, year_min, year_max, max_price, mileage_max, created_at)`.
Matched against new listings for the digest. Out of MVP scope.

---

## 5. API surface (REST)
| method | path            | purpose | success | error |
|--------|-----------------|---------|---------|-------|
| GET    | `/listings`     | filterable list (model, min/maxYear, min/maxPrice, fuelType, page) | 200 | 400 on invalid params |
| GET    | `/listings/{id}`| detail + price history | 200 | 404 if missing |
| GET    | `/stats?model=` | cohort avg, count, trend | 200 | 400 if model missing/invalid |
| POST   | `/watchlists`   | create saved filter (auth) | 201 | 401/400 |
| GET    | `/report`       | on-demand digest, same content as email | 200 | — |

All request params validated with `spring-boot-starter-validation`; invalid input → clean 4xx,
never 500. DTOs mapped to/from entities with **MapStruct** (type-safe, no manual boilerplate).

---

## 6. Tech stack & libraries (your choices)
- **Java 25**, **Spring Boot 3.5.16** (latest 3.5.x; supports Java 25).
- **Spring Web** — REST API.
- **Spring Data JPA** (Hibernate) — persistence.
- **PostgreSQL 16** (Docker) — system of record; `ddl-auto: none`, schema owned by Liquibase.
- **Liquibase** — DB changesets (`src/main/resources/db/changelog/`), versioned `0001-…`.
- **MapStruct 1.6.3** — entity ↔ DTO mapping (annotation-processor at compile time).
- **Jsoup** — polite HTML parsing of olx.ba listing pages.
- **Spring Cache** (`simple`/`ConcurrentMapCacheManager`) — cohort-average cache.
- **Spring Mail + Mailhog** (dev SMTP, test mode) — digest emails; UI at `:8025`.
- **Spring `@Scheduled`** — scraper run + digest job, off the request path.
- **Maven** (wrapper added in Phase 4; system Maven 3.9+ for Phase 2).

---

## 7. Architecture & component map
```
Cron trigger (@Scheduled, every 2h)
  → ScraperService.fetchListings(source)
      • polite HTTP fetch (Jsoup) + parse cards (title, price KM, year, km, url)
      • dedupe by external_id  → idempotent upsert (insert-or-update, never duplicate)
      • on price change → append PriceHistory row
      → save Listing (+ PriceHistory) into Postgres

PricingService.scoreListing(listing)
  → CohortSignature(model, year±2, mileageBracket)
  → cached CohortStats (avg/median/count) — recompute only on cache miss
  → % under/over vs cohort avg → flag "good deal" when ≥ threshold (default 10% under)

Scheduled digest job (daily 07:00)
  → pick listings flagged "good deal" since last run
  → render email → send via Mailhog (dev)

REST API (Spring Web):
  GET /listings  GET /listings/{id}  GET /stats  POST /watchlists  GET /report
```
**Politeness contract (ScraperService):** single-threaded; ≥ `SCRAPE_DELAY_MS` (default 2000 ms)
between requests; descriptive `User-Agent` + contact; honor `429`/retry-after; cache fetched
pages during development to avoid repeated live hits; bounded page count per run.

---

## 8. Concept mapping (the 5+ rule)
| # | Concept | Where it lives |
|---|---------|----------------|
| 1 | Web scraping pipeline (swap) | `ScraperService` — polite Jsoup fetch of olx.ba, idempotent upsert by `external_id` |
| 2 | Database | PostgreSQL — `listing`, `price_history` (Liquibase-managed), survives restart |
| 3 | Cron / background job | `@Scheduled` scraper run + scheduled digest email job |
| 4 | Reporting — email | Digest email (Mailhog) + `/report` endpoint, same content |
| 5 | Caching | Cohort average (model/year/mileage bracket) cached via Spring Cache |
| 6 | Authentication (stretch) | Watchlists tied to a logged-in user; owner-only access |
| 7 | API endpoints | REST endpoints above, correct status codes + validation |

5 core concepts, exactly **one swap** (web scraping), satisfying "max 2 swaps, ≥3 from original list".

---

## 9. Build order (phases) & what this Phase 1 delivers
- **Phase 1 (this phase):** Design one-pager ✅, source allowed ✅, exact category URL noted ✅,
  data model + API surface + stack locked ✅, infra/config scaffolded (pom, docker-compose,
  .env.example, Liquibase master + first changeset). **No running app yet.**
- **Phase 2 (next):** Walking skeleton — `@SpringBootApplication` + `ScraperService` scheduled
  scrape → Liquibase-created tables → `GET /listings` returns JSON.
- **Phase 3:** price history → cohort caching + scoring → email digest → `/report`.
- **Phase 4:** README + seed script + 5-minute demo path + Maven wrapper.
- **Phase 5:** overview doc + concept table + public repo.

---

## 10. Run / setup (preview — finalized in Phase 4)
```bash
# 1. Start Postgres + Mailhog
docker compose up -d

# 2. Build & run (Maven required; wrapper added in Phase 4)
./mvnw spring-boot:run        # or: mvn spring-boot:run

# 3. Inspect
curl localhost:8080/listings  # available from Phase 2
# Mailhog UI: http://localhost:8025
```
Env via `.env` (see `.env.example`): `DB_*`, `MAIL_*`, `SCRAPE_BASE_URL`, `SCRAPE_DELAY_MS`,
`SCRAPE_CRON`, `DIGEST_CRON`, scoring thresholds.
