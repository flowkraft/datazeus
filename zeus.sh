#!/usr/bin/env sh
# ============================================================================
#  DataZeus — Master Everything Data, become a Data Zeus.
#
#  Usage:  ./zeus.sh <command> [args]
#    zeus koans [course] [series] [lesson]   walk the path (narrow with each token)
#    zeus update                             pull the latest courses & koans (keeps your edits)
#    zeus help                               this help
#
#  koans examples:
#    ./zeus.sh koans                       # every koan, every course
#    ./zeus.sh koans learnsql series1 _00  # ONE lesson   ← the usual path
#  Short aliases: sql=learnsql, S1=series1, plain "1 00". Courses: sql modeling
#  etl warehousing dbt viz bi. Needs a JDK 17+ and Maven; koans run on embedded DuckDB.
# ============================================================================
DIR="$(cd "$(dirname "$0")" && pwd)"

zeus_help() {
  cat <<'EOF'
DataZeus - Master Everything Data, become a Data Zeus.

  zeus koans [course] [series] [lesson]   walk the path
  zeus update                             pull the latest courses & koans (keeps your edits)
  zeus help                               this help

  e.g.  ./zeus.sh koans learnsql series1 _00
Short aliases: sql=learnsql, S1=series1. Courses: sql modeling etl warehousing dbt viz bi.
EOF
}

zeus_update() {
  echo "Updating DataZeus from github.com/flowkraft/datazeus ..."
  tmp="$(mktemp -d 2>/dev/null || echo "/tmp/datazeus-update-$$")"
  mkdir -p "$tmp"
  url="https://github.com/flowkraft/datazeus/archive/refs/heads/main.zip"
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$url" -o "$tmp/dz.zip" || { echo "Update failed - could not download (check your internet connection)."; rm -rf "$tmp"; exit 1; }
  elif command -v wget >/dev/null 2>&1; then
    wget -qO "$tmp/dz.zip" "$url" || { echo "Update failed - could not download."; rm -rf "$tmp"; exit 1; }
  else
    echo "Need 'curl' or 'wget' to update."; rm -rf "$tmp"; exit 1
  fi
  if command -v unzip >/dev/null 2>&1; then
    unzip -q "$tmp/dz.zip" -d "$tmp" || { echo "Could not unzip the download."; rm -rf "$tmp"; exit 1; }
  else
    echo "Need 'unzip' to update."; rm -rf "$tmp"; exit 1
  fi
  new="$tmp/datazeus-main"
  # Your editable koans live under tests/src/koans — preserve that whole tree:
  # refresh everything else, and only ADD brand-new koans (never overwrite your edits).
  koans_sub="tests/src/koans"
  if command -v rsync >/dev/null 2>&1; then
    rsync -a --exclude="$koans_sub/***" "$new/" "$DIR/"
    [ -d "$new/$koans_sub" ] && rsync -a --ignore-existing "$new/$koans_sub/" "$DIR/$koans_sub/"
  else
    # portable fallback (no rsync): one pass — refresh non-koans, add only new koans
    ( cd "$new" && find . -type f | while IFS= read -r f; do
        case "$f" in
          "./$koans_sub/"*) [ -f "$DIR/$f" ] || { mkdir -p "$DIR/$(dirname "$f")"; cp "$f" "$DIR/$f"; } ;;
          *)                mkdir -p "$DIR/$(dirname "$f")"; cp "$f" "$DIR/$f" ;;
        esac
      done )
  fi
  rm -rf "$tmp"
  echo
  echo "DataZeus is up to date. Your in-progress koans were left untouched."
}

zeus_koans() {
  if ! command -v mvn >/dev/null 2>&1; then
    echo "Maven (mvn) was not found on your PATH. Install a JDK 17+ and Maven, then run again."
    exit 1
  fi
  case "$1" in
    ""|all)      seg="" ;;
    sql)         seg="learnsql" ;;
    modeling)    seg="datamodeling" ;;
    etl)         seg="etlpipelines" ;;
    warehousing) seg="datawarehousing" ;;
    dbt)         seg="dbt" ;;
    viz)         seg="datavisualization" ;;
    bi)          seg="bi" ;;
    *)           seg="$1" ;;
  esac
  ser="$2"; ser="${ser#series}"; ser="${ser#S}"; ser="${ser#s}"
  epi="$3"; epi="${epi#ep}"; epi="${epi#EP}"; epi="${epi#_}"
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
  mkdir -p "$DIR/tests/target"; rm -f "$PROG"
  mvn -q -f "$DIR/tests/pom.xml" -Pkoans test -Dtest.includes="$inc" > "$LOG" 2>&1 || true
  if [ -f "$PROG" ]; then
    cat "$PROG"
  else
    echo "The koans did not run. This usually means a compile error in your edit"
    echo "(e.g. a typo where the ___ used to be). Maven said:"
    echo
    cat "$LOG"
  fi
}

case "$1" in
  update)         zeus_update ;;
  help|-h|--help) zeus_help ;;
  "")             zeus_help ;;
  koans)          shift; zeus_koans "$@" ;;
  *)              zeus_koans "$@" ;;   # bare form: treat as koans args
esac
