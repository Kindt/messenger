@echo off
REM Compatibility wrapper for Windows operators.
REM Canonical CI path: scripts/smoke-export-compliance-pack.sh
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0smoke-export-compliance-pack.ps1" %*
