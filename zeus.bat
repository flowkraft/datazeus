@echo off
REM ===========================================================================
REM  DataZeus - Master Everything Data, become a Data Zeus.
REM
REM  Usage:  zeus <command> [args]
REM    zeus koans [course] [series] [lesson]   walk the path (narrow with each token)
REM    zeus test                               run the verify gate (the *Spec tests; needs Docker)
REM    zeus update                             pull the latest courses ^& koans (keeps your edits)
REM    zeus help                               this help
REM
REM  koans examples:
REM    zeus koans                       every koan, every course
REM    zeus koans learnsql              all Master SQL koans
REM    zeus koans learnsql series1 _00  ONE lesson   <- the usual path
REM  Short aliases: sql=learnsql, S1=series1, plain "1 00". Courses: sql modeling
REM  etl warehousing dbt viz bi. Needs a JDK 17+ (uses your Maven if present, else the bundled
REM  wrapper fetches one); koans run on embedded DuckDB.
REM ===========================================================================
setlocal enabledelayedexpansion
set "DIR=%~dp0"
set "ROOT=%DIR:~0,-1%"

REM A previous `zeus update` staged a newer launcher. Apply it FIRST, before this file
REM is read any further, then hand over to it. It cannot be applied during the update
REM itself: cmd.exe reads a .bat incrementally by byte offset, so replacing the running
REM script mid-run resumes it at that offset inside a different file and it dies
REM silently. Swapping here is safe because the swap is immediately followed by a
REM handover - nothing further is read from the old file.
if exist "%DIR%zeus.sh.new" move /y "%DIR%zeus.sh.new" "%DIR%zeus.sh" >nul 2>nul
if exist "%DIR%zeus.bat.new" (
  move /y "%DIR%zeus.bat.new" "%DIR%zeus.bat" >nul 2>nul
  echo Applied the newer zeus.bat that `zeus update` downloaded.
  endlocal & call "%~f0" %* & exit /b !errorlevel!
)

set "cmd=%~1"
if /I "%cmd%"=="update"  goto :update
if /I "%cmd%"=="help"    goto :help
if /I "%cmd%"=="-h"      goto :help
if /I "%cmd%"=="--help"  goto :help
if /I "%cmd%"=="/?"      goto :help
if /I "%cmd%"=="test"    goto :test
if "%cmd%"==""           goto :help

REM "zeus koans <a> <b> <c>" OR the bare "zeus <course> <series> <lesson>"
if /I "%cmd%"=="koans" (
  set "C=%~2" & set "S=%~3" & set "E=%~4"
) else (
  set "C=%~1" & set "S=%~2" & set "E=%~3"
)
goto :koans

:help
echo DataZeus - Master Everything Data, become a Data Zeus.
echo.
echo   zeus koans [course] [series] [lesson]   walk the path
echo   zeus test                               run the verify gate ^(the *Spec tests; needs Docker^)
echo   zeus update                             pull the latest courses ^& koans ^(keeps your edits^)
echo   zeus help                               this help
echo.
echo   e.g.  zeus koans learnsql series1 _00
echo Short aliases: sql=learnsql, S1=series1. Courses: sql modeling etl warehousing dbt viz bi.
endlocal & exit /b 0

:koans
REM Koans build with Maven: your installed Maven if you have one, otherwise the bundled
REM wrapper (tests\mvnw.cmd) fetches one automatically. Either way you only need a JDK 17+.
set "HASJAVA="
where java >nul 2>nul && set "HASJAVA=1"
if not defined HASJAVA if defined JAVA_HOME set "HASJAVA=1"
if not defined HASJAVA (
  echo Java was not found. Install a JDK 17+ and run again.
  echo ^(Maven is downloaded automatically by the wrapper - no Maven install needed.^)
  endlocal & exit /b 1
)

set "seg="
if /I "%C%"=="sql"          set "seg=learnsql"
if /I "%C%"=="modeling"     set "seg=datamodeling"
if /I "%C%"=="etl"          set "seg=etlpipelines"
if /I "%C%"=="warehousing"  set "seg=datawarehousing"
if /I "%C%"=="dbt"          set "seg=dbt"
if /I "%C%"=="viz"          set "seg=datavisualization"
if /I "%C%"=="bi"           set "seg=bi"
if not defined seg if not "%C%"=="" if /I not "%C%"=="all" set "seg=%C%"

