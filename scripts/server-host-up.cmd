@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0server-host-up.ps1" %*
