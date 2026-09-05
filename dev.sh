#!/bin/sh
# Bring the stack up and leave it up, for working on tests.
#
# Unlike run.sh this does not execute anything: it starts the environment and
# hands you the loop
#
#   docker compose exec runner mvn verify -Dit.test=CourseCatalogueIT
#
# which re-runs against a warm stack in seconds. src/ is bind-mounted, so a test
# edited on the host is picked up by the next mvn run with no rebuild.
#
#   ./dev.sh                                      chrome, headless
#   ./dev.sh browsers=chrome record=chrome        record every session to video
#   ./dev.sh browsers=chrome,firefox,edge

set -e

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$ROOT"

. "$ROOT/lib/stack-env.sh"

BROWSERS="chrome"
RECORD=""
SESSIONS=""

for arg in "$@"; do
    case "$arg" in
        browsers=*) BROWSERS="${arg#*=}" ;;
        record=*)   RECORD="${arg#*=}" ;;
        sessions=*) SESSIONS="${arg#*=}" ;;
        *)
            echo "Unsupported argument: $arg"
            echo "Usage: ./dev.sh [browsers=...] [record=...] [sessions=N]"
            exit 1
            ;;
    esac
done

sh "$ROOT/scripts/prepare-env.sh"
APP_URL=$(read_env "$ROOT/.env" APP_URL)

validate_browsers "$BROWSERS" "$RECORD"
resolve_parallelism "$SESSIONS" "$RECORD"
announce "$BROWSERS" "$RECORD" "$PARALLELISM"

sh "$ROOT/scripts/prepare-app.sh"
prepull_images "$BROWSERS" "$RECORD"
generate_grid_config "$BROWSERS" "$PARALLELISM"

export BROWSERS RECORD_VIDEO="$RECORD" PARALLELISM

# shellcheck disable=SC2086
docker compose up -d --build $STACK_SERVICES

cat <<EOF

The stack is up.

  Run everything      docker compose exec runner mvn verify
  Run one class       docker compose exec runner mvn verify -Dit.test=LandingIT
  Run one tag         docker compose exec runner mvn verify -Dgroups=smoke

  Grid console        http://localhost:4444  (live sessions and containers)
  Mailbox             http://localhost:8025
  Recordings          ./videos/<session-id>/<test-name>.mp4  (with record=)

  Stop everything     docker compose down
EOF