REM --- Resolve the scope against the REAL tree, one token at a time -----------
REM A course is course/series/lesson OR course/lesson, and nothing here needs to know
REM which: at every level we try the token as typed and with the prefixes we use
REM ("1" -> series1, "05" -> _05, "S1" -> series1), take whichever directory actually
REM exists, and build BOTH the Maven glob and the missing-scope check from the names
REM we resolved.
REM
REM That is what makes a 2-level course work with no special case, and - just as
REM importantly - what stops the error message from naming the WRONG level: with a
REM hardcoded course/series/lesson shape, `zeus koans dbt _01` on a 2-level course
REM would look for dbt\series01 and confidently report "SERIES _01 is missing".
REM
REM Stopping at the FIRST token that will not resolve is also what lets the message
REM be specific: we know exactly which word is wrong, and the directory it failed in
REM holds the real alternatives for that same word.
set "KDIR=%DIR%tests\src\koans\groovy\datazeus"
set "scope=" & set "cur=!KDIR!" & set "badval=" & set "badparent="

if not defined seg goto :scoperesolved
call :resolve "!cur!" "!seg!"
if not defined RES set "badval=!seg!" & set "badparent=!cur!" & goto :nolesson
set "scope=!scope!/!RES!" & set "cur=!cur!\!RES!"

if not defined S goto :scoperesolved
call :resolve "!cur!" "%S%"
if not defined RES set "badval=%S%" & set "badparent=!cur!" & goto :nolesson
set "scope=!scope!/!RES!" & set "cur=!cur!\!RES!"

if not defined E goto :scoperesolved
call :resolve "!cur!" "%E%"
if not defined RES set "badval=%E%" & set "badparent=!cur!" & goto :nolesson
set "scope=!scope!/!RES!" & set "cur=!cur!\!RES!"

:scoperesolved
set "inc=**/*Koans.java"
if defined scope set "inc=**!scope!/**/*Koans.java"

echo Walking the path...  ^(scope: !inc!^)
echo   first run compiles the koans and downloads dependencies - give it a moment.

set "PROG=%DIR%tests\target\path-to-enlightenment.txt"
set "LOG=%DIR%tests\target\koans-build.log"
if not exist "%DIR%tests\target" mkdir "%DIR%tests\target"
if exist "%PROG%" del "%PROG%" >nul 2>nul

where mvn >nul 2>nul
if not errorlevel 1 (
  REM Maven is installed - use it directly.
  call mvn -q -f "%DIR%tests\pom.xml" -Pkoans test -Dtest.includes="!inc!" > "%LOG%" 2>&1
) else (
  REM No Maven on PATH - bootstrap one via the bundled wrapper (downloads it once).
  pushd "%DIR%tests"
  call .\mvnw.cmd -q -Pkoans test -Dtest.includes="!inc!" > "%LOG%" 2>&1
  popd
)

if exist "%PROG%" (
  type "%PROG%"
) else (
  REM The lesson folder EXISTS (the guard above already proved that) and Maven still
  REM produced no progress file — so this really is the learner's own code failing to
  REM compile. Say so definitively; a missing lesson never reaches this branch.
  echo.
  echo ==========================================================================
  echo   YOUR KOANS DID NOT COMPILE.
  echo.
  REM NOTE: parentheses MUST be escaped as ^( ^) here - this echo lives inside an
  REM `else ( ... )` block, so a bare ) closes the block early and batch then tries
  REM to execute the rest of the message as commands.
  echo   The lesson is present - this is an error in the code you edited,
  echo   usually a typo where the ___ used to be ^(a missing quote, comma
  echo   or bracket^). You do NOT need to update; fix the edit and re-run.
  echo.
  echo   Maven said:
  echo ==========================================================================
  echo.
  type "%LOG%"
)
endlocal & exit /b 0

:nolesson
REM Reached ONLY when the lesson folder does not exist. Nothing has been compiled
REM or run at this point, so this must never be confused with a compile error in
REM the learner's own edit - that is reported separately, after Maven has actually
REM tried to build (see the end of :koans).
REM The resolver stopped at the FIRST token it could not match, so !badval! is the
REM exact word that is wrong and !badparent! is the directory that holds the real
REM alternatives for that same word. No level ever has to be named or guessed, which
REM is what keeps this correct for 3-level AND 2-level courses alike.
set "LIST="
for /d %%D in ("!badparent!\*") do if /I not "%%~nxD"=="_internal" set "LIST=!LIST! %%~nxD"
set "REL=!badparent:%DIR%=!"
echo.
echo ==========================================================================
echo   "!badval!" IS NOT IN YOUR COPY OF DATAZEUS.
echo.
echo   Looked in:  !REL!
echo.
echo   What you DO have there:
echo      !LIST!
echo.
echo   Nothing was compiled and nothing was run - your koans are fine.
echo   This is not an error in anything you typed into a koan.
echo.
echo   If "!badval!" is misspelled, correct it against the list above.
echo.
echo   If it is spelled right, it was published after you downloaded
echo   DataZeus. Fetch the latest - safe to run at any time:
echo.
echo       .\zeus.bat update
echo.
echo   Koans you have already solved are KEPT - update never overwrites
echo   an exercise you have edited.
echo.
echo   Then run your command again:
echo.
echo       .\zeus.bat koans %C% %S% %E%
echo.
echo   ^(Still the same after updating? Then it is a spelling mistake -
echo    compare what you typed against the list above.^)
echo ==========================================================================
echo.
endlocal & exit /b 1

