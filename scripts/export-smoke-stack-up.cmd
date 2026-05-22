@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0export-smoke-stack-up.ps1" %*
