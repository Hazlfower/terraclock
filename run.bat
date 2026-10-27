@echo off
chcp 65001 > nul
cd /d "%~dp0"

rem ===== JavaFX SDK 경로 =====
set "FX_LIB=C:\javafx-sdk-27\lib"
set "FX_MODULES=javafx.controls,javafx.web"

if not exist "%FX_LIB%\javafx.controls.jar" (
    echo [ERROR] JavaFX SDK not found: %FX_LIB%
    echo Download it from https://gluonhq.com/products/javafx/
    echo and extract it here as a folder named "javafx-sdk".
    pause
    exit /b 1
)

rem ===== java / javac 선택 =====
set "HAZ_JAVA=java"
set "HAZ_JAVAC=javac"
if exist "runtime\bin\java.exe"  set "HAZ_JAVA=runtime\bin\java.exe"
if exist "runtime\bin\javac.exe" set "HAZ_JAVAC=runtime\bin\javac.exe"

echo Compiling...
if exist bin rmdir /s /q bin
"%HAZ_JAVAC%" -encoding UTF-8 --module-path "%FX_LIB%" --add-modules %FX_MODULES% -d bin *.java
if errorlevel 1 (
    echo.
    echo [ERROR] Compile failed. Check that JDK 17+ is installed.
    pause
    exit /b 1
)

echo Starting...
"%HAZ_JAVA%" -Dfile.encoding=UTF-8 --module-path "%FX_LIB%" --add-modules %FX_MODULES% -cp bin Main
if errorlevel 1 pause