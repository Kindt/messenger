# Publish spec 001 branch: try git push, always refresh offline bundle on failure.
param(
    [switch]$DirectMain,
    [switch]$BundleOnly,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$branch = "001-system-review-refactoring"
$bundle = Join-Path $root "deploy\qemu\run\spec-001-system-review.bundle"

function Write-Bundle {
    $runDir = Split-Path $bundle -Parent
    if (-not (Test-Path $runDir)) { New-Item -ItemType Directory -Path $runDir -Force | Out-Null }
    Write-Host "Creating bundle: $bundle" -ForegroundColor Cyan
    git -C $root bundle create $bundle "origin/main..HEAD"
    $mb = [math]::Round((Get-Item $bundle).Length / 1MB, 1)
    $count = (git -C $root log origin/main..HEAD --oneline | Measure-Object -Line).Lines
    Write-Host "Bundle ready: $mb MiB, $count commits ahead of origin/main" -ForegroundColor Green
}

function Try-Push([string]$RemoteRef) {
    Write-Host "Pushing $RemoteRef ..." -ForegroundColor Cyan
    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $attempts = @(
            @{ Label = "default proxy"; Args = @() },
            @{ Label = "no proxy"; Args = @("-c", "http.proxy=", "-c", "https.proxy=") }
        )
        foreach ($a in $attempts) {
            Write-Host "  try: $($a.Label)" -ForegroundColor DarkGray
            & git -C $root @($a.Args) push -u origin $RemoteRef 2>&1 | ForEach-Object { Write-Host $_ }
            if ($LASTEXITCODE -eq 0) { return $true }
        }
        return $false
    } finally {
        $ErrorActionPreference = $prevEap
    }
}

if ($Help) {
    Write-Host @"
Publish spec 001 work to GitHub (or bundle for offline transfer).

  .\scripts\publish-spec-001-branch.ps1              # push feature branch
  .\scripts\publish-spec-001-branch.ps1 -DirectMain # push main
  .\scripts\publish-spec-001-branch.ps1 -BundleOnly # bundle only

On push failure, bundle is refreshed at:
  deploy\qemu\run\spec-001-system-review.bundle

Import elsewhere:
  git clone spec-001-system-review.bundle spec-001-import
  cd spec-001-import
  git push -u origin 001-system-review-refactoring
"@
    exit 0
}

Push-Location $root
try {
    if (-not (git rev-parse --verify $branch 2>$null)) {
        git branch $branch
    }

    if ($BundleOnly) {
        Write-Bundle
        exit 0
    }

    $target = if ($DirectMain) { "main" } else { $branch }
    if (Try-Push $target) {
        Write-Host "[OK] pushed $target" -ForegroundColor Green
        if (-not $DirectMain) {
            Write-Host "Open PR: base=main head=$branch" -ForegroundColor Yellow
        }
        exit 0
    }

    Write-Host "[WARN] push failed - refreshing offline bundle" -ForegroundColor Yellow
    Write-Bundle
    Write-Host ""
    Write-Host "Import on a machine with GitHub access, then push from there." -ForegroundColor DarkGray
    exit 1
} finally {
    Pop-Location
}
