#!/usr/bin/env bash
# Apply the idempotent seed data to the running Postgres container.
# Usage: ./scripts/seed.sh   (run from the project root; docker compose must be up)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SEED="$ROOT/src/main/resources/db/seed.sql"
CONTAINER="auto-tracker-db"
SQL="$ROOT/src/main/resources/db/seed.sql"

if [ ! -f "$SQL" ]; then
  echo "seed.sql not found at $SQL" >&2
  exit 1
fi

if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER}$"; then
  echo "Container '$CONTAINER' is not running. Start it with: docker compose up -d" >&2
  exit 1
fi

echo "Copying seed.sql into $CONTAINER ..."
docker cp "$SQL" "$CONTAINER:/seed.sql"

echo "Applying seed (idempotent: deletes prior seed-% rows first) ..."
docker exec -e PGPASSWORD=auto_tracker \
  "$CONTAINER" \
  psql -U auto_tracker -d auto_tracker -f /seed.sql

echo "Done. Verify with:"
echo "  curl http://localhost:8080/report"
echo "  curl 'http://localhost:8080/stats?model=Golf%207&year=2017'"
