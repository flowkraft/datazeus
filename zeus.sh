#!/usr/bin/env sh
# ============================================================================
#  DataZeus — Master Everything Data, become a Data Zeus.
#
#  Usage:  ./zeus.sh <command> [args]
#    zeus koans [course] [series] [lesson]   walk the path (narrow with each token)
#    zeus practice [reset|run [star]]        your Data Modeling sandbox (a private Northwind copy)
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
  zeus practice [reset|run [star]]        your Data Modeling sandbox (a private Northwind copy)
  zeus test                               run the verify gate (the *Spec tests; needs Docker)
  zeus update                             pull the latest courses & koans (keeps your edits)
  zeus help                               this help

  e.g.  ./zeus.sh koans learnsql series1 _00
Short aliases: sql=learnsql, S1=series1. Courses: sql modeling etl warehousing dbt viz bi.
EOF
}

_zeus_sweep_orphans() {
  # DELETE COMPILED CLASSES WHOSE SOURCE IS GONE.
  #
  # gmavenplus writes into target/test-classes and never removes an output whose .groovy
  # disappeared, and surefire matches its `includes` against THAT class directory, not
  # against src. So a koan file we renamed upstream keeps running from its last build:
  # its koans keep counting toward the total, and the failure report names a file the
  # learner cannot open because it no longer exists. From their side that is precisely
  # what a `zeus update` rename looks like - which makes it read as their mistake.
  #
  # A sweep, not `mvn clean`: clean would also throw away every still-valid class and turn
  # the next run into a full recompile, and this runs on every single koan attempt.
  # $1 compiled tree, $2 source tree, $3 suffix (Koans|Spec).
  _classes="$1"; _sources="$2"; _suffix="$3"
  [ -d "$_classes" ] || return 0
  ( cd "$_classes" && find . -name "*${_suffix}*.class" -type f 2>/dev/null | sed 's#^\./##' ) \
  | while IFS= read -r _c; do
      # Groovy emits closures and Spock features as Outer$__spock_feature_0_closure1.class.
      # An inner class is orphaned by exactly the same source going away as its outer one,
      # so resolve EVERY class - inner or not - back to the outer name and ask whether that
      # source still exists. Matching only "*Koans.class" would strand the inner ones.
      _rel="${_c%.class}"; _rel="${_rel%%\$*}"
      [ -f "$_sources/$_rel.groovy" ] && continue
      rm -f "$_classes/$_c"
    done
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
  # NOTE: zeus.bat now compares as TEXT (fc without /b), so an editor that only rewrote line
  # endings is not mistaken for an edit. cmp has no text mode, so the POSIX-safe equivalent
  # needs a helper that strips CR into temp files first - not done yet, hence the divergence.
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

  # 3) per-workspace merge against the baseline
  for w in $workspaces; do
    [ -d "$new/$w" ] || continue
    ( cd "$new/$w" && find . -type f 2>/dev/null | sed 's#^\./##' | while IFS= read -r f; do
        src="$new/$w/$f"; loc="$DIR/$w/$f"; bas="$baseline/$w/$f"
        if [ ! -f "$loc" ]; then
          mkdir -p "$DIR/$w/$(dirname "$f")"; cp "$src" "$loc"          # new exercise
        elif [ -f "$bas" ] && cmp -s "$loc" "$bas"; then
          cp "$src" "$loc"                                              # untouched -> update
        elif [ -f "$bas" ] && cmp -s "$src" "$bas"; then
          :                                                             # yours differs, ours didn't change -> nothing to say
        elif cmp -s "$src" "$loc"; then
          # You already match the new version - which is also how a .new sidecar from an
          # earlier update ends once you have folded it in. Clear it so it is not litter.
          rm -f "$loc.new"
        else
          # THE ONE CASE WHERE "KEEP YOUR WORK" SILENTLY COSTS YOU A FIX: you edited this
          # lesson AND we changed it upstream (a corrected expected value, a reworded koan).
          # Preserving yours in silence means a lesson we KNOW is wrong stays wrong on your
          # disk forever, and the next failure reads as your SQL being wrong. Keep yours -
          # your answers are in it - but put ours beside it and say so. Same answer dpkg and
          # rpm reached for modified config files (.dpkg-dist / .rpmnew), for the same reason:
          # a 3-way merge nobody asked for is worse than two files and a clear sentence.
          cp "$src" "$loc.new"
          echo "  $w/$f"
          echo "      we corrected this lesson and you have edits in it - yours kept,"
          echo "      ours is beside it as $(basename "$f").new"
        fi
      done )
  done
  # 5) PRUNE what upstream dropped. Steps 2-4 only ever copy, so a file we RENAMED or MOVED
  #    upstream stays on disk forever. That is invisible until a `_todo` stub is promoted to a
  #    real lesson - the promoted file arrives, the stub remains beside it, and the convention
  #    that says "a _todo file is not a real lesson" is undermined by the stub sitting next to
  #    the real one.
  #    CONSERVATIVE BY DESIGN: only files WE shipped, that upstream no longer has, that the
  #    learner never edited. Anything edited is kept and named. An update must never silently
  #    destroy somebody's work.
  for w in $workspaces; do
    [ -d "$baseline/$w" ] || continue
    ( cd "$baseline/$w" && find . -type f 2>/dev/null | sed 's#^\./##' ) | while IFS= read -r f; do
      [ -f "$new/$w/$f" ] && continue                      # still shipped
      loc="$DIR/$w/$f"; bas="$baseline/$w/$f"
      [ -f "$loc" ] || continue
      if cmp -s "$loc" "$bas"; then rm -f "$loc"; else echo "  kept your edited $w/$f (no longer shipped)"; fi
    done
  done
  # OUTSIDE the workspaces, step 2 only ever COPIES, so anything we renamed or moved upstream
  # stayed on disk forever - the verify-gate spec of a renamed lesson, a dataset we replaced,
  # a tool we dropped. The old code pruned `courses/_todo-*` only, which fixed the one symptom
  # that had been noticed rather than the leak underneath it.
  #
  # A MANIFEST, not a second byte-for-byte baseline: outside the workspaces every file is
  # ours by definition (the learner edits koans, nothing else), so the only question is "did
  # we ship this last time and stop shipping it now?" - a question a list of paths answers
  # exactly, for a few KB instead of mirroring the datasets on every update.
  manifest="$baseline/.zeus-shipped"
  mkdir -p "$baseline"
  if [ -f "$manifest" ]; then
    while IFS= read -r f; do
      [ -z "$f" ] && continue
      _in_ws "$f" && continue                              # workspaces are handled above
      [ -f "$new/$f" ] && continue                         # still shipped
      case "$f" in zeus.sh|zeus.bat) continue ;; esac      # launchers are staged, never pruned
      rm -f "$DIR/$f"
    done < "$manifest"
  fi                                                       # no manifest yet -> nothing proven, prune nothing
  ( cd "$new" && find . -type f 2>/dev/null | sed 's#^\./##' ) > "$manifest.tmp" \
    && mv -f "$manifest.tmp" "$manifest"
  # BELT AND BRACES for `_todo` stubs, which the manifest alone cannot cover on its FIRST run:
  # an install that has never updated under the new code has no .zeus-shipped yet, so the prune
  # above stays silent exactly once - and a PROMOTED lesson is the case that gap shows up in
  # worst (07-data-types.mdx arriving to sit beside _todo-07-data-types.mdx). This check needs
  # no manifest to be safe: a stub is ours by construction, nobody hand-edits one, and it is
  # stale the moment the episode it stands in for is written. Cheap, and it never stops being
  # true, so it stays as a second line of defence rather than being retired after the cold start.
  find "$DIR/courses" -name "_todo-*" -type f 2>/dev/null | while IFS= read -r loc; do
    rel="${loc#$DIR/}"
    [ -f "$new/$rel" ] || rm -f "$loc"
  done
  # A renamed lesson leaves its whole folder behind once the files inside it are gone -
  # 05-meet-your-data-with-select/, its scripts/ and its cards/. Empty is not merely untidy
  # here: `zeus koans sql 1 05` resolves its scope by asking whether a DIRECTORY exists, so
  # an empty lesson folder is a scope that resolves and then runs nothing.
  for d in courses tests/src/verify datasets tools; do
    [ -d "$DIR/$d" ] && find "$DIR/$d" -type d -empty -delete 2>/dev/null
  done

  # 6) MIRROR the baseline - add what is new AND drop what upstream no longer ships. This has
  #    to come after step 5, which reads the OLD baseline to learn what went away; a baseline
  #    that only ever grew would keep re-proposing files that have been gone for releases.
  for w in $workspaces; do
    [ -d "$new/$w" ] || continue
    ( cd "$new/$w" && find . -type f 2>/dev/null | sed 's#^\./##' | while IFS= read -r f; do
        mkdir -p "$baseline/$w/$(dirname "$f")"; cp "$new/$w/$f" "$baseline/$w/$f"
      done )
    [ -d "$baseline/$w" ] && ( cd "$baseline/$w" && find . -type f 2>/dev/null | sed 's#^\./##' \
      | while IFS= read -r f; do [ -f "$new/$w/$f" ] || rm -f "$baseline/$w/$f"; done )
    find "$baseline/$w" -type d -empty -delete 2>/dev/null
  done

  # --------------------------------------------------------------------------
  rm -rf "$tmp"
  echo
  echo "DataZeus is up to date. Your in-progress edits were left untouched."
}

