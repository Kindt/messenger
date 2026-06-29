#Requires -Version 5.1
# SCIM lab bearer smoke (spec 029 VMA-112). Token: env SCIM_BEARER_TOKEN or SCIM_LAB_TOKEN file on guest.
param(
    [string]$BaseUrl = "http://127.0.0.1:18080",
    [string]$Token = "",
    [switch]$Mandatory,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"
Usage: .\scripts\smoke-scim-lab-token.ps1 [-Token xxx]

Uses SCIM_BEARER_TOKEN env or -Token. SKIP exit 0 if unset (lab without SCIM).
GET /api/scim/v2/Users with Bearer -> 200; without -> 401.
"@
    exit 0
}

if (-not $Token) { $Token = $env:SCIM_BEARER_TOKEN }
if (-not $Token) {
    if ($Mandatory) {
        Write-Host "[FAIL] smoke-scim-lab-token: SCIM_BEARER_TOKEN required (VPP mandatory)" -ForegroundColor Red
        exit 1
    }
    Write-Host "[SKIP] smoke-scim-lab-token: SCIM_BEARER_TOKEN not set"
    exit 0
}

$uri = "$BaseUrl/api/scim/v2/Users"
$headers = @{ Authorization = "Bearer $Token" }
$r = Invoke-RestMethod -Uri $uri -Headers $headers -Method Get
if ($null -eq $r) { Write-Host "[FAIL] empty SCIM response"; exit 1 }

try {
    Invoke-RestMethod -Uri $uri -Method Get | Out-Null
    Write-Host "[FAIL] SCIM without token should not succeed"
    exit 1
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    if ($code -ne 401 -and $code -ne 403) {
        Write-Host "[FAIL] expected 401/403 without token got $code"
        exit 1
    }
}

Write-Host "[OK] smoke-scim-lab-token" -ForegroundColor Green