:test
REM Run the VERIFY GATE (the *Spec tests), NOT the koans. Same Maven logic as `zeus koans`
REM (your Maven if present, else the bundled wrapper). Needs a JDK 17+ AND Docker - the gate
REM starts a throwaway PostgreSQL to check every lesson on a real engine - unless PGHOST
REM points at a live Postgres.
set "HASJAVA="
where java >nul 2>nul && set "HASJAVA=1"
if not defined HASJAVA if defined JAVA_HOME set "HASJAVA=1"
if not defined HASJAVA (
  echo Java was not found. Install a JDK 17+ and run again.
  echo ^(Maven is downloaded automatically by the wrapper - no Maven install needed.^)
  endlocal & exit /b 1
)
if defined PGHOST goto :test_run
docker info >nul 2>nul
if not errorlevel 1 goto :test_run
echo.
echo ============================================================
echo  DOCKER IS NOT RUNNING ^(or not installed^).
echo.
echo  The DataZeus tests need Docker: it starts a throwaway
echo  PostgreSQL to verify every lesson on a real engine.
echo.
echo  Fix: start Docker Desktop, then run  zeus test  again.
echo  ^(Once up, you can target your Northwind Postgres from the Learn Data guide
echo   instead of a throwaway:  set PGHOST=localhost ^&^& zeus test^)
echo ============================================================
echo.
endlocal & exit /b 1
:test_run
where mvn >nul 2>nul
if not errorlevel 1 goto :test_mvn
pushd "%DIR%tests"
call .\mvnw.cmd test
set "RC=!errorlevel!"
popd
endlocal & exit /b !RC!
:test_mvn
call mvn -f "%DIR%tests\pom.xml" test
endlocal & exit /b !errorlevel!

:update
echo Updating DataZeus from github.com/flowkraft/datazeus ...
set "TMP_DZ=%TEMP%\datazeus-update-%RANDOM%%RANDOM%"
set "URL=https://github.com/flowkraft/datazeus/archive/refs/heads/main.zip"
set "ZIP=%TMP_DZ%\dz.zip"
set "NEW=%TMP_DZ%\datazeus-main"
mkdir "%TMP_DZ%" 2>nul

REM Prefer native curl + tar (built into Windows 10 1803+; Windows' tar is bsdtar, which
REM extracts .zip directly). Fall back to PowerShell Invoke-WebRequest + Expand-Archive on
REM older Windows. Success = "the extracted folder exists", so it doesn't matter which ran.
REM Call Windows' OWN tar by full path, never whatever `tar` PATH happens to resolve.
REM Windows ships bsdtar (1803+), which extracts .zip. Git for Windows ships GNU tar
REM and puts it EARLIER on PATH - and GNU tar cannot read a zip at all ("this does not
REM look like a tar archive"). Since a huge share of our learners have Git installed,
REM `where tar` was effectively a coin-flip deciding whether update worked, which is
REM exactly what made this fail intermittently.
set "SYSTAR=%SystemRoot%\System32\tar.exe"
if exist "%SYSTAR%" where curl >nul 2>nul && (
  curl -fsSL -o "%ZIP%" "%URL%" 2>nul && "%SYSTAR%" -xf "%ZIP%" -C "%TMP_DZ%" 2>nul
)
if not exist "%NEW%" (
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; try { Invoke-WebRequest -UseBasicParsing -Uri '%URL%' -OutFile '%ZIP%'; Expand-Archive -Path '%ZIP%' -DestinationPath '%TMP_DZ%' -Force } catch { exit 1 }" 2>nul
)
if not exist "%NEW%" (
  echo Update failed - could not download or extract.
  echo ^(Check your internet connection, or that 'curl'/'tar' or PowerShell 5+ is available.^)
  rmdir /s /q "%TMP_DZ%" 2>nul
  endlocal & exit /b 1
)
REM --- Generic, marker-driven merge ------------------------------------------
REM Editable workspaces declare themselves with a .zeus-keep marker (koans today,
REM katas tomorrow). Refresh everything else; inside each workspace: add new
REM exercises, update ones you never touched, preserve ones you edited.
REM "Never touched" = identical (fc) to the baseline snapshot from the last update.
set "BASE=%ROOT%\.internal-donttouch"
set "WSLIST=%TMP_DZ%\workspaces.txt"
type nul>"%WSLIST%"

REM 1) discover workspaces (paths relative to NEW) from .zeus-keep markers
for /f "delims=" %%M in ('dir /s /b /a:-d "%NEW%\.zeus-keep" 2^>nul') do (
  set "wd=%%~dpM"
  set "wd=!wd:%NEW%\=!"
  if defined wd set "wd=!wd:~0,-1!"
  >>"%WSLIST%" echo(!wd!
)
for %%A in ("%WSLIST%") do if %%~zA EQU 0 >"%WSLIST%" echo tests\src\koans

REM 2) refresh everything EXCEPT the workspaces (+ the local baseline cache)
REM
REM /XF the launchers: cmd.exe reads a .bat INCREMENTALLY, keeping a byte offset into
REM the file. Overwriting zeus.bat while zeus.bat is the script currently running makes
REM cmd resume at that offset inside the NEW, different-length file - landing mid-line
REM or past the end - so the update dies silently (exit 1, no message) BEFORE the merge
REM below ever copies the new lessons. That is not a flake: it fails every single time
REM the shipped launcher differs from the local one, which is exactly the update that
REM matters. zeus.sh is excluded for the same reason (cp rewrites the same inode).
REM The refreshed launchers are staged as *.new and applied on the next run, at the top
REM of this file, before anything else is read.
set "XD=/XD "%BASE%""
for /f "usebackq delims=" %%W in ("%WSLIST%") do set "XD=!XD! /XD "%NEW%\%%W""
robocopy "%NEW%" "%ROOT%" /E !XD! /XF "zeus.bat" /XF "zeus.sh" /NFL /NDL /NJH /NJS /NC /NS /NP >nul
for %%L in (zeus.bat zeus.sh) do if exist "%NEW%\%%L" (
  fc /b "%NEW%\%%L" "%ROOT%\%%L" >nul 2>nul
  if errorlevel 1 copy /y "%NEW%\%%L" "%ROOT%\%%L.new" >nul 2>nul
)

