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

# A previous `zeus update` staged a newer launcher. Apply it FIRST, before this file is
# read any further, then hand over to it. It cannot be applied during the update itself
# (see the note in zeus_update); swapping here is safe because exec replaces the process
# immediately, so nothing further is read from the old file.
[ -f "$DIR/zeus.bat.new" ] && mv -f "$DIR/zeus.bat.new" "$DIR/zeus.bat" 2>/dev/null
if [ -f "$DIR/zeus.sh.new" ]; then
  mv -f "$DIR/zeus.sh.new" "$DIR/zeus.sh" && chmod +x "$DIR/zeus.sh" 2>/dev/null
  echo "Applied the newer zeus.sh that 'zeus update' downloaded."
  exec "$DIR/zeus.sh" "$@"
fi

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
  # The launchers are STAGED, never overwritten in place: sh reads the script
  # incrementally and cp rewrites the SAME inode, so replacing zeus.sh while it is the
  # running script makes the shell resume at a byte offset inside different content and
  # abort silently - taking the lesson merge below down with it. Applied on the next run.
  ( cd "$new" && find . -type f 2>/dev/null | sed 's#^\./##' | while IFS= read -r f; do
      _in_ws "$f" && continue
      case "$f" in
        zeus.sh|zeus.bat) cmp -s "$new/$f" "$DIR/$f" || cp "$new/$f" "$DIR/$f.new"; continue ;;
      esac
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
  # Resolve each token against the REAL tree instead of assuming a fixed shape.
  # A course is course/series/lesson OR course/lesson, and nothing here needs to know
  # which: at every level we try the token as typed and with the prefixes we use
  # ("1" -> series1, "05" -> _05, "S1" -> series1), take whichever directory actually
  # exists, and build BOTH the Maven glob and the missing-scope check from the names
  # we resolved.
  #
  # That is what makes a 2-level course work with no special case, and - just as
  # importantly - what stops the error message from naming the WRONG level: with a
  # hardcoded course/series/lesson shape, `zeus koans dbt _01` on a 2-level course
  # would look for dbt/series01 and confidently report "SERIES _01 is missing".
  KDIR="$DIR/tests/src/koans/groovy/datazeus"
  _resolve() {                      # $1 = parent dir, $2 = token exactly as typed
    _t="$2"
    _s="${_t#series}"; _s="${_s#S}"; _s="${_s#s}"      # 1 | S1 | series1  -> series1
    _e="${_t#ep}"; _e="${_e#EP}"; _e="${_e#_}"         # 05 | _05 | ep05   -> _05
    for _c in "$_t" "series$_s" "_$_e"; do
      [ -n "$_c" ] && [ -d "$1/$_c" ] && { printf '%s' "$_c"; return 0; }
    done
    return 1
  }
  scope=""; cur="$KDIR"; badval=""; badparent=""
  for tok in "$seg" "$2" "$3"; do
    [ -z "$tok" ] && break                             # tokens run out -> broader scope
    if m="$(_resolve "$cur" "$tok")"; then
      scope="$scope/$m"; cur="$cur/$m"
    else
      badval="$tok"; badparent="$cur"; break
    fi
  done
  inc="**/*Koans.java"
  [ -n "$scope" ] && inc="**${scope}/**/*Koans.java"
  # --- Is this lesson actually in this copy? ---------------------------------
  # The common case: someone watches a newly published episode, pastes its command,
  # and that lesson simply is not in their download yet. Without this check Maven
  # matches nothing, path-to-enlightenment.txt is never written, and the branch
  # below reports "a compile error in your edit" - sending people to hunt a typo in
  # their own code instead of running `zeus update`.
  # The walk above already stopped at the FIRST token it could not resolve, so we
  # know exactly which word is wrong - and the directory it failed in gives us the
  # real alternatives for that same word. No level ever has to be named or guessed,
  # which is what keeps this correct for both 3-level and 2-level courses.
  if [ -n "$badval" ]; then
    echo
    echo "=========================================================================="
    echo "  \"$badval\" IS NOT IN YOUR COPY OF DATAZEUS."
    echo
    echo "  Looked in:  ${badparent#$DIR/}"
    echo
    printf "  What you DO have there:\n      "
    ls -1 "$badparent" 2>/dev/null | grep -v '^_internal$' | tr '\n' ' '; echo
    echo
    echo "  Nothing was compiled and nothing was run - your koans are fine."
    echo "  This is not an error in anything you typed into a koan."
    echo
    echo "  If \"$badval\" is misspelled, correct it against the list above."
    echo
    echo "  If it is spelled right, it was published after you downloaded"
    echo "  DataZeus. Fetch the latest - safe to run at any time:"
    echo
    echo "      ./zeus.sh update"
    echo
    echo "  Koans you have already solved are KEPT - update never overwrites"
    echo "  an exercise you have edited."
    echo
    echo "  Then run your command again:"
    echo
    echo "      ./zeus.sh koans $1 $2 $3"
    echo
    echo "  (Then run your command again.)"

    echo "=========================================================================="
    echo
    return 1
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
    # The lesson folder EXISTS (the guard above already proved that) and Maven still
    # produced no progress file - so this really is the learner's own code failing to
    # compile. Say so definitively; a missing lesson never reaches this branch.
    echo
    echo "=========================================================================="
    echo "  YOUR KOANS DID NOT COMPILE."
    echo
    echo "  The lesson is present - this is an error in the code you edited,"
    echo "  usually a typo where the ___ used to be (a missing quote, comma"
    echo "  or bracket). You do NOT need to update; fix the edit and re-run."
    echo
    echo "  Maven said:"
    echo "=========================================================================="
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
