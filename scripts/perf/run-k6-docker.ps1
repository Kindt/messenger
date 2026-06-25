# Run k6 via Docker when host k6 is not installed (QEMU lab, spec 025 VP-C).
param(
    [string]$Script = "scripts/load/pilot-health.js",
    [string]$BaseUrl = "http://127.0.0.1:18080",
    [string]$OutJson = "",
    [hashtable]$Env = @{},
    [switch]$NoThresholds,
    [switch]$Help
)
$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"
Usage: .\scripts\perf\run-k6-docker.ps1 [-Script scripts/load/pilot-rest.js] [-BaseUrl http://127.0.0.1:18080]
  Uses grafana/k6 image; K6_BASE_URL points at host API via host.docker.internal.
"@
    exit 0
}

$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $Root

$docker = Get-Command docker -ErrorAction SilentlyContinue
$useGuest = -not $docker
if ($useGuest) {
    Write-Host "Host docker not found - running k6 on QEMU server guest" -ForegroundColor Yellow
    . "$PSScriptRoot\lib\Invoke-QemuServerGuest.ps1"
    $guestBase = if ($BaseUrl -match '127\.0\.0\.1:18080') { "http://127.0.0.1:8080" } else { $BaseUrl }
    $scriptPosix = ($Script -replace '\\', '/')
    $dockerEnv = "-e K6_BASE_URL=$guestBase"
    foreach ($k in $Env.Keys) { $dockerEnv += " -e $k=$($Env[$k])" }
    $outPosix = ""
    if ($OutJson) {
        $outPosix = ($OutJson -replace '\\', '/')
        $dockerEnv += " -e K6_OUT_JSON=/work/$outPosix"
    }
    $k6Out = if ($OutJson) { "--out json=/work/$outPosix" } else { "" }
    $k6Flags = if ($NoThresholds) { "--no-thresholds" } else { "" }
    $guestScript = "set -euo pipefail`ncd /mnt/korus`ndocker run --rm --network host -v /mnt/korus:/work -w /work $dockerEnv grafana/k6:0.54.0 run $k6Flags $k6Out /work/$scriptPosix"
    Invoke-QemuServerGuest -Script $guestScript
    exit $LASTEXITCODE
}

if (-not $docker) { throw "docker not found on host (install Docker or use host k6)" }

$scriptPath = Join-Path $Root ($Script -replace '/', '\')
if (-not (Test-Path $scriptPath)) { throw "k6 script not found: $Script" }

$envArgs = @("-e", "K6_BASE_URL=$BaseUrl")
foreach ($k in $Env.Keys) { $envArgs += "-e"; $envArgs += "$k=$($Env[$k])" }

$outArgs = @()
if ($OutJson) {
    $outDir = Split-Path -Parent $OutJson
    if ($outDir -and -not (Test-Path $outDir)) { New-Item -ItemType Directory -Force -Path $outDir | Out-Null }
    $envArgs += "-e"; $envArgs += "K6_OUT_JSON=/work/$($OutJson -replace '\\','/')"
}

$k6Args = @("run")
if ($NoThresholds) { $k6Args += "--no-thresholds" }
if ($OutJson) { $k6Args += "--out"; $k6Args += "json=/work/$($OutJson -replace '\\','/')" }
$k6Args += "/work/$($Script -replace '\\','/')"

Write-Host "k6 docker: $Script -> $BaseUrl" -ForegroundColor Cyan
& docker run --rm `
    -v "${Root}:/work" `
    -w /work `
    --add-host=host.docker.internal:host-gateway `
    @envArgs `
    grafana/k6:0.54.0 `
    @k6Args
exit $LASTEXITCODE
