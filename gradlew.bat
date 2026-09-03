@echo off
setlocal EnableExtensions
set "GRADLE_VERSION=8.7"
set "BOOTSTRAP_DIR=%USERPROFILE%\.gradle-bootstrap"
set "GRADLE_HOME=%BOOTSTRAP_DIR%\gradle-%GRADLE_VERSION%"
set "ZIP_FILE=%BOOTSTRAP_DIR%\gradle-%GRADLE_VERSION%-bin.zip"
if not defined GRADLE_DIST_URL set "GRADLE_DIST_URL=https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip"

if exist "%GRADLE_HOME%\bin\gradle.bat" goto run

if not exist "%BOOTSTRAP_DIR%" mkdir "%BOOTSTRAP_DIR%"
echo [VideoCallSDK] Gradle %GRADLE_VERSION% not found. Downloading once...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; Invoke-WebRequest -UseBasicParsing '%GRADLE_DIST_URL%' -OutFile '%ZIP_FILE%'"
if errorlevel 1 goto download_failed

echo [VideoCallSDK] Extracting Gradle...
powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path '%ZIP_FILE%' -DestinationPath '%BOOTSTRAP_DIR%' -Force"
if errorlevel 1 goto extract_failed

del /q "%ZIP_FILE%" >nul 2>&1

:run
call "%GRADLE_HOME%\bin\gradle.bat" %*
exit /b %ERRORLEVEL%

:download_failed
echo ERROR: Gradle download failed. Check network/proxy settings.
exit /b 1

:extract_failed
echo ERROR: Gradle extraction failed.
exit /b 1
