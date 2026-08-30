-- Auto Tracker seed data (Phase 3 demo)
-- Run after Liquibase has created the schema. Idempotent: keyed by external_id (UNIQUE).
-- Provides 1-2 car models (VW Golf 7, Škoda Octavia) with enough comparables to show
-- fair-price scoring + a deliberately underpriced "good deal" + a price drop.
--
-- Apply with the postgres container (psql not on host PATH):
--   docker cp src/main/resources/db/seed.sql auto-tracker-db:/seed.sql
--   docker exec -e PGPASSWORD=auto_tracker auto-tracker-db psql -U auto_tracker -d auto_tracker -f /seed.sql
-- Or with a local psql:
--   psql "postgresql://auto_tracker:auto_tracker@localhost:15432/auto_tracker" -f src/main/resources/db/seed.sql
-- (Docker Compose maps Postgres to host :15432; native installs use :5432.)

\set ON_ERROR_STOP on

DO $$
DECLARE
  v_now timestamptz := now();
  v_first timestamptz := now() - interval '5 days';
  v_id int;
BEGIN
  -- Idempotent re-seed: drop any prior seed rows (keyed by external_id prefix 'seed-') first.
  DELETE FROM price_history
   WHERE listing_id IN (SELECT id FROM listing WHERE external_id LIKE 'seed-%');
  DELETE FROM listing WHERE external_id LIKE 'seed-%';

  -- VW Golf 7 cohort (year ~2017, mileage ~80-100k) -----------------------------------------
  -- Fair-priced comparables around 22.000-26.000 KM
  INSERT INTO listing (id, external_id, source, title, brand, model, price, currency, year, mileage_km, fuel_type, location, url, first_seen_at, last_seen_at, is_active, created_at, updated_at)
  VALUES
    (nextval('listing_id_seq'),'seed-1001','olx.ba','VW Golf 7 1.6 TDI','VW','Golf 7',24900,'KM',2017,92000,'dizel','Sarajevo','https://olx.ba/artikal/seed-1001',v_first,v_now,true,v_first,v_now),
    (nextval('listing_id_seq'),'seed-1002','olx.ba','VW Golf 7 1.4 TSI','VW','Golf 7',23900,'KM',2017,88000,'benzin','Tuzla','https://olx.ba/artikal/seed-1002',v_first,v_now,true,v_first,v_now),
    (nextval('listing_id_seq'),'seed-1003','olx.ba','VW Golf 7 2.0 TDI','VW','Golf 7',25900,'KM',2018,78000,'dizel','Zenica','https://olx.ba/artikal/seed-1003',v_first,v_now,true,v_first,v_now),
    (nextval('listing_id_seq'),'seed-1004','olx.ba','VW Golf 7 1.6 TDI Highline','VW','Golf 7',22900,'KM',2016,102000,'dizel','Mostar','https://olx.ba/artikal/seed-1004',v_first,v_now,true,v_first,v_now);

  -- The GOOD DEAL: a Golf 7 priced well below the cohort average (~20k vs ~24k => underpriced)
  INSERT INTO listing (id, external_id, source, title, brand, model, price, currency, year, mileage_km, fuel_type, location, url, first_seen_at, last_seen_at, is_active, created_at, updated_at)
  VALUES
    (nextval('listing_id_seq'),'seed-1005','olx.ba','VW Golf 7 1.6 TDI Trendline','VW','Golf 7',19900,'KM',2017,95000,'dizel','Banja Luka','https://olx.ba/artikal/seed-1005',v_first,v_now,true,v_first,v_now);

  -- Price history for the good deal (shows a price drop from 23.500 -> 19.900)
  SELECT id INTO v_id FROM listing WHERE external_id = 'seed-1005';
  INSERT INTO price_history (id, listing_id, price, currency, recorded_at)
  VALUES (nextval('price_history_id_seq'), v_id, 23500, 'KM', v_first);
  INSERT INTO price_history (id, listing_id, price, currency, recorded_at)
  VALUES (nextval('price_history_id_seq'), v_id, 19900, 'KM', v_now);

  -- A normal-priced car that later DROPPED price (Octavia)
  INSERT INTO listing (id, external_id, source, title, brand, model, price, currency, year, mileage_km, fuel_type, location, url, first_seen_at, last_seen_at, is_active, created_at, updated_at)
  VALUES
    (nextval('listing_id_seq'),'seed-2001','olx.ba','Škoda Octavia 1.6 TDI','Škoda','Octavia',27900,'KM',2019,64000,'dizel','Sarajevo','https://olx.ba/artikal/seed-2001',v_first,v_now,true,v_first,v_now);
  SELECT id INTO v_id FROM listing WHERE external_id = 'seed-2001';
  INSERT INTO price_history (id, listing_id, price, currency, recorded_at)
  VALUES (nextval('price_history_id_seq'), v_id, 30500, 'KM', v_first);
  INSERT INTO price_history (id, listing_id, price, currency, recorded_at)
  VALUES (nextval('price_history_id_seq'), v_id, 27900, 'KM', v_now);

  -- Octavia comparables
  INSERT INTO listing (id, external_id, source, title, brand, model, price, currency, year, mileage_km, fuel_type, location, url, first_seen_at, last_seen_at, is_active, created_at, updated_at)
  VALUES
    (nextval('listing_id_seq'),'seed-2002','olx.ba','Škoda Octavia 1.4 TSI','Škoda','Octavia',28900,'KM',2019,58000,'benzin','Tuzla','https://olx.ba/artikal/seed-2002',v_first,v_now,true,v_first,v_now),
    (nextval('listing_id_seq'),'seed-2003','olx.ba','Škoda Octavia 2.0 TDI','Škoda','Octavia',29900,'KM',2020,52000,'dizel','Zenica','https://olx.ba/artikal/seed-2003',v_first,v_now,true,v_first,v_now),
    (nextval('listing_id_seq'),'seed-2004','olx.ba','Škoda Octavia 1.6 TDI Ambition','Škoda','Octavia',26900,'KM',2018,72000,'dizel','Mostar','https://olx.ba/artikal/seed-2004',v_first,v_now,true,v_first,v_now);
END $$;
