#!/usr/bin/env bash
# Smoke-test for the deployed mcp-google server.
# Tests every read-only tool and shows full pretty-printed responses.
# Skips destructive tools (send, create, delete) unless DESTRUCTIVE=1.
set -euo pipefail

BASE_URL="${MCP_URL:-https://google-assistant.pavel-usanli.online/mcp}"
NOW=$(date -u +"%Y-%m-%dT%H:%M:%SZ")
TOMORROW=$(date -u -v+1d +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null || date -u -d "+1 day" +"%Y-%m-%dT%H:%M:%SZ")

# ── helpers ──────────────────────────────────────────────────────────────────

die()     { echo "ERROR: $*" >&2; exit 1; }
section() { echo ""; echo "══════════════════════════════════════════════════"; echo "  $*"; echo "══════════════════════════════════════════════════"; }
header()  { echo ""; echo "── $* ──"; }

for cmd in curl jq; do
  command -v "$cmd" &>/dev/null || die "'$cmd' is required but not installed."
done

# ── token ────────────────────────────────────────────────────────────────────

if [[ -z "${ACCESS_TOKEN:-}" ]]; then
  echo ""
  echo "Paste your Google OAuth access token (will not be echoed):"
  read -r -s ACCESS_TOKEN
  echo ""
fi
[[ -n "$ACCESS_TOKEN" ]] || die "ACCESS_TOKEN is empty."

# ── core helpers ──────────────────────────────────────────────────────────────

PASS=0; FAIL=0

# Send one MCP JSON-RPC call; returns the JSON-RPC response body (SSE unwrapped).
mcp_call() {
  local method="$1" params="$2"
  curl -s -X POST "$BASE_URL" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json, text/event-stream" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -d "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"$method\",\"params\":$params}" \
  | grep '^data: ' | sed 's/^data: //' | head -1
}

# Print status + full pretty response for an already-fetched body.
print_result() {
  local label="$1" body="$2"
  header "$label"
  if [[ -z "$body" ]]; then
    echo "  STATUS: FAIL (empty response)"
    FAIL=$((FAIL + 1))
    return
  fi
  if echo "$body" | jq -e '.error' &>/dev/null; then
    echo "  STATUS: FAIL"
    echo "$body" | jq '.error'
    FAIL=$((FAIL + 1))
  else
    echo "  STATUS: OK"
    local content
    content=$(echo "$body" | jq -r '.result.content[0].text // empty' 2>/dev/null || true)
    if [[ -n "$content" ]]; then
      echo "$content" | jq '.' 2>/dev/null || echo "$content"
    else
      echo "$body" | jq '.result'
    fi
    PASS=$((PASS + 1))
  fi
}

# Call + print in one step (for tools where we don't need to reuse the response).
run_test() {
  local label="$1" method="$2" params="$3"
  local body
  body=$(mcp_call "$method" "$params")
  print_result "$label" "$body"
}

# ── 1 · Protocol ──────────────────────────────────────────────────────────────

section "1 · Protocol"

run_test "initialize" "initialize" \
  '{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"0"}}'

header "tools/list"
TOOLS_RESP=$(mcp_call "tools/list" '{}')
if echo "$TOOLS_RESP" | jq -e '.error' &>/dev/null; then
  echo "  STATUS: FAIL"
  echo "$TOOLS_RESP" | jq '.error'
  FAIL=$((FAIL + 1))
else
  echo "  STATUS: OK"
  echo "  Registered tools:"
  echo "$TOOLS_RESP" | jq -r '.result.tools[].name' | sed 's/^/    • /'
  PASS=$((PASS + 1))
fi

# ── 2 · Gmail ─────────────────────────────────────────────────────────────────

section "2 · Gmail"

GMAIL_LIST=$(mcp_call "tools/call" '{"name":"gmail_list_messages","arguments":{"maxResults":3}}')
print_result "gmail_list_messages (maxResults=3)" "$GMAIL_LIST"

FIRST_MSG_ID=$(echo "$GMAIL_LIST" \
  | jq -r '.result.content[0].text // empty' 2>/dev/null \
  | jq -r '.messages[0].id // empty' 2>/dev/null || true)

