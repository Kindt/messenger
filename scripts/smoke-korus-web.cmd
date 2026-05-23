@echo off
REM Compatibility wrapper for Windows operators.
REM Canonical manual path: scripts/smoke-korus-web.sh
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0smoke-korus-web.ps1" %*
