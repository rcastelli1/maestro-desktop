@echo off
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0package.ps1"
if errorlevel 1 (
    echo.
    echo Nao foi possivel gerar o aplicativo.
    pause
    exit /b 1
)
