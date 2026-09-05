#!/bin/sh
# Run the suite: bring the whole stack up, execute the tests, archive the
# results, tear down.
#
# This is the entry point CI uses too, so a green run here means a green
# pipeline.
#
#   ./run.sh                                   chrome, headless
#   ./run.sh browsers=chrome,firefox,edge      the full matrix
#   ./run.sh browsers=chrome record=chrome     record the sessions to video
#   ./run.sh tags=smoke                        only @Tag("smoke")
#   ./run.sh test=CourseCatalogueIT            one class
#   ./run.sh sessions=4                        override the auto-sized parallelism
#   ./run.sh keep=true                         leave the stack running afterwards
#
# Requires Docker and the compose plugin. Nothing else - no JDK, no Maven and no
# browsers on the host.

set -e

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
cd "$ROOT"

. "$ROOT/lib/stack-env.sh"

BROWSERS="chrome"
RECORD=""
SESSIONS=""
TAGS=""
TEST=""
KEEP="false"

for arg in "$@"; do
    case "$arg" in
        browsers=*) BROWSERS="${arg#*=}" ;;
        record=*)   RECORD="${arg#*=}" ;;
        sessions=*) SESSIONS="${arg#*=}" ;;
        tags=*)     TAGS="${arg#*=}" ;;
        test=*)     TEST="${arg#*=}" ;;
        keep=*)     KEEP="${arg#*=}" ;;
        *)
            echo "Unsupported argument: $arg"
            echo "Usage: ./run.sh [browsers=...] [record=...] [sessions=N] [tags=...] [test=...] [keep=true]"
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

echo "Starting the stack (this builds the application image on first run)..."
docker compose down --remove-orphans > /dev/null 2>&1 || true

# shellcheck disable=SC2086
if ! docker compose up -d --build $STACK_SERVICES; then
    echo ""
    echo "ERROR: the stack did not come up. Recent logs:"
    docker compose logs --tail 60
    docker compose down --remove-orphans
    exit 1
fi

# Maven's own arguments, assembled from the shorthands above so nobody has to
# remember -Dgroups vs -Dit.test.
MVN_ARGS="-B clean verify"
[ -n "$TAGS" ] && MVN_ARGS="$MVN_ARGS -Dgroups=$TAGS"
[ -n "$TEST" ] && MVN_ARGS="$MVN_ARGS -Dit.test=$TEST"

echo ""
echo "Running the suite..."
echo ""

set +e
# shellcheck disable=SC2086
docker compose exec -T runner mvn $MVN_ARGS
EXIT_CODE=$?
set -e

RUN_DIR="runs/$(date +%Y-%m-%d_%H-%M-%S)"
mkdir -p "$RUN_DIR"

# Copied out of the container rather than bind-mounted: Maven runs as root in
# there, and a bind mount would leave root-owned build output in the working
# tree. `|| true` on each because a run that failed before a phase produced
# nothing for it, and that is not itself a failure worth aborting the archive.
copy_out() {
    docker compose cp "runner:/artra-e2e/target/$1" "$RUN_DIR/$2" > /dev/null 2>&1 || true
}
copy_out failsafe-reports reports
copy_out surefire-reports unit-reports
copy_out diagnostics diagnostics
copy_out logs logs

sh "$ROOT/scripts/summarise.sh" "$RUN_DIR" || true

if [ "$KEEP" != "true" ]; then
    # Tear down before moving the recordings: the recorder flushes and closes
    # each .mp4 when its container stops, so archiving first captures truncated
    # files - or none at all for a session that was still running.
    docker compose down --remove-orphans
    # The grid creates browser and recorder containers itself, so compose does
    # not know about them and `down` leaves any that outlived a killed run.
    reap_grid_containers
    archive_videos "$RUN_DIR/videos"
else
    echo ""
    echo "Stack left running. Re-run the suite without restarting it:"
    echo "  docker compose exec runner mvn verify"
    echo "  Grid console:  http://localhost:4444"
    echo "  Mailbox:       http://localhost:8025"
    echo "  Application:   ${APP_URL:-http://172.19.0.9:3000}  (from inside the stack)"
fi

echo ""
echo "Results archived in $RUN_DIR (maven exit code: $EXIT_CODE)"
exit $EXIT_CODE
