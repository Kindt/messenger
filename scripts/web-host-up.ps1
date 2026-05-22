# Машина 2: korus-web для LAN (без -Attach). Проверяет .env при KORUS_SERVER_HOST.
# Help: .\scripts\web-host-up.ps1 -Help
param(
    [switch]$Build,
    [switch]$SkipEnsure,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\scripts\web-host-up.ps1 [-Build] [-SkipEnsure]"
    Write-Host "  Do not use -Attach on a separate host. See deploy/two-host/README.md"
    exit 0
}

$Root = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot "lib\korus-env.ps1")
. (Join-Path $PSScriptRoot "lib\Test-KorusWebHostEnv.ps1")
Set-KorusPathEnvironment -RepoRoot $Root

$envFile = Join-Path $env:KORUS_KORUS_WEB_DIR ".env"
Test-KorusWebHostEnv -EnvFilePath $envFile

$skipEnsure = $SkipEnsure -or ($env:SKIP_KORUS_ENSURE -eq "1")
if (-not $skipEnsure) {
    Invoke-KorusEnsureDevTooling -ScriptsRoot $PSScriptRoot
}

& (Join-Path $PSScriptRoot "korus-web-up.ps1") -Build:$Build -SkipEnsure:$skipEnsure

Write-Host ""
Write-Host "Two-host: open http://<WEB_LAN_IP>:9088/ from other PCs. Hot-swap: .\scripts\dev-overlay-up.ps1" -ForegroundColor Yellow
