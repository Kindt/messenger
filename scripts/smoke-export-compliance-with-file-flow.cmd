@echo off
REM DEPRECATED wrapper (kept for backward compatibility).
REM Canonical path: smoke-export-compliance-flow.cmd (or .ps1/.sh with include-file mode).
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0smoke-export-compliance-with-file-flow.ps1" %*
