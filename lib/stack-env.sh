#!/bin/sh
# Shared shell helpers, sourced by run.sh and dev.sh.
#
# Three jobs: validate what the caller asked for, decide how many browser
# sessions this machine can sustain, and write the grid's effective
# configuration for the run.

SUPPORTED_BROWSERS="chrome firefox edge"

# Browser images the Dynamic Grid starts on demand. Pinned to exact browser and
# driver versions so a run is reproducible; keep in step with config.toml.
CHROME_IMAGE="selenium/standalone-chrome:147.0-chromedriver-147.0-grid-4.43.0-20260404"
FIREFOX_IMAGE="selenium/standalone-firefox:129.0-geckodriver-0.36-grid-4.43.0-20260404"
EDGE_IMAGE="selenium/standalone-edge:146.0-edgedriver-146.0-grid-4.43.0-20260404"
VIDEO_IMAGE="selenium/video:ffmpeg-8.1-20260505"

# Reads one key out of .env without sourcing it.
#
# Sourcing looked simpler and was wrong: a value like
# `EMAIL_FROM=Artra <no-reply@artra.test>` is a redirection to the shell, and
# the resulting syntax error kills a non-interactive shell outright - `|| true`
# does not catch it. The symptom was run.sh exiting silently with status 0
# before printing a single line.
read_env() {
    [ -f "$1" ] || return 0
    sed -n "s/^$2=//p" "$1" | tail -1 | sed 's/^"//; s/"$//'
}

validate_browsers() {
    _browsers=$1
    _record=$2

    for _b in $(echo "$_browsers" | tr ',' ' '); do
        case " $SUPPORTED_BROWSERS " in
            *" $_b "*) ;;
            *)
                echo "ERROR: unsupported browser '$_b' (supported: $SUPPORTED_BROWSERS)"
                exit 1
                ;;
        esac
    done

    # A browser listed in record= but not in browsers= records nothing, which
    # looks like broken recording rather than a typo. Say so.
    for _r in $(echo "$_record" | tr ',' ' '); do
        case ",$_browsers," in
            *",$_r,"*) ;;
            *) echo "WARN: record=$_r but '$_r' is not in browsers=$_browsers - it will not run" ;;
        esac
    done
}

# The grid pulls a browser image itself, through the Docker socket, the first
# time a session asks for one. Pulling several GB mid-run stalls the first
# tests and can time the session request out, so get them onto the machine
# before the run starts.
prepull_images() {
    echo "Pre-pulling browser images..."
    for _b in $(echo "$1" | tr ',' ' '); do
        case "$_b" in
            chrome)  docker pull -q "$CHROME_IMAGE" > /dev/null ;;
            firefox) docker pull -q "$FIREFOX_IMAGE" > /dev/null ;;
            edge)    docker pull -q "$EDGE_IMAGE" > /dev/null ;;
        esac
    done
    if [ -n "$2" ]; then
        docker pull -q "$VIDEO_IMAGE" > /dev/null
    fi
}

# ---------------------------------------------------------------------------
# Sizing.
#
# PARALLELISM is one number used twice: JUnit's thread-pool size, and the
# grid's max-sessions. Keeping them equal means the client can never ask for
# more browser containers than the grid will start at once.
# ---------------------------------------------------------------------------

# Physical cores, not nproc. nproc counts hyperthreads, and a browser rendering
# a page saturates a core rather than waiting on I/O, so a second hyperthread
# buys perhaps a quarter of a core - not a whole one.
physical_cores() {
    if [ -r /proc/cpuinfo ]; then
        _p=$(awk -F: '/^physical id/{p=$2} /^core id/{print p":"$2}' /proc/cpuinfo \
             | sort -u | grep -c . 2>/dev/null)
        if [ -n "$_p" ] && [ "$_p" -gt 0 ] 2>/dev/null; then
            echo "$_p"; return
        fi
    fi

    if command -v sysctl > /dev/null 2>&1; then          # macOS
        _p=$(sysctl -n hw.physicalcpu 2>/dev/null)
        if [ -n "$_p" ] && [ "$_p" -gt 0 ] 2>/dev/null; then
            echo "$_p"; return
        fi
    fi

    _l=$(nproc 2>/dev/null || echo 2)
    _p=$(( _l / 2 ))
    [ "$_p" -lt 1 ] && _p=1
    echo "$_p"
}

