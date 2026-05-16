@echo off
cd /d C:\workspace\kotlin_workspace\skate
echo Compiling Kotlin code...
call gradlew.bat compileKotlin --no-daemon
if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo BUILD SUCCESSFUL
    echo ========================================
    echo.
    echo ComponentSearchProvider.kt - OK
    echo ActionSearchProvider.kt - OK
    echo KoinModule.kt - Updated
    echo.
) else (
    echo.
    echo ========================================
    echo BUILD FAILED
    echo ========================================
    echo.
)
pause
