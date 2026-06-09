function Write-KorusDebugLog {
    param(
        [Parameter(Mandatory)][string]$Location,
        [Parameter(Mandatory)][string]$Message,
        [string]$HypothesisId = "",
        [hashtable]$Data = @{},
        [string]$RunId = "clean-build"
    )
    if ($env:KORUS_DEBUG_LOG -ne "1") {
        return
    }
    $repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
    $logPath = Join-Path $repoRoot "debug-korus-qemu.log"
    $payload = @{
        timestamp    = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
        location     = $Location
        message      = $Message
        hypothesisId = $HypothesisId
        runId        = $RunId
        data         = $Data
    }
    try {
        Add-Content -Path $logPath -Value ($payload | ConvertTo-Json -Compress -Depth 6) -Encoding utf8
    } catch {}
}
