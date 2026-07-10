#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

cd "$SCRIPT_DIR"

if [ ! -d node_modules ]; then
  npm install
fi

OUTPUT_DIR="$REPO_ROOT/crawlers-data/lex.bg" \
MAX_DOCS="${MAX_DOCS:0}" \
DELAY_MS="${DELAY_MS:-1500}" \
RECRAWL_DAYS="${RECRAWL_DAYS:-7}" \
node index.js