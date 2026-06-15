param(
    [Parameter(Mandatory = $true)]
    [string]$WorkerName,
    [int]$DurationSeconds = 60,
    [string]$OutputDir = "./jfr-recordings"
)

$ErrorActionPreference = "Stop"

$validWorkers = @(
    "deep-archiver", "retention", "indexer", "export-replay", "archiver",
    "message-pipeline", "push", "bot-delivery"
)
if ($WorkerName -notin $validWorkers) {
    Write-Error "WorkerName must be one of: $($validWorkers -join ', ')"
    exit 1
}

if (-not (Test-Path -LiteralPath $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
}

$outputFile = Join-Path -Path $OutputDir -ChildPath "${WorkerName}.jfr"

Write-Host "Looking for ${WorkerName} process..." -ForegroundColor Cyan
$pid = (Get-CimInstance Win32_Process -Filter "Name='java.exe' AND CommandLine LIKE '%${WorkerName}%'" |
    Select-Object -First 1).ProcessId

if (-not $pid) {
    Write-Error "No ${WorkerName} process found. Is it running?"
    exit 1
}

Write-Host "Found ${WorkerName} PID: $pid" -ForegroundColor Green
Write-Host "Recording JFR for ${DurationSeconds}s -> $outputFile" -ForegroundColor Yellow

$jcmdOutput = & jcmd $pid JFR.start duration=${DurationSeconds}s filename=$outputFile 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Error "JFR recording failed: $jcmdOutput"
    exit 1
}

Write-Host $jcmdOutput -ForegroundColor Gray
Write-Host "Recording saved to $outputFile" -ForegroundColor Green