zeus_koans_python() {
  # PYTHON KOANS RUN IN DOCKER, AND THE LEARNER NEVER TYPES A DOCKER COMMAND.
  #
  # The JVM tracks can assume a JDK and fetch Maven with the bundled wrapper. Python has no
  # equivalent: assuming a working local Python means assuming their version, their PATH and
  # their venv habits, and on Windows that is the likeliest thing to make somebody quit before
  # koan one. Docker is already required by `zeus test`, CloudBeaver, PostgreSQL and Jupyter,
  # so this asks for nothing new.
  #
  # The image is built here rather than pulled: it pins pytest, pandas, polars and duckdb
  # exactly, so a koan's expected answer cannot shift under a library upgrade. `docker build`
  # is a no-op after the first run - Docker's layer cache makes it about a second.
  if ! command -v docker >/dev/null 2>&1 || ! docker info >/dev/null 2>&1; then
    echo
    echo "============================================================"
    echo " DOCKER IS NOT RUNNING (or not installed)."
    echo
    echo " The Python koans run inside a container so you do not have"
    echo " to install Python, pandas or pytest yourself. Docker is the"
    echo " only thing you need - and DataPallas already uses it for"
    echo " CloudBeaver, PostgreSQL and Jupyter."
    echo
    echo " Fix: start Docker, then run your command again."
    echo "============================================================"
    echo
    exit 1
  fi

  KO="$DIR/tests/src/koans/python"
  scope=""
  for tok in "$2" "$3"; do
    [ -z "$tok" ] && break
    _s="${tok#series}"; _s="${_s#S}"; _s="${_s#s}"
    _e="${tok#ep}"; _e="${_e#EP}"; _e="${_e#_}"
    _hit=""
    for _c in "$tok" "series$_s" "_$_e"; do
      [ -n "$_c" ] && [ -d "$KO$scope/$_c" ] && { _hit="$_c"; break; }
    done
    if [ -z "$_hit" ]; then
      # The Python twin of the miss report in zeus_koans() - same message, same running
      # order, same reasoning, and word-for-word in step with :nolesson_python in zeus.bat.
      # A learner who hits this on one track and then the other must not be told two
      # different stories.
      echo
      echo "=========================================================================="
      echo "  \"$tok\" IS NOT IN YOUR COPY OF DATAZEUS."
      echo
      echo "  MOST LIKELY YOUR DATAZEUS IS OUT OF DATE - this lesson was published"
      echo "  after you downloaded it. Fetch the latest - safe to run at any time:"
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
      echo "  Nothing was run - your koans are fine."
      echo
      printf "  What you DO have there:\n      "
      ls -1 "$KO$scope" 2>/dev/null | grep -v conftest | tr '\n' ' '; echo
      echo
      echo "  (Still not there after updating? Then \"$tok\" is a typo -"
      echo "   compare what you typed against the list above.)"
      echo "=========================================================================="
      echo
      return 1
    fi
    scope="$scope/$_hit"
  done

  echo "Building the Python koan runner (first run only, a few seconds)..."
  if ! docker build -q -t datazeus-python "$DIR/tests/python" >/dev/null 2>&1; then
    echo "Could not build the koan image. Is Docker running?"; exit 1
  fi

  # Git Bash hands out /c/... paths that the Docker daemon cannot resolve; cygpath -m fixes
  # that and is a no-op everywhere else.
  kmount="$KO"; dmount="$DIR/datasets"
  khost="$KO"
  if command -v cygpath >/dev/null 2>&1; then
    kmount="$(cygpath -m "$KO")"; dmount="$(cygpath -m "$DIR/datasets")"
    # -w, not -m: khost is never handed to Docker, it is SHOWN to the learner, so it wants
    # the notation they would type themselves - C:\DataPallas\... , not C:/DataPallas/... .
    khost="$(cygpath -w "$KO")"
  fi

  echo "Walking the path...  (scope: python$scope)"
  # MSYS_NO_PATHCONV: Git Bash rewrites any argument that looks like a Unix path, so the
  # CONTAINER path /koans/series1/_15 arrives as C:/Program Files/Git/koans/... and pytest
  # reports "file or directory not found". Harmless on macOS and Linux.
  # -p no:terminalreporter: pytest prints NOTHING and conftest.py writes the whole screen, so
  # walking the path looks identical on this track and the JVM one. A test runner's tracebacks
  # and "4 failed in 0.9s" are a report to an engineer; the koans are a lesson.
  # DATAZEUS_KOANS_HOST_DIR: every path pytest knows is a path INSIDE the container, which is
  # not where the learner's file lives and cannot be pasted into their editor. conftest.py uses
  # this to report the real location on their own machine. See the notes there.
  MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL="*" docker run --rm     -v "$kmount:/koans"     -v "$dmount:/datasets:ro"     -e "DATAZEUS_KOANS_HOST_DIR=$khost"     datazeus-python -p no:terminalreporter "/koans$scope"
  # An unsolved koan is the ASSIGNMENT, not a failure, so walking the path always reports
  # success - exactly as the JVM track does. pytest exits 1 while any blank is still blank,
  # which on lesson one is every learner: letting that escape makes VS Code's terminal put a
  # red cross beside the command and turns most modern prompts red, telling somebody who did
  # everything right that they broke something. Throughout zeus a non-zero code means "I
  # could not do what you asked" - no Docker, image will not build, lesson not in this copy -
  # and every one of those is already reported above, before we ever get here.
  return 0
}

