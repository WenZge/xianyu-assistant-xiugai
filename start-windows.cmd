@echo off
setlocal
cd /d "%~dp0"
set "BUTLER_JAVA=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "BUTLER_JAVA=%JAVA_HOME%\bin\java.exe"
if "%BUTLER_JAVA%"=="java" for /d %%J in ("%~dp0..\.tools\jdk-*") do if exist "%%~fJ\bin\java.exe" set "BUTLER_JAVA=%%~fJ\bin\java.exe"
if not exist "target\XianYuAssistant-2.0.3.jar" (
  echo Missing target\XianYuAssistant-2.0.3.jar. Build the project first.
  pause
  exit /b 1
)
echo Open http://localhost:12400 after startup.
"%BUTLER_JAVA%" -jar "target\XianYuAssistant-2.0.3.jar" %*
if errorlevel 1 pause
endlocal
