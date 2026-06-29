# Read/increment VPP monitor session (tick counter, watcher PIDs).
param(
    [ValidateSet('Get', 'Init', 'NextTick', 'Set')]
    [string]$Action = 'Get',
    [hashtable]$Values = @{}
)

$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$EvDir = Join-Path $Root 'deploy\qemu\run\vpp-evidence'
if (-not (Test-Path $EvDir)) { New-Item -ItemType Directory -Path $EvDir -Force | Out-Null }
$path = Join-Path $EvDir 'vpp-monitor-session.json'

function New-Session {
    return [ordered]@{
        session_id = (Get-Date).ToUniversalTime().ToString('o')
        started_at_local = (Get-Date -Format 'yyyy-MM-dd HH:mm:ss.fff')
        last_tick_at_local = ''
        tick_number = 0
        watcher_pid = 0
        reporter_pid = 0
        runner_pid = 0
    }
}

$session = $null
if ($Action -eq 'Init') {
    $session = New-Session
    $session | ConvertTo-Json -Depth 4 | Set-Content -Path $path -Encoding utf8
    return $session
}

if (-not (Test-Path $path)) {
    $session = New-Session
} else {
    try { $session = Get-Content -Raw $path | ConvertFrom-Json } catch { $session = New-Session }
}

switch ($Action) {
    'NextTick' {
        $ht = @{}
        $session.PSObject.Properties | ForEach-Object { $ht[$_.Name] = $_.Value }
        $ht['tick_number'] = [int]$ht['tick_number'] + 1
        $ht['last_tick_at_local'] = (Get-Date -Format 'yyyy-MM-dd HH:mm:ss.fff')
        $ht | ConvertTo-Json -Depth 4 | Set-Content -Path $path -Encoding utf8
        return ($ht | ConvertTo-Json -Depth 4 | ConvertFrom-Json)
    }
    'Set' {
        $ht = @{}
        $session.PSObject.Properties | ForEach-Object { $ht[$_.Name] = $_.Value }
        foreach ($k in $Values.Keys) { $ht[$k] = $Values[$k] }
        $ht | ConvertTo-Json -Depth 4 | Set-Content -Path $path -Encoding utf8
        return ($ht | ConvertTo-Json -Depth 4 | ConvertFrom-Json)
    }
    default { return $session }
}
