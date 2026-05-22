@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0smoke-export-compliance-with-file-flow.ps1" %*
