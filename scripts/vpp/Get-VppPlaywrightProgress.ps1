# Parse deploy/qemu/run/playwright-dev-loop.log for live inner tier progress (VPP ticks).
$ErrorActionPreference = 'Continue'
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$logPath = Join-Path $Root 'deploy\qemu\run\playwright-dev-loop.log'
$watchPath = Join-Path $Root 'deploy\qemu\run\vpp-evidence\vpp-pw-progress-watch.json'

$result = [ordered]@{
    active = $false
    test_index = 0
    test_total = 0
    last_line = ''
    log_mtime_local = $null
    progress_stall_min = $null
    same_test_min = $null
}

if (-not (Test-Path $logPath)) { return [pscustomobject]$result }

try {
    $fi = Get-Item -LiteralPath $logPath
    $result.log_mtime_local = $fi.LastWriteTime.ToString('yyyy-MM-dd HH:mm:ss.fff')
} catch { }

$tail = @(Get-Content -LiteralPath $logPath -Tail 400 -ErrorAction SilentlyContinue)
if (-not $tail.Count) { return [pscustomobject]$result }

$head = @(Get-Content -LiteralPath $logPath -TotalCount 30 -ErrorAction SilentlyContinue)
if ($head) {
    $runHead = @($head | Where-Object { $_ -match 'Running (\d+) tests' } | Select-Object -Last 1)
    if ($runHead -and $runHead -match 'Running (\d+) tests') {
        $result.test_total = [int]$Matches[1]
        $result.active = $true
    }
}

$lastTest = @($tail | Where-Object { $_ -match '^\s+(ok|x|-)\s+(\d+)\s+' } | Select-Object -Last 1)
if ($lastTest) {
    if ($lastTest -match '^\s+(ok|x|-)\s+(\d+)\s+(.*)$') {
        $result.test_index = [int]$Matches[2]
        if ($Matches[3]) { $result.last_line = $Matches[3].Trim() }
        $result.active = $true
    }
}

$now = Get-Date
if ($result.log_mtime_local) {
    try {
        $mt = [datetime]::Parse($result.log_mtime_local)
        $result.progress_stall_min = [math]::Round(($now - $mt).TotalMinutes, 1)
    } catch { }
}

$watch = $null
if (Test-Path $watchPath) {
    try { $watch = Get-Content -Raw $watchPath | ConvertFrom-Json } catch { }
}
$sameIdx = $false
if ($watch -and $watch.test_index -eq $result.test_index -and $result.test_index -gt 0) {
    try {
        $since = [datetime]::Parse($watch.since_local)
        $result.same_test_min = [math]::Round(($now - $since).TotalMinutes, 1)
        $sameIdx = $true
    } catch { }
}

$newWatch = [ordered]@{
    test_index = $result.test_index
    since_local = if ($sameIdx -and $watch.since_local) { $watch.since_local } else { $now.ToString('yyyy-MM-dd HH:mm:ss.fff') }
    updated_local = $now.ToString('yyyy-MM-dd HH:mm:ss.fff')
}
try {
    ($newWatch | ConvertTo-Json -Compress) | Set-Content -Path $watchPath -Encoding utf8
} catch { }

return [pscustomobject]$result
