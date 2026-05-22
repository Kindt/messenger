@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0dev-web-stack-down.ps1" %*
