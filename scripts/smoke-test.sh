#!/usr/bin/env bash
#
# EventZone backend smoke test.
#
# Builds the app, starts it, and walks through the full happy-path flow via curl
# (register/login, browse events, book a ticket, cancel it, organiser + admin
# actions), asserting the HTTP status code at every step. This is meant to give a
# fast, concrete "yes, it actually works end-to-end" signal beyond `mvn test`
# (which only exercises the service layer in isolation with mocks).
#
# Usage (from the backend/ directory):
#   ./scripts/smoke-test.sh
#
# Requires: Java 17+, Maven, curl, python3 (used only to pull fields out of JSON
# responses — no other dependency).
#
set -uo pipefail

BASE_URL="http://localhost:8080"
JAR_DIR="target"
LOG_FILE="/tmp/eventzone-smoke-test.log"
PASS=0
FAIL=0

json_field() {
  # json_field '<json>' 'key' -> prints the value, or nothing if missing/absent
  python3 -c "
import sys, json
try:
    data = json.loads(sys.argv[1])
    val = data.get(sys.argv[2], '')
    print(val if val is not None else '')
except Exception:
    print('')
" "$1" "$2"
}

json_first_id() {
  # json_first_id '<json array>' -> id of first element, or nothing
  python3 -c "
import sys, json
try:
    data = json.loads(sys.argv[1])
    print(data[0]['id'] if isinstance(data, list) and data else '')
except Exception:
    print('')
"
}

check() {
  local desc="$1" expected="$2" actual="$3"
  if [ "$actual" = "$expected" ]; then
    echo "  [PASS] $desc (HTTP $actual)"
    PASS=$((PASS + 1))
  else
    echo "  [FAIL] $desc (expected HTTP $expected, got $actual)"
    FAIL=$((FAIL + 1))
  fi
}

cleanup() {
  if [ -n "${APP_PID:-}" ] && kill -0 "$APP_PID" 2>/dev/null; then
    echo ""
    echo "Stopping backend (pid $APP_PID)..."
    kill "$APP_PID" 2>/dev/null
    wait "$APP_PID" 2>/dev/null
  fi
}
trap cleanup EXIT

echo "== 1. Building (mvn -q -DskipTests package) =="
if ! mvn -q -DskipTests package; then
  echo "Build failed — fix compile errors before running the smoke test. See above for the Maven error."
  exit 1
fi

JAR_FILE=$(ls "$JAR_DIR"/eventzone-backend*.jar 2>/dev/null | head -1)
if [ -z "$JAR_FILE" ]; then
  echo "No jar found in $JAR_DIR after build — aborting."
  exit 1
fi

echo "== 2. Starting $JAR_FILE (log: $LOG_FILE) =="
nohup java -jar "$JAR_FILE" > "$LOG_FILE" 2>&1 &
APP_PID=$!

echo "Waiting for the app to become ready on $BASE_URL ..."
READY=0
for i in $(seq 1 60); do
  if curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/categories" 2>/dev/null | grep -q "200"; then
    READY=1
    break
  fi
  sleep 1
done
if [ "$READY" -ne 1 ]; then
  echo "App did not become ready within 60s. Last 40 log lines:"
  tail -40 "$LOG_FILE"
  exit 1
fi
echo "App is up."
echo ""

echo "== 3. Walking the happy path =="

echo "-- Auth --"
LOGIN_ADMIN=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/auth/login" -H "Content-Type: application/json" -d '{"email":"admin@eventzone.com","password":"Password@123"}')
ADMIN_CODE=$(echo "$LOGIN_ADMIN" | tail -1)
ADMIN_BODY=$(echo "$LOGIN_ADMIN" | sed '$d')
check "Login as admin" 200 "$ADMIN_CODE"
ADMIN_TOKEN=$(json_field "$ADMIN_BODY" token)

LOGIN_ORG=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/auth/login" -H "Content-Type: application/json" -d '{"email":"organiser1@eventzone.com","password":"Password@123"}')
ORG_CODE=$(echo "$LOGIN_ORG" | tail -1)
ORG_BODY=$(echo "$LOGIN_ORG" | sed '$d')
check "Login as organiser" 200 "$ORG_CODE"
ORG_TOKEN=$(json_field "$ORG_BODY" token)

RAND=$RANDOM$RANDOM
REGISTER_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/auth/register" -H "Content-Type: application/json" \
  -d "{\"email\":\"smoketest${RAND}@eventzone.com\",\"password\":\"Password@123\",\"name\":\"Smoke Test\"}")
check "Register new attendee" 201 "$REGISTER_CODE"

LOGIN_ATTENDEE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/auth/login" -H "Content-Type: application/json" \
  -d "{\"email\":\"smoketest${RAND}@eventzone.com\",\"password\":\"Password@123\"}")
ATT_CODE=$(echo "$LOGIN_ATTENDEE" | tail -1)
ATT_BODY=$(echo "$LOGIN_ATTENDEE" | sed '$d')
check "Login as newly-registered attendee" 200 "$ATT_CODE"
ATTENDEE_TOKEN=$(json_field "$ATT_BODY" token)

echo "-- Categories & Events --"
CATS=$(curl -s "$BASE_URL/api/categories")
CATEGORY_ID=$(json_first_id "$CATS")
CATS_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/categories")
check "List categories" 200 "$CATS_CODE"

EVENTS=$(curl -s "$BASE_URL/api/events")
EVENT_ID=$(json_first_id "$EVENTS")
EVENTS_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/events")
check "List events" 200 "$EVENTS_CODE"

