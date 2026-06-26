@echo off
REM ===========================================================================
REM  DataZeus SQL Koans - walk the path to enlightenment.
REM
REM  Usage:  koans.bat [course] [series] [lesson]   (each narrows the run)
REM    koans.bat                            every koan, every course
REM    koans.bat learnsql                  all Master SQL koans
REM    koans.bat learnsql series1          Master SQL, series1
REM    koans.bat learnsql series1 _00      ONE lesson   <- the usual path
REM
REM  The tokens match the folders AND the run's header ("Forging 'series1 _00 ...'").
REM  Short aliases work too: sql=learnsql, S1=series1, and plain "1 00".
REM  Courses: sql modeling etl warehousing dbt viz bi. Needs a JDK 17+ and Maven;
REM  koans run on an embedded DuckDB - no Docker.
REM ===========================================================================
setlocal enabledelayedexpansion
set "DIR=%~dp0"

where mvn >nul 2>nul
if errorlevel 1 (
  echo Maven ^(mvn^) was not found on your PATH. Install a JDK 17+ and Maven, then run again.
  exit /b 1
)

set "seg="
if /I "%~1"=="sql" set "seg=learnsql"
if /I "%~1"=="modeling" set "seg=datamodeling"
if /I "%~1"=="etl" set "seg=etlpipelines"
if /I "%~1"=="warehousing" set "seg=datawarehousing"
if /I "%~1"=="dbt" set "seg=dbt"
if /I "%~1"=="viz" set "seg=datavisualization"
if /I "%~1"=="bi" set "seg=bi"
if not defined seg if not "%~1"=="" if /I not "%~1"=="all" set "seg=%~1"

REM Normalize the series/lesson tokens so "S1 _00" (what the header prints),
REM "series1 _00", or plain "1 00" all resolve to the same path. The :search=
REM substitution is case-insensitive, so it strips s/S/ep/EP and the _ alike.
set "ser=%~2"
if defined ser (
  set "ser=!ser:series=!"
  set "ser=!ser:S=!"
)
set "epi=%~3"
if defined epi (
  set "epi=!epi:EP=!"
  set "epi=!epi:_=!"
)

REM A PATH include that keeps the *Koans restriction (the verified *Spec gate
REM never runs here), narrowed to the chosen course / series / lesson.
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

REM Run quietly: Maven's noise goes to a log; the koans print a clean "path to
REM enlightenment" screen to %PROG%. Koans are RED until filled, so a non-zero
REM exit is EXPECTED — we never echo the build log unless the run never started.
call mvn -q -f "%DIR%tests\pom.xml" -Pkoans test -Dtest.includes="!inc!" > "%LOG%" 2>&1

if exist "%PROG%" (
  type "%PROG%"
) else (
  echo The koans did not run. This usually means a compile error in your edit
  echo ^(e.g. a typo where the ___ used to be^). Maven said:
  echo.
  type "%LOG%"
)
endlocal
