@echo off
setlocal EnableDelayedExpansion

:: ============================================================================
:: Songs of Syx (v0.71.44) — Single-PC Dual-Instance Multiplayer Test Launcher
:: Project Root : %USERPROFILE%\Documents\Songs of Syx MP Mod
:: Game Root    : F:\SteamLibrary\steamapps\common\Songs of Syx
:: ============================================================================

set "PROJECT_DIR=%USERPROFILE%\Documents\Songs of Syx MP Mod"
set "GAME_DIR=F:\SteamLibrary\steamapps\common\Songs of Syx"
set "APPDATA_SYX=%USERPROFILE%\AppData\Roaming\songsofsyx"
set "MOD_JAR=%APPDATA_SYX%\mods\SyxHybridCoop\V71\script\SyxHybridCoop.jar"
set "GAME_JAR=%GAME_DIR%\SongsOfSyx.jar"
set "JAVA_EXE=%GAME_DIR%\jre\bin\java.exe"
set "LOG_DIR=%APPDATA_SYX%\logs"

:: 1. One-time extraction of SongsOfSyx-sources.jar into vendor\syx-src-v71.44 for the coding agent
if not exist "%PROJECT_DIR%\vendor\syx-src-v71.44\game\GAME.java" (
    echo [SETUP] Extracting SongsOfSyx-sources.jar into "%PROJECT_DIR%\vendor\syx-src-v71.44"...
    mkdir "%PROJECT_DIR%\vendor\syx-src-v71.44" 2>nul
    tar -xf "%GAME_DIR%\info\SongsOfSyx-sources.jar" -C "%PROJECT_DIR%\vendor\syx-src-v71.44"
)

:: 2. Verify that SyxHybridCoop.jar has been built and installed
if not exist "%MOD_JAR%" (
    echo [ERROR] Mod JAR not found at: "%MOD_JAR%"
    echo         Run "mvn clean install" in "%PROJECT_DIR%" first.
    exit /b 1
)

if not exist "%LOG_DIR%" mkdir "%LOG_DIR%" 2>nul
if not exist "%APPDATA_SYX%\saves_client" mkdir "%APPDATA_SYX%\saves_client" 2>nul

:: 3. Build classpath: SyxHybridCoop.jar FIRST, then base scripts & other mods, then SongsOfSyx.jar
set "CP=%MOD_JAR%"
for %%J in ("%GAME_DIR%\base\script\*.jar") do (
    set "CP=!CP!;%%~fJ"
)
for /d %%D in ("%APPDATA_SYX%\mods\*") do (
    for %%J in ("%%D\V71\script\*.jar") do (
        if /i not "%%~fJ"=="%MOD_JAR%" (
            set "CP=!CP!;%%~fJ"
        )
    )
)
set "CP=!CP!;%GAME_JAR%"
set "JVM_COMMON=-Xmx4G -Dfile.encoding=UTF-8"

echo [LAUNCH] Starting Instance 1 (HOST on 127.0.0.1:27015)...
echo          Log: "%LOG_DIR%\coop-host.log"
start "Songs of Syx - COOP HOST" /D "%GAME_DIR%" cmd /c ""%JAVA_EXE%" %JVM_COMMON% -Dcoop.testRole=HOST -Dcoop.port=27015 -cp "!CP!" init.MainProcess > "%LOG_DIR%\coop-host.log" 2>&1"

:: Brief pause so Host binds port 27015 and finishes file checks first
ping -n 4 127.0.0.1 >nul

echo [LAUNCH] Starting Instance 2 (CLIENT to 127.0.0.1:27015, isolated save folder: saves_client)...
echo          Log: "%LOG_DIR%\coop-client.log"
start "Songs of Syx - COOP CLIENT" /D "%GAME_DIR%" cmd /c ""%JAVA_EXE%" %JVM_COMMON% -Dcoop.testRole=CLIENT -Dcoop.connect=127.0.0.1:27015 -Dcoop.saveFolder=saves_client -cp "!CP!" init.MainProcess > "%LOG_DIR%\coop-client.log" 2>&1"

echo [DONE] Both Host and Client instances are running.
echo        Have your coding agent inspect "%LOG_DIR%\coop-host.log" and "%LOG_DIR%\coop-client.log" after testing.
endlocal