DETAIL=$(curl -s "$BASE_URL/api/events/$EVENT_ID")
DETAIL_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/events/$EVENT_ID")
check "Get event detail" 200 "$DETAIL_CODE"
TICKET_CATEGORY_ID=$(python3 -c "
import sys, json
try:
    data = json.loads(sys.argv[1])
    print(data['ticketCategories'][0]['id'])
except Exception:
    print('')
" "$DETAIL")

echo "-- Organiser: create event + ticket category --"
CREATE_EVENT_RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/events" \
  -H "Content-Type: application/json" -H "Authorization: Bearer $ORG_TOKEN" \
  -d "{\"title\":\"Smoke Test Concert\",\"description\":\"created by smoke-test.sh\",\"eventDate\":\"2026-12-01T19:00:00\",\"venue\":\"Test Venue\",\"coverImageUrl\":\"https://example.com/x.jpg\",\"categoryId\":\"$CATEGORY_ID\"}")
CREATE_EVENT_CODE=$(echo "$CREATE_EVENT_RESP" | tail -1)
CREATE_EVENT_BODY=$(echo "$CREATE_EVENT_RESP" | sed '$d')
check "Organiser creates event" 201 "$CREATE_EVENT_CODE"
NEW_EVENT_ID=$(json_field "$CREATE_EVENT_BODY" id)

CREATE_TC_RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/events/$NEW_EVENT_ID/ticket-categories" \
  -H "Content-Type: application/json" -H "Authorization: Bearer $ORG_TOKEN" \
  -d '{"name":"General","price":500.00,"totalSeats":10}')
CREATE_TC_CODE=$(echo "$CREATE_TC_RESP" | tail -1)
CREATE_TC_BODY=$(echo "$CREATE_TC_RESP" | sed '$d')
check "Organiser adds ticket category" 201 "$CREATE_TC_CODE"
NEW_TC_ID=$(json_field "$CREATE_TC_BODY" id)

echo "-- Attendee: book + view + cancel --"
BOOK_RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/bookings" \
  -H "Content-Type: application/json" -H "Authorization: Bearer $ATTENDEE_TOKEN" \
  -d "{\"ticketCategoryId\":\"$NEW_TC_ID\",\"quantity\":2}")
BOOK_CODE=$(echo "$BOOK_RESP" | tail -1)
BOOK_BODY=$(echo "$BOOK_RESP" | sed '$d')
check "Attendee books 2 tickets" 201 "$BOOK_CODE"
BOOKING_ID=$(json_field "$BOOK_BODY" id)

OVERBOOK_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/bookings" \
  -H "Content-Type: application/json" -H "Authorization: Bearer $ATTENDEE_TOKEN" \
  -d "{\"ticketCategoryId\":\"$NEW_TC_ID\",\"quantity\":50}")
check "Booking more than available seats is rejected" 400 "$OVERBOOK_CODE"

MINE_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/bookings/mine" -H "Authorization: Bearer $ATTENDEE_TOKEN")
check "Attendee views their bookings" 200 "$MINE_CODE"

CANCEL_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$BASE_URL/api/bookings/$BOOKING_ID/cancel" -H "Authorization: Bearer $ATTENDEE_TOKEN")
check "Attendee cancels their booking" 200 "$CANCEL_CODE"

DOUBLE_CANCEL_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$BASE_URL/api/bookings/$BOOKING_ID/cancel" -H "Authorization: Bearer $ATTENDEE_TOKEN")
check "Cancelling an already-cancelled booking is rejected" 400 "$DOUBLE_CANCEL_CODE"

echo "-- Authorization checks --"
NO_AUTH_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/bookings/mine")
check "Bookings without a token is rejected" 401 "$NO_AUTH_CODE"

WRONG_ROLE_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/api/admin/categories" \
  -H "Content-Type: application/json" -H "Authorization: Bearer $ATTENDEE_TOKEN" -d '{"name":"Should Fail"}')
check "Attendee cannot create a category (ADMIN only)" 403 "$WRONG_ROLE_CODE"

echo "-- Organiser dashboard & Admin --"
ORG_DASH_CODE=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/organiser/events" -H "Authorization: Bearer $ORG_TOKEN")
check "Organiser views their dashboard" 200 "$ORG_DASH_CODE"

DEACTIVATE_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$BASE_URL/api/admin/events/$NEW_EVENT_ID/deactivate" -H "Authorization: Bearer $ADMIN_TOKEN")
check "Admin deactivates the smoke-test event" 200 "$DEACTIVATE_CODE"

REACTIVATE_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X PUT "$BASE_URL/api/admin/events/$NEW_EVENT_ID/activate" -H "Authorization: Bearer $ADMIN_TOKEN")
check "Admin reactivates it" 200 "$REACTIVATE_CODE"

DELETE_TC_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$BASE_URL/api/ticket-categories/$NEW_TC_ID" -H "Authorization: Bearer $ORG_TOKEN")
check "Organiser deletes the ticket category" 204 "$DELETE_TC_CODE"

DELETE_EVENT_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X DELETE "$BASE_URL/api/events/$NEW_EVENT_ID" -H "Authorization: Bearer $ORG_TOKEN")
check "Organiser deletes the smoke-test event" 204 "$DELETE_EVENT_CODE"

echo ""
echo "== Result: $PASS passed, $FAIL failed =="
if [ "$FAIL" -ne 0 ]; then
  echo "Backend log at $LOG_FILE"
  exit 1
fi
exit 0
