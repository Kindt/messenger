function Write-KorusDebugLog {
    param(
        [Parameter(Mandatory)][string]$Location,
        [Parameter(Mandatory)][string]$Message,
        [string]$HypothesisId = "",
        [hashtable]$Data = @{},
        [string]$RunId = "clean-build"
    )
    # #region agent log
    $agentSession = $env:KORUS_DEBUG_SESSION
    $writeAgent = [bool]$agentSession
    $writeLegacy = ($env:KORUS_DEBUG_LOG -eq "1")
    if (-not $writeAgent -and -not $writeLegacy) { return }

    $repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
    $timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $payload = @{
        timestamp    = $timestamp
        location     = $Location
        message      = $Message
        hypothesisId = $HypothesisId
        runId        = $RunId
        data         = $Data
    }
    if ($writeAgent) {
        $payload["sessionId"] = $agentSession
        $logPath = Join-Path $repoRoot "debug-$agentSession.log"
    } else {
        $logPath = Join-Path $repoRoot "debug-korus-qemu.log"
    }
    try {
        Add-Content -Path $logPath -Value ($payload | ConvertTo-Json -Compress -Depth 8) -Encoding utf8
    } catch {}
    # #endregion
}