REM 3) per-workspace merge against the baseline; 4) then refresh the baseline
for /f "usebackq delims=" %%W in ("%WSLIST%") do if exist "%NEW%\%%W" (
  for /f "delims=" %%F in ('dir /s /b /a:-d "%NEW%\%%W\*" 2^>nul') do (
    set "rel=%%F"
    set "rel=!rel:%NEW%\=!"
    set "loc=%ROOT%\!rel!"
    set "bas=%BASE%\!rel!"
    if not exist "!loc!" (
      call :dzcopy "%%F" "!loc!"
    ) else if exist "!bas!" (
      fc /b "!loc!" "!bas!" >nul 2>nul
      if not errorlevel 1 call :dzcopy "%%F" "!loc!"
    )
  )
  robocopy "%NEW%\%%W" "%BASE%\%%W" /E /NFL /NDL /NJH /NJS /NC /NS /NP >nul
)
rmdir /s /q "%TMP_DZ%" 2>nul
echo.
echo DataZeus is up to date. Your in-progress edits were left untouched.
endlocal & exit /b 0

:dzcopy
for %%P in ("%~2") do if not exist "%%~dpP" mkdir "%%~dpP" >nul 2>nul
copy /y "%~1" "%~2" >nul 2>nul
exit /b 0

:resolve
REM  %1 = parent directory, %2 = one token exactly as the user typed it.
REM  Sets RES to the directory name that matched, or leaves it empty.
REM  Tries the token as-is first, so a token typed in full always wins; the
REM  prefixed forms are the convenience layer that lets "1" mean series1 and
REM  "05" mean _05. Nothing here assumes how DEEP the course is.
set "RES="
set "P=%~1"
set "T=%~2"
REM Strip only a LEADING prefix, via substring - NOT `set "x=!T:series=!"`, which is
REM batch string REPLACEMENT: global and case-insensitive. That removed the letters
REM anywhere in the token, so "s1s" collapsed to "1" and the fallback candidate became
REM "series1" - a real directory. Batch would then silently accept a typo that zeus.sh
REM correctly rejects. Same command, two platforms, two answers.
set "TS=!T!"
if /I "!TS:~0,6!"=="series" set "TS=!TS:~6!"
if /I "!TS:~0,1!"=="s"      set "TS=!TS:~1!"
set "TE=!T!"
if /I "!TE:~0,2!"=="ep" set "TE=!TE:~2!"
if "!TE:~0,1!"=="_"     set "TE=!TE:~1!"
if exist "!P!\!T!\"        set "RES=!T!" & goto :eof
if exist "!P!\series!TS!\" set "RES=series!TS!" & goto :eof
if exist "!P!\_!TE!\"      set "RES=_!TE!" & goto :eof
goto :eof
