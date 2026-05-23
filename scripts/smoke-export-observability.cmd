@echo off
REM Compatibility wrapper for Windows operators.
REM Canonical CI path: scripts/smoke-export-observability.sh
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0smoke-export-observability.ps1" %*
