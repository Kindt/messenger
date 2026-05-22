@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0dev-overlay-up.ps1" %*
