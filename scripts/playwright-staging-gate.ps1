# Outer Playwright gate against staging HTTPS UI (operator workstation).
param(
    [Parameter(Mandatory = $true)]
    [string]$BaseUrl,
    [string]$ApiUrl = "",
    [string]$Tier = "all-inner"
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not $ApiUrl) {
    $ApiUrl = $BaseUrl.TrimEnd("/") + "/api"
}
$env:PLAYWRIGHT_BASE_URL = $BaseUrl.TrimEnd("/")
$env:KORUS_API_URL = $ApiUrl.TrimEnd("/")
Push-Location (Join-Path $repoRoot "tests\e2e-web")
try {
    & "$repoRoot\scripts\playwright-dev-loop.ps1" -Tier $Tier
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
