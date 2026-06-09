# Debug monitor: poll VM/bootstrap state → debug-baea72.log (session baea72)
param(
    [int]$IntervalSeconds = 30,
    [int]$MaxMinutes = 90,
    [switch]$Help
)

if ($Help) {
    Write-Host "Usage: .\scripts\qemu-debug-monitor.ps1 [-IntervalSeconds 30] [-MaxMinutes 90]"
    exit 0
}

$ErrorActionPreference = "SilentlyContinue"
$Root = Split-Path -Parent $PSScriptRoot
. (Join-Path $Root "deploy\qemu\lib\Write-KorusDebugLog.ps1")
$Plink = "${env:ProgramFiles}\PuTTY\plink.exe"
$RunDir = Join-Path $Root "deploy\qemu\run"
$SerialServer = Join-Path $RunDir "server-serial.log"
$SerialWeb = Join-Path $RunDir "web-serial.log"

function Get-Ed25519HostKey {
    param([string]$SerialPath)
    if (-not (Test-Path $SerialPath)) { return $null }
    $m = Select-String -Path $SerialPath -Pattern "256 SHA256:([A-Za-z0-9+/=]+)\s+root@.*\(ED25519\)" | Select-Object -Last 1
    if ($m) { return "ssh-ed25519 255 SHA256:$($m.Matches[0].Groups[1].Value)" }
    $cache = Join-Path $RunDir "ssh-hostkeys.ps1"
    if (Test-Path $cache) { . $cache; return $null }
    return $null
}

function Invoke-GuestProbe {
    param([string]$HostKey, [int]$Port, [string]$Role)
    if (-not $HostKey -or -not (Test-Path $Plink)) { return @{ ok = $false; detail = "no hostkey or plink" } }
    $cmd = @"
uptime | head -1
test -f /var/log/korus-bootstrap.log && tail -3 /var/log/korus-bootstrap.log || echo 'no bootstrap log'
"@
    $tmp = [IO.Path]::GetTempFileName()
    try {
        [IO.File]::WriteAllText($tmp, $cmd + "`n")
        $out = & $Plink -batch -hostkey $HostKey -pw korus -P $Port -m $tmp "korus@127.0.0.1" 2>&1
        if ($LASTEXITCODE -ne 0) { return @{ ok = $false; detail = ($out | Out-String).Trim() } }
        return @{ ok = $true; detail = ($out | Out-String).Trim() }
    } finally {
        Remove-Item $tmp -Force -ErrorAction SilentlyContinue
    }
}

function Test-HttpCode {
    param([string]$Url, [int]$TimeoutSec = 8)
    try {
        $c = curl.exe -sS -m $TimeoutSec -o NUL -w "%{http_code}" $Url 2>$null
        if ($c -match '^\d{3}$') { return [int]$c }
    } catch {}
    return 0
}

Write-KorusDebugLog -Location "qemu-debug-monitor.ps1" -Message "monitor started" -HypothesisId "ALL" -Data @{
    intervalSec = $IntervalSeconds; maxMin = $MaxMinutes
}

$deadline = (Get-Date).AddMinutes($MaxMinutes)
$tick = 0
while ((Get-Date) -lt $deadline) {
    $tick++
    $qemu = @(Get-Process qemu-system-x86_64 -ErrorAction SilentlyContinue)
    $apiCode = Test-HttpCode "http://127.0.0.1:18080/api/v1/health"
    $webCode = Test-HttpCode "http://127.0.0.1:19088/"

    $srvKey = Get-Ed25519HostKey $SerialServer
    $webKey = Get-Ed25519HostKey $SerialWeb
    $srvProbe = if ($srvKey) { Invoke-GuestProbe -HostKey $srvKey -Port 12221 -Role "server" } else { @{ ok = $false; detail = "server key pending" } }
    $webProbe = if ($webKey) { Invoke-GuestProbe -HostKey $webKey -Port 12222 -Role "web" } else { @{ ok = $false; detail = "web key pending" } }

    $bootstrapHint = ""
    if ($srvProbe.detail -match "i/o timeout|FAILED|ERROR") { $bootstrapHint = "server-fail" }
    elseif ($webProbe.detail -match "ERROR: server API|FAILED") { $bootstrapHint = "web-wait-fail" }
    elseif ($apiCode -eq 200 -and $webCode -eq 200) { $bootstrapHint = "ready" }

    Write-KorusDebugLog -Location "qemu-debug-monitor.ps1:tick" -Message "poll $tick" -HypothesisId "F" -Data @{
        tick           = $tick
        qemuCount      = $qemu.Count
        apiHttp        = $apiCode
        webHttp        = $webCode
        serverSsh      = [bool]$srvProbe.ok
        webSsh         = [bool]$webProbe.ok
        status         = $bootstrapHint
        serverTail     = if ($srvProbe.detail) { $srvProbe.detail.Substring(0, [Math]::Min(400, $srvProbe.detail.Length)) } else { "" }
        webTail        = if ($webProbe.detail) { $webProbe.detail.Substring(0, [Math]::Min(300, $webProbe.detail.Length)) } else { "" }
    }

    if ($bootstrapHint -eq "ready") {
        Write-KorusDebugLog -Location "qemu-debug-monitor.ps1" -Message "stand ready" -HypothesisId "ALL" -Data @{ tick = $tick }
        Write-Host "[monitor] READY api=$apiCode web=$webCode (tick $tick)" -ForegroundColor Green
        exit 0
    }
    if ($bootstrapHint -eq "server-fail" -and $tick -gt 3 -and $apiCode -eq 0) {
        Write-KorusDebugLog -Location "qemu-debug-monitor.ps1" -Message "server bootstrap likely failed" -HypothesisId "E" -Data @{ tick = $tick }
    }

    Start-Sleep -Seconds $IntervalSeconds
}

Write-KorusDebugLog -Location "qemu-debug-monitor.ps1" -Message "monitor timeout" -HypothesisId "ALL" -Data @{ tick = $tick }
Write-Host "[monitor] timeout after $MaxMinutes min" -ForegroundColor Yellow
exit 1
