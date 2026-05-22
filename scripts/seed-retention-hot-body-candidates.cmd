@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0seed-retention-hot-body-candidates.ps1" %*