# MemAvailable rather than MemTotal: what is free right now is what the run can
# claim, and on a working laptop those differ by several gigabytes.
available_ram_gb() {
    if [ -r /proc/meminfo ]; then
        _kb=$(awk '/^MemAvailable:/{print $2; exit}' /proc/meminfo)
        [ -z "$_kb" ] && _kb=$(awk '/^MemTotal:/{print $2; exit}' /proc/meminfo)
        if [ -n "$_kb" ]; then echo $(( _kb / 1024 / 1024 )); return; fi
    fi

    if command -v sysctl > /dev/null 2>&1; then
        _b=$(sysctl -n hw.memsize 2>/dev/null)
        if [ -n "$_b" ]; then echo $(( _b / 1024 / 1024 / 1024 )); return; fi
    fi

    echo 4
}

# Sets the global PARALLELISM. Pass the user's sessions= value (empty to
# auto-size) and the record list.
#
#   headless   ~1 physical core, ~1.5 GB per session
#   recorded   ~2 physical cores, ~2 GB per session
#
# Recording costs twice over: the browser runs headed, painting into a real
# display instead of skipping layout, and an ffmpeg sidecar encodes 1920x1080
# continuously, which saturates a core on its own. If any browser is recorded
# the run is sized for the recorded case, because one global number cannot know
# which sessions happen to land together.
#
# The cap of 6 is not about this machine: past that the bottleneck stops being
# the browsers and becomes the single application container they all talk to,
# so more threads only add queueing and flakiness.
resolve_parallelism() {
    if [ -n "$1" ]; then
        PARALLELISM=$1
        return
    fi

    _cores=$(physical_cores)
    _ram=$(available_ram_gb)

    if [ -n "$2" ]; then
        _mode="recorded"
        _by_cpu=$(( _cores / 2 ))
        _by_ram=$(( _ram / 2 ))
    else
        _mode="headless"
        _by_cpu=$_cores
        _by_ram=$(( _ram * 2 / 3 ))
    fi

    PARALLELISM=$_by_cpu
    [ "$_by_ram" -lt "$PARALLELISM" ] && PARALLELISM=$_by_ram
    [ "$PARALLELISM" -gt 6 ] && PARALLELISM=6
    [ "$PARALLELISM" -lt 1 ] && PARALLELISM=1

    echo "Auto-sized to ${PARALLELISM} concurrent session(s) - ${_cores} physical core(s), ${_ram}GB available, ${_mode}."
    echo "  Override with sessions=N."
}

# ---------------------------------------------------------------------------
# The grid's effective configuration for this run.
# ---------------------------------------------------------------------------

# Writes config.generated.toml from the tracked config.toml, and prepares the
# directory the recorder writes into.
#
# Generated rather than edited in place: an in-place sed dirties config.toml on
# every run and gives everyone spurious diffs and merge conflicts.
generate_grid_config() {
    _browsers=$1
    _parallelism=$2

    mkdir -p videos

    # The recorder container writes as uid 1200 / gid 1201, so the host
    # directory has to be owned by that uid or recording silently produces no
    # files at all - an empty per-session directory and nothing else.
    #
    # Done through a throwaway container rather than host sudo: the Docker
    # daemon already runs as root, so this needs no password and behaves the
    # same on a laptop and on an unattended CI runner.
    if [ "$(id -u)" = "0" ]; then
        chown -R 1200:1201 videos
    elif ! docker run --rm -v "$(pwd)/videos:/videos" alpine:3 \
            chown -R 1200:1201 /videos > /dev/null 2>&1; then
        echo "WARN: could not set ownership on videos/ (uid 1200:1201)."
        echo "      Recording will produce no files. Fix it manually with:"
        echo "        sudo chown -R 1200:1201 videos"
    fi

    # Only the requested browsers are declared. The grid resolves every image in
    # [docker].configs at startup and pulls any it does not have, so leaving all
    # three in would make a chrome-only run fetch Firefox and Edge as well.
    _configs=""
    for _b in $(echo "$_browsers" | tr ',' ' '); do
        case "$_b" in
            chrome)  _img="$CHROME_IMAGE";  _name="chrome" ;;
            firefox) _img="$FIREFOX_IMAGE"; _name="firefox" ;;
            edge)    _img="$EDGE_IMAGE";    _name="MicrosoftEdge" ;;
            *) continue ;;
        esac
        _configs="${_configs}    \"${_img}\", '{\"browserName\": \"${_name}\"}',