zeus_koans() {
  # PYTHON IS NOT A JVM TRACK - hand it over before the java check below, so the same
  # `zeus koans <course> <series> <lesson>` command works identically for every course.
  case "$1" in
    python|py|learn-python) shift 0; zeus_koans_python "$@"; return $? ;;
  esac
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
  #
  # THE FIX LEADS AND THE TYPO HYPOTHESIS TRAILS, deliberately. Somebody who watched a
  # freshly published episode and pasted its command into an older download did NOT
  # mistype it, and that is overwhelmingly who lands here. Opening with "check your
  # spelling" sends exactly that person hunting a typo which is not there, so the update
  # they actually need is the first thing on screen and spelling is the fallback at the
  # bottom, where it belongs for the minority who really did fat-finger a token.
  if [ -n "$badval" ]; then
    echo
    echo "=========================================================================="
    echo "  \"$badval\" IS NOT IN YOUR COPY OF DATAZEUS."
    echo
    echo "  MOST LIKELY YOUR DATAZEUS IS OUT OF DATE - this lesson was published"
    echo "  after you downloaded it. Fetch the latest - safe to run at any time:"
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
    echo "  Nothing was compiled and nothing was run - your koans are fine."
    echo "  This is not an error in anything you typed into a koan."
    echo
    echo "  Looked in:  ${badparent#$DIR/}"
    echo
    printf "  What you DO have there:\n      "
    ls -1 "$badparent" 2>/dev/null | grep -v '^_internal$' | tr '\n' ' '; echo
    echo
    echo "  (Still not there after updating? Then \"$badval\" is a typo -"
    echo "   compare what you typed against the list above.)"
    echo "=========================================================================="
    echo
    return 1
  fi

  echo "Walking the path...  (scope: $inc)"
  echo "  first run compiles the koans and downloads dependencies — give it a moment."
  PROG="$DIR/tests/target/path-to-enlightenment.txt"
  LOG="$DIR/tests/target/koans-build.log"
  mkdir -p "$DIR/tests/target"; rm -f "$PROG"
  _zeus_sweep_orphans "$DIR/tests/target/test-classes" "$DIR/tests/src/koans/groovy" Koans
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

