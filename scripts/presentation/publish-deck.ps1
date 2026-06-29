# Rebuild product deck, run local gates, remind to commit docs/index.html.
param(
    [switch]$SkipTests
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Push-Location $Root
try {
    python scripts/presentation/build.py
    python scripts/presentation/smoke_deck.py
    python scripts/presentation/analyze_deck.py
    python scripts/presentation/verify_offerings_urls.py
    if (-not $SkipTests) {
        python -m pytest scripts/presentation/ -q
    }
    if (-not (Test-Path docs/.nojekyll)) {
        New-Item -ItemType File -Path docs/.nojekyll -Force | Out-Null
    }
    Write-Host ""
    Write-Host "OK: docs/index.html rebuilt. Commit docs/ and push - GitHub Pages deploys committed artifact (no CI rebuild)." -ForegroundColor Green
    Write-Host 'Live: https://kindt.github.io/messenger/' -ForegroundColor Cyan
}
finally {
    Pop-Location
}
