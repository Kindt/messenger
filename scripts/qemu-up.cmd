@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0qemu-up.ps1" %*
