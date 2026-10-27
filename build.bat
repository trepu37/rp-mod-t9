@echo off
rem Windows: one-click build. Requires JDK 21 (https://adoptium.net) and internet.
setlocal
set GRADLE_VER=8.14.3

where java >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Java not found. Install JDK 21 from https://adoptium.net and run again.
  pause
  exit /b 1
)

if not exist ".tools\gradle-%GRADLE_VER%\bin\gradle.bat" (
  echo Downloading Gradle %GRADLE_VER% ...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; New-Item -ItemType Directory -Force .tools | Out-Null; Invoke-WebRequest https://services.gradle.org/distributions/gradle-%GRADLE_VER%-bin.zip -OutFile .tools\gradle.zip; Expand-Archive -Force .tools\gradle.zip .tools"
  if errorlevel 1 (
    echo [ERROR] Could not download Gradle.
    pause
    exit /b 1
  )
)

call ".tools\gradle-%GRADLE_VER%\bin\gradle.bat" build
if errorlevel 1 (
  echo.
  echo [ERROR] Build failed. Copy the error text above and send it for a fix.
  pause
  exit /b 1
)

echo.
echo DONE. Your mod JAR is in the folder: build\libs
echo Use the file WITHOUT "-sources" in its name: rp-autocomplete-1.1.0.jar
pause
