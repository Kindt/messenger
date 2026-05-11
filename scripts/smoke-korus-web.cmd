@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0smoke-korus-web.ps1" %*
