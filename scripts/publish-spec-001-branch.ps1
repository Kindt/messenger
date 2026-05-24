# Publish spec 001 / retention phase B branch when GitHub is reachable.
# Run from repo root after VPN/proxy is configured.
param(
    [switch]$DirectMain,
    [switch]$Help
)

$ErrorActionPreference = "Stop"

if ($Help) {
    Write-Host @"
Publish local spec 001 work to GitHub.

  .\scripts\publish-spec-001-branch.ps1              # push branch 001-system-review-refactoring
  .\scripts\publish-spec-001-branch.ps1 -DirectMain  # push main (skip feature branch)

If corporate proxy blocks GitHub, try temporarily:
  git -c http.proxy= -c https.proxy= push -u origin 001-system-review-refactoring

Offline bundle (already on disk if generated):
  deploy\qemu\run\spec-001-system-review.bundle
  git clone spec-001-system-review.bundle spec-001-import
  cd spec-001-import && git push -u origin 001-system-review-refactoring
"@
    exit 0
}

$branch = "001-system-review-refactoring"
if (-not (git rev-parse --verify $branch 2>$null)) {
    git branch $branch
}

if ($DirectMain) {
    Write-Host "Pushing main..." -ForegroundColor Cyan
    git push origin main
} else {
    Write-Host "Pushing $branch..." -ForegroundColor Cyan
    git push -u origin $branch
    Write-Host ""
    Write-Host "Create PR on GitHub: base=main head=$branch" -ForegroundColor Yellow
    Write-Host "Title: feat: spec 001 system review + retention phase B" -ForegroundColor DarkGray
}

Write-Host "[OK] publish complete" -ForegroundColor Green
