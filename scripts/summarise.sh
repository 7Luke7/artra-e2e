#!/bin/sh
# Turns the failsafe XML into one readable line, plus a Markdown summary that
# CI pins to the top of the job page.
#
# The XML is the machine-readable record and GitHub renders it in the checks
# UI, but neither answers the first question after a red run - which browser
# broke - because the browser lives inside each test case's name. This pulls
# that out, so the job summary reads "signInCompletesWithTheEmailedCode
# [firefox]" rather than just a count.

set -e

RUN_DIR=${1:-runs/latest}
REPORTS="$RUN_DIR/reports"

[ -d "$REPORTS" ] || exit 0

# One awk pass over every report: totals from the <testsuite> attributes, and
# the class/name/browser of each case that carries a <failure> or <error>.
awk '
    function attr(line, key,   value) {
        if (match(line, key "=\"[^\"]*\"")) {
            value = substr(line, RSTART + length(key) + 2, RLENGTH - length(key) - 3)
            return value
        }
        return ""
    }
    /<testsuite / {
        tests    += attr($0, "tests")
        failures += attr($0, "failures")
        errors   += attr($0, "errors")
        skipped  += attr($0, "skipped")
    }
    /<testcase / {
        name = attr($0, "name")
        cls  = attr($0, "classname")
        sub(/.*\./, "", cls)
        pending = cls " . " name
        counted = 0
    }
    /<failure|<error/ {
        if (pending != "" && !counted) { print "FAIL\t" pending; counted = 1 }
    }
    END {
        printf "TOTALS\t%d\t%d\t%d\t%d\n", tests, failures, errors, skipped
    }
' "$REPORTS"/TEST-*.xml > "$RUN_DIR/.parsed"

TOTALS=$(grep '^TOTALS' "$RUN_DIR/.parsed" | head -1)
TOTAL=$(echo "$TOTALS" | cut -f2)
FAILURES=$(echo "$TOTALS" | cut -f3)
ERRORS=$(echo "$TOTALS" | cut -f4)
SKIPPED=$(echo "$TOTALS" | cut -f5)
PASSED=$(( TOTAL - FAILURES - ERRORS - SKIPPED ))

SUMMARY="$RUN_DIR/summary.md"
{
    echo "## End-to-end results"
    echo ""
    echo "| Total | Passed | Failed | Errors | Skipped |"
    echo "|------:|-------:|-------:|-------:|--------:|"
    echo "| $TOTAL | $PASSED | $FAILURES | $ERRORS | $SKIPPED |"
    echo ""

    if [ $(( FAILURES + ERRORS )) -gt 0 ]; then
        echo "### Failed"
        echo ""
        grep '^FAIL' "$RUN_DIR/.parsed" | cut -f2 | sed 's/^/- /'
        echo ""
        if [ -d "$RUN_DIR/diagnostics" ]; then
            echo "A screenshot, page dump and browser console log for each failure are in"
            echo '`diagnostics/`.'
        fi
    fi
} > "$SUMMARY"

rm -f "$RUN_DIR/.parsed"

echo ""
echo "  $PASSED/$TOTAL passed, $FAILURES failed, $ERRORS errored, $SKIPPED skipped"
echo "  Summary: $SUMMARY"

if [ -n "$GITHUB_STEP_SUMMARY" ]; then
    cat "$SUMMARY" >> "$GITHUB_STEP_SUMMARY"
fi
