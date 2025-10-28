@echo off
setlocal enabledelayedexpansion

echo Checking dependencies...

REM Function to check if a command exists
where docker >nul 2>&1
if errorlevel 1 (
    echo Error: docker is not found in PATH
    set MISSING_DEPS=!MISSING_DEPS! docker
)

where npm >nul 2>&1
if errorlevel 1 (
    echo Error: npm is not found in PATH
    set MISSING_DEPS=!MISSING_DEPS! npm
)

where mvn >nul 2>&1
if errorlevel 1 (
    echo Error: mvn (Maven) is not found in PATH
    set MISSING_DEPS=!MISSING_DEPS! mvn
)

where java >nul 2>&1
if errorlevel 1 (
    echo Error: java is not found in PATH
    set MISSING_DEPS=!MISSING_DEPS! java
)

REM If there are missing dependencies, print error and exit
if defined MISSING_DEPS (
    echo Error: Missing required dependencies: !MISSING_DEPS!
    echo.
    echo Please install the missing dependencies and try again.
    pause
    exit /b 1
)

echo All dependencies are installed.

REM Get the directory where this script is located
set "SCRIPT_DIR=%~dp0"
REM Navigate to the parent directory (event-publisher root)
set "PROJECT_ROOT=%SCRIPT_DIR%.."

REM Navigate to the event-publisher subdirectory (where docker-compose.yml is)
set "EVENT_PUBLISHER_DIR=%PROJECT_ROOT%\event-publisher"

if not exist "%EVENT_PUBLISHER_DIR%" (
    echo Error: Directory %EVENT_PUBLISHER_DIR% does not exist.
    pause
    exit /b 1
)

cd /d "%EVENT_PUBLISHER_DIR%"
echo Navigated to: %cd%

REM Start Docker Compose services (Kafka and Zookeeper)
echo Starting Docker Compose services...
docker-compose up -d

echo Waiting for Kafka to start up...
timeout /t 10 /nobreak >nul

REM Navigate to the soccer server directory
set "SOCCER_SERVER_DIR=%PROJECT_ROOT%\..\soccer-server"

if not exist "%SOCCER_SERVER_DIR%" (
    echo Error: Directory %SOCCER_SERVER_DIR% does not exist.
    pause
    exit /b 1
)

cd /d "%SOCCER_SERVER_DIR%"
echo Navigated to: %cd%

echo Starting soccer server...
start "Soccer Server" cmd /c npm start

REM Wait a bit for the soccer server to start
timeout /t 3 /nobreak >nul

REM Navigate back to the event-publisher directory and start the Spring Boot app
cd /d "%EVENT_PUBLISHER_DIR%"
echo Navigated to: %cd%

echo Starting Spring Boot application...
start "Event Publisher" cmd /c mvn spring-boot:run

echo.
echo All services started successfully!
echo.
echo Services running:
echo - Kafka and Zookeeper: via Docker Compose
echo - Soccer Server: on http://localhost:8081
echo - Event Publisher: on http://localhost:8080
echo.
echo To stop all services, use Ctrl+C in each command window and then run: docker-compose down in the event-publisher directory
echo.
pause