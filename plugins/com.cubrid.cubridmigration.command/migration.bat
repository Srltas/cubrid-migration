@echo off
setlocal

rem Check if JAVA_HOME is set
if defined JAVA_HOME (
    set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
) else (
    set "JAVA_EXE=java"
)

rem Check if java exists
"%JAVA_EXE%" -version >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo Java is not found. Please set JAVA_HOME or add java to PATH.
    exit /b 1
)

rem Get the directory of this script
set "SCRIPT_DIR=%~dp0"

rem Execute the migration tool
"%JAVA_EXE%" -Xms40M -Xmx1024M -jar "%SCRIPT_DIR%migration.jar" %*

endlocal