if [[ -n "$FIRST_MSG_ID" ]]; then
  run_test "gmail_get_message (id=$FIRST_MSG_ID, format=metadata)" "tools/call" \
    "{\"name\":\"gmail_get_message\",\"arguments\":{\"messageId\":\"$FIRST_MSG_ID\",\"format\":\"metadata\"}}"
else
  header "gmail_get_message"
  echo "  SKIPPED — no message ID returned by previous call"
fi

run_test "gmail_search_messages (query='in:inbox', maxResults=3)" "tools/call" \
  '{"name":"gmail_search_messages","arguments":{"query":"in:inbox","maxResults":3}}'

# ── 3 · Calendar ──────────────────────────────────────────────────────────────

section "3 · Calendar"

CAL_LIST=$(mcp_call "tools/call" '{"name":"calendar_list_calendars","arguments":{}}')
print_result "calendar_list_calendars" "$CAL_LIST"

PRIMARY_CAL=$(echo "$CAL_LIST" \
  | jq -r '.result.content[0].text // empty' 2>/dev/null \
  | jq -r '.[] | select(.primary==true) | .id' 2>/dev/null | head -1 || true)
PRIMARY_CAL="${PRIMARY_CAL:-primary}"
echo "  (primary calendar id: $PRIMARY_CAL)"

run_test "calendar_list_events (primary, from now, maxResults=5)" "tools/call" \
  "{\"name\":\"calendar_list_events\",\"arguments\":{\"calendarIds\":[\"primary\"],\"timeMin\":\"$NOW\",\"maxResults\":5}}"

run_test "calendar_search_events (query='meeting', primary, from now)" "tools/call" \
  "{\"name\":\"calendar_search_events\",\"arguments\":{\"query\":\"meeting\",\"calendarIds\":[\"primary\"],\"timeMin\":\"$NOW\",\"maxResults\":5}}"

run_test "calendar_check_availability (self: $PRIMARY_CAL, now→tomorrow)" "tools/call" \
  "{\"name\":\"calendar_check_availability\",\"arguments\":{\"emails\":[\"$PRIMARY_CAL\"],\"timeMin\":\"$NOW\",\"timeMax\":\"$TOMORROW\"}}"

# ── 4 · Destructive (opt-in) ──────────────────────────────────────────────────

if [[ "${DESTRUCTIVE:-0}" == "1" ]]; then
  section "4 · Destructive (DESTRUCTIVE=1)"

  run_test "gmail_send_message (test email to self)" "tools/call" \
    "{\"name\":\"gmail_send_message\",\"arguments\":{\"to\":\"$PRIMARY_CAL\",\"subject\":\"mcp-google test\",\"body\":\"Automated test from test.sh.\"}}"

  CREATE_RESP=$(mcp_call "tools/call" \
    "{\"name\":\"calendar_create_event\",\"arguments\":{\"summary\":\"mcp-google test event\",\"start\":\"$NOW\",\"end\":\"$TOMORROW\",\"calendarId\":\"primary\"}}")
  print_result "calendar_create_event (test event)" "$CREATE_RESP"

  EVENT_ID=$(echo "$CREATE_RESP" \
    | jq -r '.result.content[0].text // empty' 2>/dev/null \
    | jq -r '.id // empty' 2>/dev/null || true)

  if [[ -n "$EVENT_ID" ]]; then
    run_test "calendar_get_event (id=$EVENT_ID)" "tools/call" \
      "{\"name\":\"calendar_get_event\",\"arguments\":{\"eventId\":\"$EVENT_ID\",\"calendarId\":\"primary\"}}"

    run_test "calendar_delete_event (id=$EVENT_ID)" "tools/call" \
      "{\"name\":\"calendar_delete_event\",\"arguments\":{\"eventId\":\"$EVENT_ID\",\"calendarId\":\"primary\"}}"
  fi
fi

# ── Summary ───────────────────────────────────────────────────────────────────

section "Summary"
echo "  PASS: $PASS   FAIL: $FAIL"
echo ""
[[ $FAIL -eq 0 ]]