param(
    [string]$ContainerName = "",
    [string]$OutputName = "",
    [string[]]$Service = @(),
    [int]$DurationSeconds = 60,
    [string]$OutputDir = "./jfr-recordings",
    [int]$Pid = 1
)

$ErrorActionPreference = "Stop"

$ProfilingTargets = @(
    @{ Service = "core-api"; OutputName = "core-api" },
    @{ Service = "message-pipeline"; OutputName = "message-pipeline" },
    @{ Service = "archiver-worker"; OutputName = "archiver-worker" },
    @{ Service = "deep-archiver-worker"; OutputName = "deep-archiver-worker" },
    @{ Service = "retention-worker"; OutputName = "retention-worker" },
    @{ Service = "export-replay-worker"; OutputName = "export-replay-worker" },
    @{ Service = "push-worker"; OutputName = "push-worker" },
    @{ Service = "indexer-worker"; OutputName = "indexer-worker" },
    @{ Service = "bot-delivery-worker"; OutputName = "bot-delivery-worker" }
)

function Resolve-ContainerName {
    param([string]$ServiceName)
    $matches = docker ps --filter "name=${ServiceName}" --format "{{.Names}}" 2>$null
    if (-not $matches) {
        return $null
    }
    return ($matches | Select-Object -First 1)
}

function Invoke-ProfileContainer {
    param(
        [string]$TargetContainer,
        [string]$TargetOutputName,
        [int]$Seconds,
        [string]$Dir,
        [int]$ProcessId
    )

    if (-not (Test-Path -LiteralPath $Dir)) {
        New-Item -ItemType Directory -Path $Dir -Force | Out-Null
    }

    $containerJfr = "/tmp/$TargetOutputName.jfr"
    $outputFile = Join-Path $Dir "$TargetOutputName.jfr"

    Write-Host "Recording JFR in container '$TargetContainer' (pid $ProcessId) for ${Seconds}s..." -ForegroundColor Cyan

    $startOut = docker exec $TargetContainer jcmd $ProcessId JFR.start "duration=${Seconds}s" "filename=$containerJfr" 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Error "JFR.start failed for ${TargetContainer}: $startOut"
        return $false
    }
    Write-Host $startOut -ForegroundColor Gray

    Write-Host "Waiting ${Seconds}s for recording to finish..." -ForegroundColor Yellow
    Start-Sleep -Seconds ($Seconds + 2)

    docker cp "${TargetContainer}:${containerJfr}" $outputFile
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Failed to copy JFR from container $TargetContainer"
        return $false
    }

    Write-Host "Saved $outputFile" -ForegroundColor Green
    return $true
}

if ($ContainerName) {
    if (-not $OutputName) { $OutputName = $ContainerName -replace '[^a-zA-Z0-9_-]', '-' }
    $ok = Invoke-ProfileContainer -TargetContainer $ContainerName -TargetOutputName $OutputName `
        -Seconds $DurationSeconds -Dir $OutputDir -ProcessId $Pid
    if (-not $ok) { exit 1 }
    Write-Host "Open with: jmc $OutputDir\$OutputName.jfr" -ForegroundColor DarkGray
    exit 0
}

$targets = if ($Service.Count -gt 0) {
    $ProfilingTargets | Where-Object { $Service -contains $_.Service }
} else {
    $ProfilingTargets
}

if ($targets.Count -eq 0) {
    Write-Error "No profiling targets matched. Use -Service core-api,message-pipeline,... or -ContainerName."
    exit 1
}

$failed = @()
foreach ($target in $targets) {
    $resolved = Resolve-ContainerName -ServiceName $target.Service
    if (-not $resolved) {
        Write-Warning "Skipping $($target.Service): no running container matched"
        $failed += $target.Service
        continue
    }
    $ok = Invoke-ProfileContainer -TargetContainer $resolved -TargetOutputName $target.OutputName `
        -Seconds $DurationSeconds -Dir $OutputDir -ProcessId $Pid
    if (-not $ok) {
        $failed += $target.Service
    }
}

if ($failed.Count -gt 0) {
    Write-Warning "Failed or skipped: $($failed -join ', ')"
    exit 1
}

Write-Host "All profiling recordings saved under $OutputDir" -ForegroundColor Green
Write-Host "Open with: jmc $OutputDir\<service>.jfr" -ForegroundColor DarkGray
