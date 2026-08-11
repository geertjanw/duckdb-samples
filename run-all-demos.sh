#!/usr/bin/env bash
#
# run-all-demos.sh — start every DuckDB ODBC demo at once.
#
# All five demos are the same app in a different language and each one, on its
# own, listens on port 8080 and expects its own MariaDB on 3306. They also share
# an identical docker-compose.yml (same container name, same seed data), so this
# script brings up ONE shared MariaDB and then starts each app on its own port:
#
#     Java   (Spring Boot) -> http://localhost:8081
#     Python (FastAPI)     -> http://localhost:8082
#     Node   (Express)     -> http://localhost:8083
#     Go     (net/http)    -> http://localhost:8084
#     Rust   (Axum)        -> http://localhost:8085
#
# A demo whose toolchain (java/mvn, python3, node/npm, go, cargo) is not
# installed is skipped with a note; the rest still run. Logs are written to
# ./logs/<demo>.log. Press Ctrl+C to stop every app and tear down MariaDB.
#
set -uo pipefail

cd "$(dirname "$0")"
ROOT="$(pwd)"
LOG_DIR="$ROOT/logs"
mkdir -p "$LOG_DIR"

PIDS=()

cleanup() {
  echo
  echo "==> Shutting down demos..."
  for pid in "${PIDS[@]:-}"; do
    [ -n "$pid" ] && kill "$pid" 2>/dev/null || true
  done
  echo "==> Stopping shared MariaDB..."
  (cd "$ROOT/duckdb-odbc-go-demo" && docker compose down) >/dev/null 2>&1 || true
  echo "==> Done."
}
trap cleanup INT TERM EXIT

have() { command -v "$1" >/dev/null 2>&1; }

# 1. One shared MariaDB (every demo's docker-compose.yml is identical).
if ! have docker; then
  echo "ERROR: docker is required for the shared MariaDB. Install Docker and retry." >&2
  exit 1
fi
echo "==> Starting shared MariaDB (port 3306)..."
(cd "$ROOT/duckdb-odbc-go-demo" && docker compose up -d)

echo "==> Waiting for MariaDB to accept connections..."
for i in $(seq 1 60); do
  if docker exec duckdb-odbc-mysql mariadb -uapp_user -papp_password -e 'SELECT 1' demo >/dev/null 2>&1; then
    echo "    MariaDB is ready."
    break
  fi
  sleep 2
  if [ "$i" -eq 60 ]; then
    echo "ERROR: MariaDB did not become ready in time." >&2
    exit 1
  fi
done

start() { # name  port  dir  command...
  local name="$1" port="$2" dir="$3"; shift 3
  echo "==> Starting $name on http://localhost:$port  (log: logs/$name.log)"
  ( cd "$ROOT/$dir" && "$@" ) >"$LOG_DIR/$name.log" 2>&1 &
  PIDS+=("$!")
}

# 2. Each app on its own port, only if its toolchain is present.
if have mvn; then
  start java 8081 duckdb-odbc-java-demo \
    env SERVER_PORT=8081 mvn -q spring-boot:run
else
  echo "--  Skipping Java demo (mvn not found)."
fi

if have python3; then
  ( cd "$ROOT/duckdb-odbc-python-demo" \
      && { [ -d .venv ] || python3 -m venv .venv; } \
      && ./.venv/bin/pip install -q -r requirements.txt \
      && ./.venv/bin/uvicorn app.main:app --port 8082 ) \
    >"$LOG_DIR/python.log" 2>&1 &
  PIDS+=("$!")
  echo "==> Starting python on http://localhost:8082  (log: logs/python.log)"
else
  echo "--  Skipping Python demo (python3 not found)."
fi

if have npm; then
  ( cd "$ROOT/duckdb-odbc-node-demo" && npm install --silent && PORT=8083 npm start ) \
    >"$LOG_DIR/node.log" 2>&1 &
  PIDS+=("$!")
  echo "==> Starting node on http://localhost:8083  (log: logs/node.log)"
else
  echo "--  Skipping Node demo (npm not found)."
fi

if have go; then
  start go 8084 duckdb-odbc-go-demo env PORT=8084 go run .
else
  echo "--  Skipping Go demo (go not found)."
fi

if have cargo; then
  start rust 8085 duckdb-odbc-rust-demo env PORT=8085 cargo run --release
else
  echo "--  Skipping Rust demo (cargo not found)."
fi

echo
echo "==> All available demos are starting. First-time builds (mvn/cargo/npm) can take a while;"
echo "    tail the logs in ./logs to watch progress. Press Ctrl+C to stop everything."
echo
wait
