#!/usr/bin/env sh
# ============================================================================
#  DataZeus — Master Everything Data, become a Data Zeus.
#
#  Usage:  ./zeus.sh <command> [args]
#    zeus koans [course] [series] [lesson]   walk the path (narrow with each token)
#    zeus test                               run the verify gate (the *Spec tests; needs Docker)
#    zeus update                             pull the latest courses & koans (keeps your edits)
#    zeus help                               this help
#
#  koans examples:
#    ./zeus.sh koans                       # every koan, every course
#    ./zeus.sh koans learnsql series1 _00  # ONE lesson   ← the usual path
#  Short aliases: sql=learnsql, S1=series1, plain "1 00". Courses: sql modeling
#  etl warehousing dbt viz bi. Needs a JDK 17+ (uses your Maven if present, else the bundled
#  wrapper fetches one); koans run on embedded DuckDB.
# ============================================================================
DIR="$(cd "$(dirname "$0")" && pwd)"

zeus_help() {
  cat <<'EOF'
DataZeus - Master Everything Data, become a Data Zeus.

  zeus koans [course] [series] [lesson]   walk the path
  zeus test                               run the verify gate (the *Spec tests; needs Docker)
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
  base="https://github.com/flowkraft/datazeus/archive/refs/heads/main"
  # Pick an archive format by whichever extractor exists: prefer unzip (.zip),
  # fall back to tar (.tar.gz) — tar is present on more minimal systems than unzip.
  # GitHub wraps both in the same datazeus-main/ folder, so the rest is identical.
  if command -v unzip >/dev/null 2>&1; then
    fmt="zip"; url="$base.zip"; arc="$tmp/dz.zip"
  elif command -v tar >/dev/null 2>&1; then
    fmt="tar"; url="$base.tar.gz"; arc="$tmp/dz.tar.gz"
  else
    echo "Need 'unzip' or 'tar' to update."; rm -rf "$tmp"; exit 1
  fi
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL "$url" -o "$arc" || { echo "Update failed - could not download (check your internet connection)."; rm -rf "$tmp"; exit 1; }
  elif command -v wget >/dev/null 2>&1; then
    wget -qO "$arc" "$url" || { echo "Update failed - could not download."; rm -rf "$tmp"; exit 1; }
  else
    echo "Need 'curl' or 'wget' to update."; rm -rf "$tmp"; exit 1
  fi
  if [ "$fmt" = "zip" ]; then
    unzip -q "$arc" -d "$tmp" || { echo "Could not extract the download."; rm -rf "$tmp"; exit 1; }
  else
    tar -xzf "$arc" -C "$tmp" || { echo "Could not extract the download."; rm -rf "$tmp"; exit 1; }
  fi
  new="$tmp/datazeus-main"
  # --- Generic, marker-driven merge -----------------------------------------
  # Editable workspaces declare themselves with a .zeus-keep marker (koans today,
  # katas tomorrow). Refresh everything else; inside each workspace: add new
  # exercises, update ones you never touched, preserve ones you edited.
  # "Never touched" = byte-identical (cmp) to the baseline snapshot from last update.
  baseline="$DIR/.internal-donttouch"

  # 1) discover workspaces (paths relative to the download) from the markers
  workspaces="$( cd "$new" && find . -name .zeus-keep -type f 2>/dev/null \
                 | sed -e 's#^\./##' -e 's#/\.zeus-keep$##' )"
  [ -z "$workspaces" ] && workspaces="tests/src/koans"   # fallback: legacy koans tree
  _in_ws() { for w in $workspaces; do case "$1" in "$w"/*|"$w") return 0 ;; esac; done; return 1; }

  # 2) refresh everything EXCEPT the workspaces
  ( cd "$new" && find . -type f 2>/dev/null | sed 's#^\./##' | while IFS= read -r f; do
      _in_ws "$f" && continue
      mkdir -p "$DIR/$(dirname "$f")"; cp "$new/$f" "$DIR/$f"
    done )

  # 3) per-workspace merge against the baseline, then 4) refresh the baseline
  for w in $workspaces; do
    [ -d "$new/$w" ] || continue
    ( cd "$new/$w" && find . -type f 2>/dev/null | sed 's#^\./##' | while IFS= read -r f; do
        src="$new/$w/$f"; loc="$DIR/$w/$f"; bas="$baseline/$w/$f"
        if [ ! -f "$loc" ]; then
          mkdir -p "$DIR/$w/$(dirname "$f")"; cp "$src" "$loc"          # new exercise
        elif [ -f "$bas" ] && cmp -s "$loc" "$bas"; then
          cp "$src" "$loc"                                              # untouched -> update
        fi                                                              # else edited -> preserve
      done )
    ( cd "$new/$w" && find . -type f 2>/dev/null | sed 's#^\./##' | while IFS= read -r f; do
        mkdir -p "$baseline/$w/$(dirname "$f")"; cp "$new/$w/$f" "$baseline/$w/$f"
      done )
  done
  # --------------------------------------------------------------------------
  rm -rf "$tmp"
  echo
  echo "DataZeus is up to date. Your in-progress edits were left untouched."
}

zeus_koans() {
  # Koans build with Maven: your installed Maven if you have one, otherwise the bundled
  # wrapper (./mvnw) fetches one automatically. Either way you only need a JDK 17+.
  if ! command -v java >/dev/null 2>&1 && [ -z "$JAVA_HOME" ]; then
    echo "Java was not found. Install a JDK 17+ and run again."
    echo "(Maven is used if you have it, otherwise fetched automatically - no Maven install needed.)"
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
  if command -v mvn >/dev/null 2>&1; then
    # Maven is installed - use it directly.
    mvn -q -f "$DIR/tests/pom.xml" -Pkoans test -Dtest.includes="$inc" > "$LOG" 2>&1 || true
  else
    # No Maven on PATH - bootstrap one via the bundled wrapper (downloads it once).
    # chmod guards the exec bit, which zip extraction can drop.
    ( cd "$DIR/tests" && chmod +x mvnw 2>/dev/null; ./mvnw -q -Pkoans test -Dtest.includes="$inc" ) > "$LOG" 2>&1 || true
  fi
  if [ -f "$PROG" ]; then
    cat "$PROG"
  else
    echo "The koans did not run. This usually means a compile error in your edit"
    echo "(e.g. a typo where the ___ used to be). Maven said:"
    echo
    cat "$LOG"
  fi
}

zeus_test() {
  # Run the VERIFY GATE (the *Spec tests), NOT the koans. Same Maven logic as zeus_koans
  # (your Maven if present, else the bundled wrapper). Needs a JDK 17+ AND Docker — the gate
  # starts a throwaway PostgreSQL to check every lesson on a real engine — unless PGHOST
  # points at a live Postgres.
  if ! command -v java >/dev/null 2>&1 && [ -z "$JAVA_HOME" ]; then
    echo "Java was not found. Install a JDK 17+ and run again."
    exit 1
  fi
  if [ -z "$PGHOST" ] && { ! command -v docker >/dev/null 2>&1 || ! docker info >/dev/null 2>&1; }; then
    echo
    echo "============================================================"
    echo " DOCKER IS NOT RUNNING (or not installed)."
    echo
    echo " The DataZeus tests need Docker: it starts a throwaway"
    echo " PostgreSQL to verify every lesson on a real engine."
    echo
    echo " Fix: start Docker, then run  ./zeus.sh test  again."
    echo " (Once up, you can target your Northwind Postgres from the Learn Data guide"
    echo "  instead of a throwaway:  PGHOST=localhost ./zeus.sh test)"
    echo "============================================================"
    echo
    exit 1
  fi
  if command -v mvn >/dev/null 2>&1; then
    mvn -f "$DIR/tests/pom.xml" test
  else
    ( cd "$DIR/tests" && chmod +x mvnw 2>/dev/null; ./mvnw test )
  fi
}

case "$1" in
  update)         zeus_update ;;
  help|-h|--help) zeus_help ;;
  "")             zeus_help ;;
  koans)          shift; zeus_koans "$@" ;;
  test)           zeus_test ;;
  *)              zeus_koans "$@" ;;   # bare form: treat as koans args
esac
