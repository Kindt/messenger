# Копирует эталон webui в dev-overlay/webui/ (hot-swap sandbox).
param(
    [string]$RepoRoot = (Split-Path -Parent $PSScriptRoot),
    [switch]$Force,
    [switch]$Help
)

if ($Help) {
    Write-Host "Usage: .\scripts\dev-overlay-init.ps1 [-Force]"
    Write-Host "  Copies modules/web-client/.../webui/* -> dev-overlay/webui/"
    exit 0
}

$ErrorActionPreference = "Stop"
$src = Join-Path $RepoRoot "modules\web-client\src\main\resources\webui"
$dst = Join-Path $RepoRoot "dev-overlay\webui"
if (-not (Test-Path $src)) {
    Write-Error "Source not found: $src"
}
New-Item -ItemType Directory -Force -Path $dst | Out-Null
$files = @("index.html", "app.js", "styles.css")
foreach ($f in $files) {
    $from = Join-Path $src $f
    $to = Join-Path $dst $f
    if ((Test-Path $to) -and -not $Force) {
        Write-Host "Skip (exists): $to  (use -Force to overwrite)"
        continue
    }
    Copy-Item -Path $from -Destination $to -Force
    Write-Host "Copied: $f"
}
Write-Host "[OK] dev-overlay ready. Edit dev-overlay/webui/, then .\scripts\dev-overlay-up.ps1" -ForegroundColor Green
