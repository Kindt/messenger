@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0smoke-export-compliance-flow.ps1" %*
