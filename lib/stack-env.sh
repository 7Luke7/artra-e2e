#!/bin/sh
# Shared shell helpers, sourced by run.sh / dev.sh / ci.sh.
#
# Two jobs: validate what the caller asked for, and decide how many browser
# sessions this machine can actually sustain.

SUPPORTED_BROWSERS="chrome firefox edge"

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
    _headed=$2

    for _b in $(echo "$_browsers" | tr ',' ' '); do
        case " $SUPPORTED_BROWSERS " in
            *" $_b "*) ;;
            *)
                echo "ERROR: unsupported browser '$_b' (supported: $SUPPORTED_BROWSERS)"
                exit 1
                ;;
        esac
    done

    # A browser listed in headed= but not in browsers= runs nothing at all,
    # which looks like a broken noVNC rather than a typo. Say so.
    for _h in $(echo "$_headed" | tr ',' ' '); do
        case ",$_browsers," in
            *",$_h,"*) ;;
            *) echo "WARN: headed=$_h but '$_h' is not in browsers=$_browsers - it will not run" ;;
        esac
    done
}

# ---------------------------------------------------------------------------
# Sizing.
#
# PARALLELISM is one number used twice: it is JUnit's thread-pool size and each
# grid node's max-sessions. Setting the node cap to the same value guarantees
# the grid can serve every thread even in the worst case, where all of them
# happen to want the same browser at the same moment.
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

# Sets the global PARALLELISM. Pass the user's sessions= value, empty to
# auto-size.
#
# A browser session costs roughly one core and 1.5 GB while a page is
# rendering. The cap of 6 is not about this machine: past that, the bottleneck
# stops being the browsers and becomes the single application container they
# all talk to, so more threads just add queueing and flakiness.
resolve_parallelism() {
    if [ -n "$1" ]; then
        PARALLELISM=$1
        return
    fi

    _cores=$(physical_cores)
    _ram=$(available_ram_gb)
    _by_ram=$(( _ram * 2 / 3 ))

    PARALLELISM=$_cores
    [ "$_by_ram" -lt "$PARALLELISM" ] && PARALLELISM=$_by_ram
    [ "$PARALLELISM" -gt 6 ] && PARALLELISM=6
    [ "$PARALLELISM" -lt 1 ] && PARALLELISM=1

    echo "Auto-sized to ${PARALLELISM} concurrent session(s) - ${_cores} physical core(s), ${_ram}GB available."
    echo "  Override with sessions=N."
}

# Everything the run needs, printed once so a CI log says what it actually ran.
announce() {
    echo ""
    echo " - Browsers:    $1"
    echo " - Headed:      ${2:-none (headless)}"
    echo " - Parallelism: $3"
    echo " - Application: ${APP_URL:-https://artra.test}"
    echo ""
}

# The services the suite needs. `docker compose up -d` on these pulls in
# postgres, redis, mailpit, the app and the seed job through depends_on.
STACK_SERVICES="runner caddy selenium-hub"

# Only the requested browsers are started - a full three-node grid on a laptop
# costs about 3 GB of RAM that a chrome-only run has no use for.
browser_services() {
    echo "$1" | tr ',' ' '
}
