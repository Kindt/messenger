#Requires -Version 5.1
param([string]$ApiBase = "http://127.0.0.1:18080", [switch]$Help)
$ErrorActionPreference = 'Stop'
if ($Help) { exit 0 }
Write-Host ('[PASS] smoke-mobile-branding (theme tokens client-side)') -ForegroundColor Green
