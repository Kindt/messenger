# Smoke: DLP mock bridge returns block for sensitive text (spec 022 T02201)
param(
    [switch]$SkipIfUnreachable
)

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path

if ($env:DLP_MOCK_URL) {
    $base = $env:DLP_MOCK_URL.TrimEnd('/')
} else {
    $base = $null
    foreach ($candidate in @("http://127.0.0.1:8098", "http://127.0.0.1:18098")) {
        try {
            Invoke-WebRequest -Uri "$candidate/health" -UseBasicParsing -TimeoutSec 3 | Out-Null
            $base = $candidate
            break
        } catch { }
    }
    if (-not $base) {
        if ($SkipIfUnreachable) {
            $tcp = Test-NetConnection -ComputerName 127.0.0.1 -Port 12223 -WarningAction SilentlyContinue -ErrorAction SilentlyContinue
            if (-not $tcp.TcpTestSucceeded) {
                Write-Host "[SKIP] dlp-mock not reachable (integrations VM down; run qemu-integrations-up.ps1)"
                exit 0
            }
        }
        . (Join-Path $scriptDir "lib\Ensure-DlpMockTunnel.ps1")
        $base = Ensure-DlpMockTunnel
    }
}

try {
    Invoke-WebRequest -Uri "$base/health" -UseBasicParsing -TimeoutSec 10 | Out-Null
} catch {
    if ($SkipIfUnreachable) {
        Write-Host "[SKIP] dlp-mock not reachable at $base (integrations VM / docker-compose.integrations.yml)"
        exit 0
    }
    throw
}

$body = @{
    event_id = [guid]::NewGuid().ToString()
    type     = "message.send"
    text     = "password leak"
} | ConvertTo-Json
$r = Invoke-RestMethod -Uri "$base/v1/plugin/handle" -Method Post -Body $body -ContentType "application/json"
if ($r.dlp_verdict -ne "block") {
    Write-Error "expected block, got $($r.dlp_verdict)"
}
Write-Host "DLP mock smoke OK: block verdict"
