#Requires -Version 5.1
# Read-only network_profile catalog vs compose (spec 029 VMA-110).
param(
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\scripts\smoke-network-profile-catalog.ps1"
    exit 0
}

$Root = Split-Path -Parent $PSScriptRoot
$yaml = Join-Path $Root "modules\core-api\src\main\resources\product-modules.yaml"
if (-not (Test-Path $yaml)) { Write-Host "[FAIL] missing product-modules.yaml"; exit 1 }

$profiles = @()
$current = $null
Get-Content $yaml | ForEach-Object {
    if ($_ -match '^\s+- id: (addon-[^\s]+)') { $current = $matches[1] }
    if ($_ -match 'network_profile:\s*(\S+)' -and $current) {
        $profiles += @{ addon = $current; network_profile = $matches[1] }
        $current = $null
    }
}

$unique = $profiles | ForEach-Object { $_.network_profile } | Sort-Object -Unique
Write-Host "network_profiles in runtime yaml: $($unique -join ', ')"
Write-Host "addon rows: $($profiles.Count)"

$out = Join-Path $Root "deploy\qemu\run\vma-evidence\network-profile-catalog.json"
$dir = Split-Path $out
if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
@{ addons = $profiles; unique_profiles = $unique; generated_at = (Get-Date).ToString("o") } |
    ConvertTo-Json -Depth 4 | Set-Content -Path $out -Encoding utf8

Write-Host "[OK] network profile catalog -> $out" -ForegroundColor Green
