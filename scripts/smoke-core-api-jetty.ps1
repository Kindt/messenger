# Smoke core-api WAR on Tomcat (host Docker lab — prefer smoke-container-portability-guest.ps1 on QEMU).# Help: .\scripts\smoke-core-api-jetty.ps1 -Help
param(
    [string]$ApiBaseUrl = "http://127.0.0.1:18080",
    [int]$HostPort = 18080,
    [string]$ContainerName = "korus-core-api-war-smoke",
    [string]$ImageName = "korus-messenger-core-api-war:local",
    [string]$Dockerfile = "docker/Dockerfile.core-api.war",
    [switch]$SkipBuild,
    [switch]$KeepContainer,
    [switch]$Help
)
$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\scripts\smoke-core-api-jetty.ps1 [-HostPort <port>] [-SkipBuild] [-KeepContainer] [-Help]"
    Write-Host "  Builds :modules:core-api:war (when Gradle task exists), docker image from $Dockerfile,"
    Write-Host "  runs Tomcat WAR container, GET /api/v1/health."
    Write-Host "  Prefer QEMU guest: .\scripts\smoke-container-portability-guest.ps1"
    Write-Host "  Default health URL: http://127.0.0.1:18080/api/v1/health (QEMU host forward)."
    exit 0
}

function Fail([string]$msg) {
    Write-Host "[FAIL] $msg" -ForegroundColor Red
    exit 1
}

function Get-SmokeRepoRoot {
    return (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
}

$root = Get-SmokeRepoRoot
Set-Location $root

$warGlob = Join-Path $root "modules\core-api\build\libs\core-api-*.war"
$warFile = $null

if (-not $SkipBuild) {
    Write-Host "Attempting Gradle WAR build (:modules:core-api:war) ..." -ForegroundColor Cyan
    $gradleOut = & .\gradlew.bat :modules:core-api:war --no-daemon 2>&1
    $gradleExit = $LASTEXITCODE
    if ($gradleExit -ne 0) {
        Write-Host "[WARN] Gradle war task failed or missing (T021-101 pending). Output:" -ForegroundColor Yellow
        $gradleOut | ForEach-Object { Write-Host $_ }
    }
}

$warMatches = @(Get-ChildItem -Path (Join-Path $root "modules\core-api\build\libs") -Filter "core-api-*.war" -ErrorAction SilentlyContinue)
if ($warMatches.Count -gt 0) {
    $warFile = $warMatches[0].FullName
    Write-Host "WAR artifact: $warFile" -ForegroundColor Cyan
} else {
    Fail "No core-api WAR in modules/core-api/build/libs. Run after T021-101 or pass -SkipBuild with prebuilt WAR."
}

Write-Host "Building Docker image $ImageName from $Dockerfile ..." -ForegroundColor Cyan
& docker build -f $Dockerfile -t $ImageName $root
if ($LASTEXITCODE -ne 0) { Fail "docker build failed" }

$existing = docker ps -aq -f "name=^${ContainerName}$"
if ($existing) {
    Write-Host "Removing existing container $ContainerName ..." -ForegroundColor Cyan
    docker rm -f $ContainerName | Out-Null
}

Write-Host "Starting Tomcat WAR container on host port $HostPort ..." -ForegroundColor Cyan
& docker run -d --name $ContainerName -p "${HostPort}:8080" $ImageName
if ($LASTEXITCODE -ne 0) { Fail "docker run failed" }

$healthUrl = "$ApiBaseUrl/api/v1/health"
if ($HostPort -ne 18080) {
    $healthUrl = "http://127.0.0.1:${HostPort}/api/v1/health"
}

$deadline = (Get-Date).AddMinutes(3)
$ok = $false
while ((Get-Date) -lt $deadline) {
    try {
        $r = Invoke-RestMethod -Uri $healthUrl -Method Get -TimeoutSec 5
        if ($r.status) {
            $ok = $true
            break
        }
    } catch {
        Start-Sleep -Seconds 5
    }
}

if (-not $ok) {
    Write-Host "Container logs:" -ForegroundColor Yellow
    docker logs $ContainerName 2>&1 | Select-Object -Last 40
    if (-not $KeepContainer) { docker rm -f $ContainerName | Out-Null }
    Fail "health check timed out: $healthUrl"
}

Write-Host "[OK] GET $healthUrl status=$($r.status)" -ForegroundColor Green

if (-not $KeepContainer) {
    docker rm -f $ContainerName | Out-Null
    Write-Host "Container $ContainerName removed." -ForegroundColor Cyan
} else {
    Write-Host "Container left running: $ContainerName (port $HostPort)." -ForegroundColor Cyan
}

Write-Host "Jetty 12 spike: swap base image when WAR bootstrap is green; same health probe." -ForegroundColor Cyan
exit 0