"
    done
    # Drop the trailing comma so the TOML array stays valid.
    _configs=$(printf '%s' "$_configs" | sed '$ s/,$//')

    awk -v parallelism="$_parallelism" -v configs="$_configs" '
        /^[[:space:]]*configs[[:space:]]*=[[:space:]]*\[/ {
            print "configs = ["
            print configs
            print "]"
            skip = 1
            next
        }
        skip && /^[[:space:]]*\]/ { skip = 0; next }
        skip { next }
        /^[[:space:]]*max-sessions[[:space:]]*=/ {
            print "max-sessions = " parallelism
            next
        }
        { print }
    ' config.toml > config.generated.toml
}

# Moves videos/ into the run's archive directory.
#
# The recorder writes as uid 1200, so videos/ ends up owned by 1200:1201. POSIX
# requires WRITE access to a directory to move it to a different parent, because
# the directory's ".." entry has to be rewritten - owning the parent is not
# enough. So the invoking user cannot mv videos/ into runs/, and it fails with a
# bare "Permission denied" that reads like a bug in the script. (Renaming it
# within the same parent works fine, which is what makes it confusing to debug.)
#
# Hand ownership back first, through a throwaway container, since the daemon
# runs as root and the user may have no sudo.
archive_videos() {
    _target=$1
    [ -d videos ] || return 0
    # Nothing was recorded; do not leave an empty directory in the archive.
    [ -n "$(find videos -mindepth 1 -print -quit 2>/dev/null)" ] || return 0

    docker run --rm -v "$(pwd)/videos:/videos" alpine:3 \
        chown -R "$(id -u):$(id -g)" /videos > /dev/null 2>&1

    if mv videos "$_target" 2>/dev/null; then
        return 0
    fi
    if cp -r videos "$_target" 2>/dev/null; then
        echo "NOTE: copied videos/ (could not move it); the originals are still in ./videos"
        return 0
    fi
    echo "WARN: could not archive videos/ - the recordings are still in ./videos"
}

# Removes browser and recorder containers the grid left behind.
#
# The grid creates them through the Docker socket, so they are not part of the
# compose project and `docker compose down` does not touch them. It cleans up
# after itself in the normal case; this covers the one that matters - a run
# interrupted mid-session, which otherwise leaves a browser holding a gigabyte
# of RAM until someone notices.
reap_grid_containers() {
    # Queried in two passes rather than with two --filter name= arguments,
    # whose OR/AND semantics have differed between docker versions.
    _stray=$(printf '%s\n%s\n' \
        "$(docker ps -aq --filter 'name=^/browser-' 2>/dev/null)" \
        "$(docker ps -aq --filter 'name=^/recorder-' 2>/dev/null)" | grep -v '^$' || true)
    if [ -n "$_stray" ]; then
        echo "Removing $(echo "$_stray" | grep -c .) leftover grid container(s)..."
        echo "$_stray" | xargs -r docker rm -f > /dev/null 2>&1 || true
    fi
}

# Everything the run needs, printed once so a CI log says what it actually ran.
announce() {
    echo ""
    echo " - Browsers:    $1"
    echo " - Recording:   ${2:-none (headless)}"
    echo " - Parallelism: $3"
    echo " - Application: ${APP_URL:-http://172.19.0.9:3000}"
    echo ""
}

# The services the run needs. `docker compose up -d` on these pulls in postgres,
# redis, mailpit, the app and the seed job through depends_on. The browsers are
# NOT listed: the grid starts those itself, one container per session.
STACK_SERVICES="runner standalone-docker"
