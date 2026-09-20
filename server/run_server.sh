#!/usr/bin/env sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

HOST=${APP_GOOD_WORDS_HOST:-127.0.0.1}
PORT=${APP_GOOD_WORDS_PORT:-8765}
DB=${APP_GOOD_WORDS_DB:-"$SCRIPT_DIR/app-good-words.db.json"}

exec node "$SCRIPT_DIR/app_good_words_server.mjs" \
  --host "$HOST" \
  --port "$PORT" \
  --db "$DB" \
  --seed
