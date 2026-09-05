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
#   ./dev.sh                                 chrome, headless
#   ./dev.sh browsers=chrome headed=chrome   watch it live at localhost:7900
#   ./dev.sh browsers=chrome,firefox,edge headed=chrome,firefox,edge

set -e

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$ROOT"

. "$ROOT/lib/stack-env.sh"

BROWSERS="chrome"
HEADED=""
SESSIONS=""

for arg in "$@"; do
    case "$arg" in
        browsers=*) BROWSERS="${arg#*=}" ;;
        headed=*)   HEADED="${arg#*=}" ;;
        sessions=*) SESSIONS="${arg#*=}" ;;
        *)
            echo "Unsupported argument: $arg"
            echo "Usage: ./dev.sh [browsers=...] [headed=...] [sessions=N]"
            exit 1
            ;;
    esac
done

sh "$ROOT/scripts/prepare-env.sh"
APP_URL=$(read_env "$ROOT/.env" APP_URL)

validate_browsers "$BROWSERS" "$HEADED"
resolve_parallelism "$SESSIONS"
announce "$BROWSERS" "$HEADED" "$PARALLELISM"

sh "$ROOT/scripts/prepare-certs.sh"
sh "$ROOT/scripts/prepare-app.sh"

export BROWSERS HEADED PARALLELISM

# shellcheck disable=SC2086
docker compose up -d --build $STACK_SERVICES $(browser_services "$BROWSERS")

cat <<EOF

The stack is up.

  Run everything      docker compose exec runner mvn verify
  Run one class       docker compose exec runner mvn verify -Dit.test=LandingIT
  Run one tag         docker compose exec runner mvn verify -Dgroups=smoke

  Grid console        http://localhost:4444
  Watch a browser     http://localhost:7900 (chrome) 7901 (firefox) 7902 (edge), password: secret
  Mailbox             http://localhost:8025
  Application         https://localhost:8443  (accept the local certificate)

  Stop everything     docker compose down
EOF