zeus_practice() {
  # THE DATA MODELING SANDBOX.
  #
  # Learn SQL only ever READS, so its koans can open a throwaway copy and nobody needs a
  # workspace. Data Modeling WRITES: the learner spends the whole of Series 1 growing a
  # schema.sql that rebuilds Northwind's integrity layer. That needs somewhere to put it.
  #
  # Why a COPY of the database and not the shipped one: northwind.duckdb is read by the
  # koans, by the verify gate, and by the e2e tests. A learner experimenting with CREATE and
  # DROP inside it would break all three, and the breakage would look like their fault.
  #
  # Why a `practice` SCHEMA inside that copy rather than a bare database: their tables sit
  # next to main.* so they can INSERT INTO practice.X SELECT * FROM main.X — which is the
  # whole grading mechanism. Real rows either fit their model or they do not.
  #
  # Why RESET drops and recreates: DuckDB cannot ALTER TABLE ADD FOREIGN KEY / UNIQUE / CHECK
  # (only ADD PRIMARY KEY and SET NOT NULL), so schema.sql has to be a REBUILD script with
  # constraints declared inline. Rebuilds are only pleasant if they are idempotent, so
  # DROP SCHEMA IF EXISTS ... CASCADE is baked into the template rather than left to memory.
  #
  # And the teardown is a lesson, not housekeeping: DROP SCHEMA practice CASCADE is the first
  # genuinely destructive statement in the course, in the one place where it is safe to run.
  WORK="$DIR/practice"
  SRC="$DIR/datasets/northwind/northwind.duckdb"
  DB="$WORK/northwind-practice.duckdb"
  case "$1" in
    reset|"")
      mkdir -p "$WORK"
      if [ ! -f "$SRC" ]; then echo "Northwind dataset not found at $SRC"; exit 1; fi
      cp "$SRC" "$DB" || { echo "Could not create your practice database."; exit 1; }
      # One working file per series, because each series leaves a different artifact behind:
      # schema.sql is the Series 1 Northwind rebuild, star.sql is the Series 3 star schema.
      # Series 2's library schema is the learner's own file - they name it, we do not.
      # Never overwritten once created; your work survives every reset and every update.
      TPLDIR="$DIR/courses/datamodeling/practice-template"
      for f in schema.sql star.sql; do
        if [ ! -f "$WORK/$f" ]; then
          cp "$TPLDIR/$f" "$WORK/$f" 2>/dev/null && echo "Created $WORK/$f - your working file."
        else
          echo "Kept your existing $WORK/$f."
        fi
      done
      echo "Fresh practice database: $DB"
      echo "The shipped dataset was not touched."
      ;;
    run)
      [ -f "$DB" ] || { echo "No practice database yet. Run:  ./zeus.sh practice reset"; exit 1; }
      # `practice run` defaults to schema.sql; `practice run star` runs star.sql.
      case "$2" in star) SQLFILE="star.sql" ;; *) SQLFILE="${2:-schema.sql}" ;; esac
      [ -f "$WORK/$SQLFILE" ] || { echo "No $SQLFILE in $WORK."; exit 1; }
      if command -v duckdb >/dev/null 2>&1; then
        # Git Bash hands out /c/... paths that a native duckdb.exe cannot resolve, and the
        # failure looks like a missing file rather than a path-format problem. Translate when
        # cygpath is there; everywhere else these two are already the paths we had.
        d="$DB"; f="$WORK/$SQLFILE"
        if command -v cygpath >/dev/null 2>&1; then
          d="$(cygpath -m "$DB")"; f="$(cygpath -m "$WORK/$SQLFILE")"
        fi
        duckdb "$d" -c ".read $f" && echo "$SQLFILE ran clean."
      else
        echo "The DuckDB CLI is not installed - which is fine, it is optional."
        echo "Open this file in CloudBeaver instead and run it there:"
        echo "    $DB"
        echo "(Or let the koans run it for you:  ./zeus.sh koans datamodeling)"
      fi
      ;;
    *) echo "Usage: ./zeus.sh practice [reset|run]" ;;
  esac
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
  # Same ghost, other source root: a renamed lesson leaves its old *Spec class behind and the
  # gate would keep verifying a lesson that no longer exists.
  _zeus_sweep_orphans "$DIR/tests/target/test-classes" "$DIR/tests/src/verify/groovy" Spec
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
  practice)       shift; zeus_practice "$@" ;;
  test)           zeus_test ;;
  *)              zeus_koans "$@" ;;   # bare form: treat as koans args
esac
