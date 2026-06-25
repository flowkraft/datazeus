#!/usr/bin/env sh
# ============================================================================
#  DataZeus SQL Koans — walk the path to enlightenment.
#
#  Usage:  ./koans.sh [course] [series] [lesson]   (each narrows the run)
#    ./koans.sh                            # every koan, every course
#    ./koans.sh mastersql                  # all Master SQL koans
#    ./koans.sh mastersql series1          # Master SQL · series1
#    ./koans.sh mastersql series1 _00      # ONE lesson   ← the usual path
#
#  The tokens match the folders AND the run's header ("Forging 'series1 _00 ...'").
#  Short aliases work too: sql=mastersql, S1=series1, and plain "1 00".
#  Courses:  sql · modeling · etl · warehousing · dbt · viz · bi. Needs a JDK 17+
#  and Maven; koans run on an embedded DuckDB — no Docker.
# ============================================================================
DIR="$(cd "$(dirname "$0")" && pwd)"

if ! command -v mvn >/dev/null 2>&1; then
  echo "Maven (mvn) was not found on your PATH. Install a JDK 17+ and Maven, then run again."
  exit 1
fi

case "$1" in
  ""|all)      seg="" ;;
  sql)         seg="mastersql" ;;
  modeling)    seg="datamodeling" ;;
  etl)         seg="etlpipelines" ;;
  warehousing) seg="datawarehousing" ;;
  dbt)         seg="dbt" ;;
  viz)         seg="datavisualization" ;;
  bi)          seg="bi" ;;
  *)           seg="$1" ;;   # treat anything else as a raw package segment
esac

# Normalize the series/lesson tokens so "S1 _00" (what the header prints),
# "series1 _00", or plain "1 00" all resolve to the same path.
ser="$2"; ser="${ser#series}"; ser="${ser#S}"; ser="${ser#s}"
epi="$3"; epi="${epi#ep}"; epi="${epi#EP}"; epi="${epi#_}"

# A PATH include that keeps the *Koans restriction (so the verified *Spec gate
# never runs here), narrowed to the chosen course / series / lesson.
inc="**/*Koans.java"
if [ -n "$seg" ]; then
  inc="**/${seg}"
  [ -n "$ser" ] && inc="${inc}/series${ser}"
  [ -n "$epi" ] && inc="${inc}/_${epi}"
  inc="${inc}/**/*Koans.java"
fi

echo "Walking the path...  (scope: $inc)"
echo "  first run compiles the koans and downloads dependencies — give it a moment."

PROG="$DIR/tests/target/path-to-enlightenment.txt"
LOG="$DIR/tests/target/koans-build.log"
mkdir -p "$DIR/tests/target"
rm -f "$PROG"

# Run quietly: Maven's noise goes to a log; the koans print a clean "path to
# enlightenment" screen to $PROG. Koans are RED until filled, so a non-zero exit
# is EXPECTED — we only fall back to the build log if the run never started.
mvn -q -f "$DIR/tests/pom.xml" -Pkoans test -Dtest.includes="$inc" > "$LOG" 2>&1 || true

if [ -f "$PROG" ]; then
  cat "$PROG"
else
  echo "The koans did not run. This usually means a compile error in your edit"
  echo "(e.g. a typo where the ___ used to be). Maven said:"
  echo
  cat "$LOG"
fi
