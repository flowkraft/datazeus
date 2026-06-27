@echo off
REM ===========================================================================
REM  DataZeus - Master Everything Data, become a Data Zeus.
REM
REM  Usage:  zeus <command> [args]
REM    zeus koans [course] [series] [lesson]   walk the path (narrow with each token)
REM    zeus update                             pull the latest courses ^& koans (keeps your edits)
REM    zeus help                               this help
REM
REM  koans examples:
REM    zeus koans                       every koan, every course
REM    zeus koans learnsql              all Master SQL koans
REM    zeus koans learnsql series1 _00  ONE lesson   <- the usual path
REM  Short aliases: sql=learnsql, S1=series1, plain "1 00". Courses: sql modeling
REM  etl warehousing dbt viz bi. Needs a JDK 17+ and Maven; koans run on embedded DuckDB.
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
echo   zeus update                             pull the latest courses ^& koans ^(keeps your edits^)
echo   zeus help                               this help
echo.
echo   e.g.  zeus koans learnsql series1 _00
echo Short aliases: sql=learnsql, S1=series1. Courses: sql modeling etl warehousing dbt viz bi.
endlocal & exit /b 0

:koans
where mvn >nul 2>nul
if errorlevel 1 (
  echo Maven ^(mvn^) was not found on your PATH. Install a JDK 17+ and Maven, then run again.
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

call mvn -q -f "%DIR%tests\pom.xml" -Pkoans test -Dtest.includes="!inc!" > "%LOG%" 2>&1

if exist "%PROG%" (
  type "%PROG%"
) else (
  echo The koans did not run. This usually means a compile error in your edit
  echo ^(e.g. a typo where the ___ used to be^). Maven said:
  echo.
  type "%LOG%"
)
endlocal & exit /b 0

:update
echo Updating DataZeus from github.com/flowkraft/datazeus ...
set "TMP_DZ=%TEMP%\datazeus-update-%RANDOM%%RANDOM%"
mkdir "%TMP_DZ%" 2>nul
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; try { Invoke-WebRequest -UseBasicParsing -Uri 'https://github.com/flowkraft/datazeus/archive/refs/heads/main.zip' -OutFile '%TMP_DZ%\dz.zip'; Expand-Archive -Path '%TMP_DZ%\dz.zip' -DestinationPath '%TMP_DZ%' -Force } catch { exit 1 }"
if errorlevel 1 (
  echo Update failed - could not download ^(check your internet connection^).
  rmdir /s /q "%TMP_DZ%" 2>nul
  endlocal & exit /b 1
)
set "NEW=%TMP_DZ%\datazeus-main"
REM Your editable koans live under tests\src\koans - preserve that whole tree.
REM 1) refresh everything EXCEPT your koans tree (framework, datasets, courses, verify-gates, scripts)
robocopy "%NEW%" "%ROOT%" /E /XD "%NEW%\tests\src\koans" /NFL /NDL /NJH /NJS /NC /NS /NP >nul
REM 2) add only brand-NEW koan lessons into that tree; never overwrite a koan you've filled in
robocopy "%NEW%\tests\src\koans" "%ROOT%\tests\src\koans" /E /XC /XN /XO /NFL /NDL /NJH /NJS /NC /NS /NP >nul
rmdir /s /q "%TMP_DZ%" 2>nul
echo.
echo DataZeus is up to date. Your in-progress koans were left untouched.
endlocal & exit /b 0
