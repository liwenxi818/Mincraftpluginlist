@echo off
title Mafia Game Server
cd /d "%~dp0"

echo ================================
echo  Mafia Plugin Server (port 25566)
echo ================================
echo.

if not exist paper.jar (
    echo [INFO] Copying paper.jar from CSGO server...
    copy "..\MinecraftServer_CSGO\paper.jar" "paper.jar"
    if errorlevel 1 (
        echo [ERROR] paper.jar copy failed. Put paper.jar here manually.
        pause
        exit /b 1
    )
    echo [OK] paper.jar copied.
)

set MVN=
if exist "%USERPROFILE%\AppData\Local\Temp\apache-maven-3.9.6\bin\mvn.cmd" set "MVN=%USERPROFILE%\AppData\Local\Temp\apache-maven-3.9.6\bin\mvn.cmd"
if exist "C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.2\plugins\maven\lib\maven3\bin\mvn.cmd" set "MVN=C:\Program Files\JetBrains\IntelliJ IDEA 2025.3.2\plugins\maven\lib\maven3\bin\mvn.cmd"

if defined MVN (
    echo [INFO] Building MafiaPlugin...
    pushd ..\MafiaPlugin
    call "%MVN%" package -q -DskipTests
    if errorlevel 1 (
        echo [WARN] Maven build failed. Using existing jar.
    ) else (
        echo [OK] Build complete.
    )
    popd
) else (
    echo [INFO] Maven not found. Skipping build.
)

if not exist "..\MafiaPlugin\target\MafiaPlugin-1.0.0.jar" (
    echo [ERROR] MafiaPlugin-1.0.0.jar not found.
    pause
    exit /b 1
)

echo [INFO] Deploying plugin...
copy /Y "..\MafiaPlugin\target\MafiaPlugin-1.0.0.jar" "plugins\MafiaPlugin-1.0.0.jar"
echo [OK] Plugin deployed.

echo.
echo [INFO] Starting server...
echo [INFO] /mafia join / leave / start / end / status / vote
echo ================================
echo.

java -Xmx2G -Xms1G -jar paper.jar --nogui

echo.
echo Server stopped.
pause