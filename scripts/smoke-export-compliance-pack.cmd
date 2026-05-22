@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0smoke-export-compliance-pack.ps1" %*
