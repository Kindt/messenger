@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0qemu-down.ps1" %*
