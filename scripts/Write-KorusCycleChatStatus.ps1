#Requires -Version 5.1
# Compact Russian status for chat / loop ticks.
param([switch]$Json)

$ErrorActionPreference = 'Continue'
$Root = Split-Path -Parent $PSScriptRoot
$RunDir = Join-Path $Root 'deploy\qemu\run'
$EvDir = Join-Path $RunDir 'vpp-evidence'

function Read-JsonFile($path) {
    if (-not (Test-Path $path)) { return $null }
    try { return Get-Content -Raw $path | ConvertFrom-Json } catch { return $null }
}

$cycle = Read-JsonFile (Join-Path $RunDir 'cycle-unattended-status.json')
$cyclePid = Read-JsonFile (Join-Path $RunDir 'cycle-unattended.pid')
$pidVal = if (Test-Path (Join-Path $RunDir 'cycle-unattended.pid')) {
    (Get-Content (Join-Path $RunDir 'cycle-unattended.pid') -Raw).Trim()
} else { '' }
$cycleAlive = $false
if ($pidVal -match '^\d+$') {
    $cycleAlive = $null -ne (Get-Process -Id ([int]$pidVal) -ErrorAction SilentlyContinue)
}

$api = 'down'
try {
    $h = Invoke-RestMethod -Uri 'http://127.0.0.1:18080/api/v1/health' -TimeoutSec 8
    if ($h.status) { $api = $h.status }
} catch { $api = 'down' }

$ui = 'down'
try {
    $u = Invoke-WebRequest -Uri 'http://127.0.0.1:19088/' -UseBasicParsing -TimeoutSec 8
    if ($u.StatusCode -eq 200) { $ui = 'ok' }
} catch { $ui = 'down' }

$guest = 'unknown'
$jobScript = Join-Path $Root 'scripts\qemu-guest-job.ps1'
if (Test-Path $jobScript) {
    & $jobScript 2>&1 | Out-Null
    switch ($LASTEXITCODE) {
        0 { $guest = 'idle/done' }
        2 { $guest = 'core-api-rebuild running' }
        default { $guest = "job exit $LASTEXITCODE" }
    }
}

$cp = Read-JsonFile (Join-Path $EvDir 'vpp-checkpoint.json')
$green = Read-JsonFile (Join-Path $EvDir 'vpp-green.json')
$vpp = if ($green -and $green.status -eq 'GREEN' -and $green.full_coverage -eq $true) { 'GREEN' }
         elseif ($cp) { "$($cp.gates_pass_count)/$($cp.gates_total) PASS, next=$($cp.resume_from_gate)" }
         else { 'no checkpoint' }

$phase = if ($cycle) { $cycle.phase } else { 'n/a' }
$cycleSt = if ($cycle) { $cycle.status } else { 'n/a' }
$detail = if ($cycle -and $cycle.detail) { $cycle.detail } else { '' }

$lines = @(
    "cycle: phase=$phase status=$cycleSt pid=$pidVal alive=$cycleAlive",
    "stack: API=$api UI=$ui guest=$guest",
    "vpp: $vpp"
)
if ($detail) { $lines += "detail: $detail" }

$text = ($lines -join ' | ')

if ($Json) {
    @{
        at     = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss')
        text   = $text
        phase  = $phase
        api    = $api
        ui     = $ui
        guest  = $guest
        vpp    = $vpp
        alive  = $cycleAlive
    } | ConvertTo-Json -Compress
} else {
    Write-Output $text
}
