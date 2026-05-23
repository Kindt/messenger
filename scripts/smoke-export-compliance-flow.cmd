@echo off
REM Compatibility wrapper for Windows operators.
REM Canonical CI path: scripts/smoke-export-compliance-flow.sh
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0smoke-export-compliance-flow.ps1" %*
