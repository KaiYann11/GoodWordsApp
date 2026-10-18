#!/usr/bin/env sh
# macOS·리눅스에서 서버를 띄웁니다. launchd로 걸어 두면 로그인할 때 저절로 돕니다
# (`launchd/com.appgoodwords.server.plist.example`).
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

HOST=${APP_GOOD_WORDS_HOST:-127.0.0.1}
PORT=${APP_GOOD_WORDS_PORT:-8765}
DB=${APP_GOOD_WORDS_DB:-"$SCRIPT_DIR/app-good-words.db.json"}

# launchd가 띄우면 PATH가 /usr/bin:/bin:/usr/sbin:/sbin뿐이라, Homebrew나 nvm으로 깐 node가
# 보이지 않습니다. 그대로 두면 로그에 "node: command not found" 한 줄만 남아 왜 안 뜨는지
# 알기 어렵습니다. 흔히 깔리는 자리까지 찾아보고, 그래도 없으면 무엇을 해야 하는지 적습니다.
find_node() {
    if [ -n "${APP_GOOD_WORDS_NODE:-}" ]; then
        printf '%s\n' "$APP_GOOD_WORDS_NODE"
        return 0
    fi
    if command -v node >/dev/null 2>&1; then
        command -v node
        return 0
    fi
    for candidate in /opt/homebrew/bin/node /usr/local/bin/node "$HOME/.local/bin/node"; do
        if [ -x "$candidate" ]; then
            printf '%s\n' "$candidate"
            return 0
        fi
    done
    # nvm은 버전마다 폴더가 달라, 눈에 띄는 마지막 것을 씁니다.
    latest=""
    for candidate in "$HOME"/.nvm/versions/node/*/bin/node; do
        [ -x "$candidate" ] && latest="$candidate"
    done
    if [ -n "$latest" ]; then
        printf '%s\n' "$latest"
        return 0
    fi
    return 1
}

if ! NODE=$(find_node); then
    echo "node를 찾지 못했습니다. node를 설치하거나, APP_GOOD_WORDS_NODE에 경로를 지정해 주세요." >&2
    echo "launchd로 띄울 때는 plist의 PATH나 APP_GOOD_WORDS_NODE를 함께 적어야 합니다." >&2
    exit 1
fi

exec "$NODE" "$SCRIPT_DIR/app_good_words_server.mjs" \
  --host "$HOST" \
  --port "$PORT" \
  --db "$DB" \
  --seed
