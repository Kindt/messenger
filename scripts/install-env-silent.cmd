@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0install-env-silent.ps1" %*
