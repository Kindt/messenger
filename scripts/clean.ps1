# Stop Docker stand and remove volumes. From repo root: .\scripts\clean.ps1 [min|full|all]
# Help: .\scripts\clean.ps1 -Help
param(
    [Parameter(Position = 0)]
    [ValidateSet("min", "full", "all")]
    [string]$Stand = "min",
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\scripts\clean.ps1 [min|full|all]  (default: min)"
    Write-Host "  min|full: docker compose down -v for that stack."
    Write-Host "  all: best-effort down for both stacks, then docker system prune -f."
    exit 0
}
$Root = Split-Path -Parent $PSScriptRoot
$Lib = Join-Path $PSScriptRoot "lib\korus-env.ps1"
if (-not (Test-Path $Lib)) {
    Write-Error "Missing: $Lib"
}
. $Lib

Set-KorusPathEnvironment -RepoRoot $Root

Write-Host "=== Cleaning AvandocMsg stand: $Stand ===" -ForegroundColor Cyan

switch ($Stand) {
    "min" {
        Invoke-KorusDockerComposeDown -ComposeFile $env:KORUS_COMPOSE_DEV_MIN -Volumes -WorkingDirectory $env:KORUS_REPO_ROOT -Retries 2
        Write-Host "Stand 'min' cleaned (volumes removed)" -ForegroundColor Green
    }
    "full" {
        Invoke-KorusDockerComposeDown -ComposeFile $env:KORUS_COMPOSE_FULL_SERVER -Volumes -WorkingDirectory $env:KORUS_REPO_ROOT -Retries 2
        Write-Host "Stand 'full' (full-server) cleaned (volumes removed)" -ForegroundColor Green
    }
    "all" {
        $prev = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        try {
            try {
                Invoke-KorusDockerComposeDown -ComposeFile $env:KORUS_COMPOSE_DEV_MIN -Volumes -WorkingDirectory $env:KORUS_REPO_ROOT -Retries 2
            } catch { }
            try {
                Invoke-KorusDockerComposeDown -ComposeFile $env:KORUS_COMPOSE_FULL_SERVER -Volumes -WorkingDirectory $env:KORUS_REPO_ROOT -Retries 2
            } catch { }
            docker system prune -f
        } finally {
            $ErrorActionPreference = $prev
        }
        Write-Host "All stands cleaned (dev-min + full-server, prune)" -ForegroundColor Green
    }
}
