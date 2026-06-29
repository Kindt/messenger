#Requires -Version 5.1
# VPP-2: verify GET /platform/capabilities matches product-modules catalog (spec 030).
param(
    [string]$BaseUrl = "http://127.0.0.1:18080",
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"
Usage: .\scripts\smoke-platform-capabilities.ps1 [-BaseUrl http://127.0.0.1:18080]

Public capabilities smoke - no auth required.
"@
    exit 0
}

$Root = Split-Path -Parent $PSScriptRoot
$yamlPath = Join-Path $Root "modules\core-api\src\main\resources\product-modules.yaml"
if (-not (Test-Path $yamlPath)) { Write-Error "missing $yamlPath"; exit 1 }

$catalogIds = @()
$lines = Get-Content -Path $yamlPath
foreach ($line in $lines) {
    if ($line -match '^\s+-\s+id:\s+(addon-[a-z0-9-]+)\s*$') {
        $catalogIds += $Matches[1]
    }
}
if ($catalogIds.Count -lt 10) {
    Write-Error "expected >=10 addons in catalog, got $($catalogIds.Count)"
    exit 1
}

$API = "$BaseUrl/api/v1"
$cap = Invoke-RestMethod -Method GET -Uri "$API/platform/capabilities" -TimeoutSec 15
if (-not $cap.product) { throw "capabilities missing product section" }
if ($cap.product.base.state -ne "active" -and $cap.product.base.state -ne "required") {
    throw "unexpected base state=$($cap.product.base.state)"
}

$enabled = @($cap.product.addons_enabled)
Write-Host "  catalog addons: $($catalogIds.Count)" -ForegroundColor DarkGray
Write-Host "  stack addons_enabled: $($enabled.Count)" -ForegroundColor DarkGray

foreach ($id in $enabled) {
    if ($catalogIds -notcontains $id) {
        throw "unknown addon in capabilities: $id"
    }
    if (-not $cap.modules.$id) {
        throw "capabilities.modules missing $id"
    }
    $mod = $cap.modules.$id
    if (-not $mod.selected) {
        throw "addon $id in addons_enabled but selected=false"
    }
}

foreach ($id in $catalogIds) {
    if (-not $cap.modules.$id) {
        throw "capabilities.modules missing catalog addon $id"
    }
}

$featureCount = ($cap.features.PSObject.Properties | Measure-Object).Count
if ($featureCount -lt 50) {
    throw "expected >=50 features in capabilities, got $featureCount"
}

Write-Host "[OK] platform capabilities ($($enabled.Count) addons enabled, $featureCount features)" -ForegroundColor Green
