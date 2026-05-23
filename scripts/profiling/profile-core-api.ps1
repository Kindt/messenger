param(
    [int]$DurationSeconds = 60,
    [string]$OutputDir = "./jfr-recordings"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
}

$outputFile = Join-Path -Path $OutputDir -ChildPath "core-api.jfr"

Write-Host "Looking for core-api (Tomcat) process..." -ForegroundColor Cyan
$pid = (Get-CimInstance Win32_Process -Filter "Name='java.exe' AND CommandLine LIKE '%tomcat%'" |
    Select-Object -First 1).ProcessId

if (-not $pid) {
    Write-Error "No Tomcat process found. Is core-api running?"
    exit 1
}

Write-Host "Found core-api PID: $pid" -ForegroundColor Green
Write-Host "Recording JFR for ${DurationSeconds}s -> $outputFile" -ForegroundColor Yellow

# Start JFR recording with 60s duration
$jcmdOutput = & jcmd $pid JFR.start duration=${DurationSeconds}s filename=$outputFile 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Error "JFR recording failed: $jcmdOutput"
    exit 1
}

Write-Host $jcmdOutput -ForegroundColor Gray
Write-Host "Recording saved to $outputFile" -ForegroundColor Green
