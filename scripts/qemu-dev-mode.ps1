# Unified QEMU dev entrypoint (docs/plans/2026-06-12-qemu-dev-modes-stabilization-design.md).
param(
    [ValidateSet("warm", "sync-api", "sync-api-core", "sync-web", "sync-ui", "rebuild-api", "rebuild-web", "enable-hotswap", "status", "stop", "monitored")]
    [string]$Mode = "status",
    [switch]$Force,
    [switch]$Rebuild,
    [switch]$EnableHotswap,
    [ValidateSet("server", "web", "both")]
    [string]$Target = "server",
    [int]$MaxCycles = 5,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$RunDir = Join-Path $Root "deploy\qemu\run"

if ($Help) {
    Write-Host @"

QEMU dev modes facade:

  .\scripts\qemu-dev-mode.ps1 -Mode warm           # qemu-up -KeepDisks + stack-wait
  .\scripts\qemu-dev-mode.ps1 -Mode sync-api       # redeploy ServerOnly (Ansible, no image build)
  .\scripts\qemu-dev-mode.ps1 -Mode sync-api-core  # repo sync + rebuild core-api only (~5-15 min)
  .\scripts\qemu-dev-mode.ps1 -Mode sync-web       # redeploy WebOnly (no build)
  .\scripts\qemu-dev-mode.ps1 -Mode sync-ui        # qemu-web-sync
  .\scripts\qemu-dev-mode.ps1 -Mode rebuild-api    # redeploy ServerOnly -Rebuild
  .\scripts\qemu-dev-mode.ps1 -Mode rebuild-web    # redeploy WebOnly -Rebuild
  .\scripts\qemu-dev-mode.ps1 -Mode enable-hotswap
  .\scripts\qemu-dev-mode.ps1 -Mode status
  .\scripts\qemu-dev-mode.ps1 -Mode stop
  .\scripts\qemu-dev-mode.ps1 -Mode monitored [-Target server|both] [-EnableHotswap]

"@
    exit 0
}

. (Join-Path $Root "deploy\qemu\lib\Get-KorusQemuDevStatus.ps1")

switch ($Mode) {
    "status" {
        $s = Get-KorusQemuDevStatus -RunDir $RunDir -Root $Root
        Write-KorusQemuDevStatus -S $s
        if ($s.ApiReady -and $s.Web) {
            Write-Host "  -> stack ready: sync-ui or playwright" -ForegroundColor Green
        } elseif ($s.Web -and -not $s.ApiReady) {
            Write-Host "  -> try: -Mode sync-api" -ForegroundColor Yellow
        } elseif (-not $s.VmUp) {
            Write-Host "  -> try: -Mode warm" -ForegroundColor Yellow
        }
        exit 0
    }
    "stop" {
        foreach ($f in @("golden-path.no-auto-restart", "qemu-auto-restart.lock", "qemu-redeploy-server.lock", "qemu-redeploy-web.lock")) {
            Remove-Item (Join-Path $RunDir $f) -Force -ErrorAction SilentlyContinue
        }
        & (Join-Path $Root "scripts\qemu-down.ps1")
        exit $LASTEXITCODE
    }
    "warm" {
        & (Join-Path $Root "scripts\qemu-up.ps1") -KeepDisks
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        & (Join-Path $Root "scripts\qemu-stack-wait.ps1") -MaxMinutes 30
        exit $LASTEXITCODE
    }
    "sync-api" {
        $p = @{ ServerOnly = $true }
        if ($Force) { $p.Force = $true }
        & (Join-Path $Root "scripts\qemu-redeploy.ps1") @p
        exit $LASTEXITCODE
    }
    "sync-api-core" {
        & (Join-Path $Root "scripts\qemu-sync-api-core.ps1")
        exit $LASTEXITCODE
    }
    "sync-web" {
        $p = @{ WebOnly = $true }
        if ($Force) { $p.Force = $true }
        & (Join-Path $Root "scripts\qemu-redeploy.ps1") @p
        exit $LASTEXITCODE
    }
    "rebuild-api" {
        $p = @{ ServerOnly = $true; Rebuild = $true }
        if ($Force) { $p.Force = $true }
        & (Join-Path $Root "scripts\qemu-redeploy.ps1") @p
        exit $LASTEXITCODE
    }
    "rebuild-web" {
        $p = @{ WebOnly = $true; Rebuild = $true }
        if ($Force) { $p.Force = $true }
        & (Join-Path $Root "scripts\qemu-redeploy.ps1") @p
        exit $LASTEXITCODE
    }
    "sync-ui" {
        & (Join-Path $Root "scripts\qemu-web-sync.ps1")
        exit $LASTEXITCODE
    }
    "enable-hotswap" {
        & (Join-Path $Root "scripts\qemu-web-hotswap.ps1") -Enable
        exit $LASTEXITCODE
    }
    "monitored" {
        $monitored = Join-Path $Root "scripts\qemu-redeploy-monitored.ps1"
        $mParams = @{ Target = $Target; MaxCycles = $MaxCycles }
        if ($EnableHotswap) { $mParams.EnableHotswap = $true }
        if ($Force) { $mParams.Force = $true }
        if ($Rebuild) { $mParams.Rebuild = $true }
        & $monitored @mParams
        exit $LASTEXITCODE
    }
}
