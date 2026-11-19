@echo off
setlocal
where java >nul 2>&1
if errorlevel 1 (
    echo Java 21 is required. Install Java 21, then re-run this file.
    pause
    exit /b 1
)
where gradle >nul 2>&1
if errorlevel 1 (
    echo Gradle 8.14 or newer is required to build locally.
    echo Easiest alternative: upload the project to GitHub and use the included Actions workflow.
    echo See README.md for instructions.
    pause
    exit /b 1
)
echo Building SpreeCore for Paper 1.21.11...
call gradle clean test build --no-daemon
if errorlevel 1 (
    echo Build failed. Read the errors above.
    pause
    exit /b 1
)
echo Build finished. The server plugin is build\libs\SpreeCore-1.0.0.jar
pause
