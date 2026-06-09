param(
    [Parameter(Mandatory = $true)]
    [string]$ContainerName,
    [string]$OutputName = "",
    [int]$DurationSeconds = 60,
    [string]$OutputDir = "./jfr-recordings",
    [int]$Pid = 1
)

$ErrorActionPreference = "Stop"

if (-not $OutputName) { $OutputName = $ContainerName -replace '[^a-zA-Z0-9_-]', '-' }
if (-not (Test-Path -LiteralPath $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
}

$containerJfr = "/tmp/$OutputName.jfr"
$outputFile = Join-Path $OutputDir "$OutputName.jfr"

Write-Host "Recording JFR in container '$ContainerName' (pid $Pid) for ${DurationSeconds}s..." -ForegroundColor Cyan

$startOut = docker exec $ContainerName jcmd $Pid JFR.start "duration=${DurationSeconds}s" "filename=$containerJfr" 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Error "JFR.start failed: $startOut"
    exit 1
}
Write-Host $startOut -ForegroundColor Gray

Write-Host "Waiting ${DurationSeconds}s for recording to finish..." -ForegroundColor Yellow
Start-Sleep -Seconds ($DurationSeconds + 2)

docker cp "${ContainerName}:${containerJfr}" $outputFile
if ($LASTEXITCODE -ne 0) {
    Write-Error "Failed to copy JFR from container"
    exit 1
}

Write-Host "Saved $outputFile" -ForegroundColor Green
Write-Host "Open with: jmc $outputFile" -ForegroundColor DarkGray
