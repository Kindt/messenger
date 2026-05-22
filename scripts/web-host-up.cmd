@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0web-host-up.ps1" %*
