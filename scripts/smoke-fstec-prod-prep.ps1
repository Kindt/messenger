# Offline/lab verify for FSTEC prod controls (TLS vars, passkeys scaffold, geo doc).
param(
    [string]$BaseUrl = "http://127.0.0.1:18080",
    [switch]$SkipLiveApi,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"
Usage: .\scripts\smoke-fstec-prod-prep.ps1 [-BaseUrl url] [-SkipLiveApi]

Checks:
  - stage/prod ansible group_vars have korus_tls_enabled
  - docs/review/fstec-prod-controls.md exists
  - smoke-tls-redirect -SkipTls
  - smoke-passkeys-scaffold (unless -SkipLiveApi and API down)
"@
    exit 0
}

function Fail([string]$m) { Write-Host "[FAIL] $m" -ForegroundColor Red; exit 1 }

$root = Split-Path -Parent $PSScriptRoot
$doc = Join-Path $root "docs\review\fstec-prod-controls.md"
if (-not (Test-Path $doc)) { Fail "missing $doc" }

foreach ($inv in @("stage", "prod")) {
    $vars = Join-Path $root "deploy\ansible\inventory\$inv\group_vars\all.yml"
    if (-not (Test-Path $vars)) { Fail "missing $vars" }
    $text = Get-Content $vars -Raw
    if ($text -notmatch 'korus_tls_enabled:\s*true') {
        Fail "$inv group_vars missing korus_tls_enabled: true"
    }
    Write-Host "[OK] ansible inventory/$inv TLS vars" -ForegroundColor Green
}

$tlsExit = & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root "scripts\smoke-tls-redirect.ps1") -SkipTls
if ($LASTEXITCODE -ne 0) { exit $tlsExit }

$apiOk = $false
if (-not $SkipLiveApi) {
    try {
        $null = Invoke-WebRequest -Uri "$BaseUrl/api/v1/health" -UseBasicParsing -TimeoutSec 5
        $apiOk = $true
    } catch { }
}

if ($apiOk) {
    $pkExit = & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $root "scripts\smoke-passkeys-scaffold.ps1") -BaseUrl $BaseUrl
    if ($pkExit -ne 0) { exit $pkExit }
} else {
    Write-Host "[SKIP] live passkeys smoke (API down)" -ForegroundColor Yellow
}

Write-Host "[OK] fstec-prod-prep (TLS ansible + doc + tls-skip)" -ForegroundColor Green
