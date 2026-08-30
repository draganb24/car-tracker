#!/usr/bin/env bash
# 5-minute end-to-end demo of Auto Tracker.
# Prereqs: docker compose up -d, app built & running on :8080, ./scripts/seed.sh already run.
# Usage:   ./scripts/demo.sh
set -euo pipefail

B="${BASE_URL:-http://localhost:8080}"

hr() { printf '\n\033[1;34m==== %s ====\033[0m\n' "$1"; }

# Pick a Python interpreter by TESTING execution (avoids Windows Store `python3` stub
# that exists in PATH but prints "Python was not found" when run with arguments).
PY=""
for cand in python python3; do
  if command -v "$cand" >/dev/null 2>&1 && "$cand" -c "import json" >/dev/null 2>&1; then
    PY="$cand"
    break
  fi
done
if [ -z "$PY" ]; then
  echo "Python (with json) is required to run this demo. Install Python 3 or set BASE_URL and edit the script." >&2
  exit 1
fi

hr "1. Listings (filter: Golf 7)"
curl -s "$B/listings?model=Golf%207&size=3" \
  | $PY -c "import sys,json; d=json.load(sys.stdin); print('total active listings:', d['totalElements']); [print(' -', x['title'], x['price'], x['currency'], x['year']) for x in d['content'][:3]]"

hr "2. Cohort stats for Golf 7, 2017 (cached after first call)"
curl -s "$B/stats?model=Golf%207&year=2017"

hr "3. Detail of a listing — fair-price verdict + price history"
# Find the planted good deal (seed-1005) and show its verdict
ID=$(curl -s "$B/listings?size=200" | $PY -c "import sys,json; d=json.load(sys.stdin); print(next((x['id'] for x in d['content'] if x['externalId']=='seed-1005'),''))")
curl -s "$B/listings/$ID" \
  | $PY -c "import sys,json; d=json.load(sys.stdin); fp=d.get('fairPrice') or {}; print(' model:',d['model'],'| price:',d['price'],'| verdict:',fp.get('priceLabel'),'| delta%:',fp.get('deltaPercent'),'| goodDeal:',fp.get('goodDeal')); print(' price history rows:',len(d.get('priceHistory',[])))"

hr "4. On-demand digest (same content as the email)"
curl -s "$B/report" \
  | $PY -c "import sys,json; d=json.load(sys.stdin); print(' title:',d['title']); print(' good deals:',d['goodDealsCount']); print(' notable price drops:',len(d.get('notablePriceDrops',[])))"

hr "5. Send digest email -> Mailhog"
curl -s -X POST "$B/digest/send" \
  | $PY -c "import sys,json; d=json.load(sys.stdin); print(' sent:',d)"
echo "Open the Mailhog UI to see it:  http://localhost:8025"

hr "Demo complete. Try the live scrape too:  curl -X POST $B/scrape"
