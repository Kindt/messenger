@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0retention-export-smoke-up.ps1" %*
