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

set "ser=%S%"
if defined ser (
  set "ser=!ser:series=!"
  set "ser=!ser:S=!"
)
set "epi=%E%"
if defined epi (
  set "epi=!epi:EP=!"
  set "epi=!epi:_=!"
)

set "inc=**/*Koans.java"
if defined seg (
  set "inc=**/!seg!"
  if defined ser set "inc=!inc!/series!ser!"
  if defined epi set "inc=!inc!/_!epi!"
  set "inc=!inc!/**/*Koans.java"
)

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
  echo The koans did not run. This usually means a compile error in your edit
  echo ^(e.g. a typo where the ___ used to be^). Maven said:
  echo.
  type "%LOG%"
)
endlocal & exit /b 0

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
where curl >nul 2>nul && where tar >nul 2>nul && (
  curl -fsSL -o "%ZIP%" "%URL%" 2>nul && tar -xf "%ZIP%" -C "%TMP_DZ%" 2>nul
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
set "XD=/XD "%BASE%""
for /f "usebackq delims=" %%W in ("%WSLIST%") do set "XD=!XD! /XD "%NEW%\%%W""
robocopy "%NEW%" "%ROOT%" /E !XD! /NFL /NDL /NJH /NJS /NC /NS /NP >nul

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
