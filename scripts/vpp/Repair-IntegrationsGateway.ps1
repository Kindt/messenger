# Guest: docker compose up + wait for :8090/:8088 health (spec 014 / VPP lab).
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
. (Join-Path $root "deploy\qemu\lib\Update-KorusGuestRepo.ps1")

$plink = Join-Path $env:ProgramFiles "PuTTY\plink.exe"
$runDir = Join-Path $root "deploy\qemu\run"
if (-not (Test-Path $plink)) { throw "PuTTY plink not found: $plink" }

$tcp = Test-NetConnection -ComputerName 127.0.0.1 -Port 12223 -WarningAction SilentlyContinue -ErrorAction SilentlyContinue
if (-not $tcp.TcpTestSucceeded) {
    Write-Host "[FAIL] integrations SSH :12223 down - run .\scripts\qemu-integrations-up.ps1" -ForegroundColor Red
    exit 2
}

$hostKey = Get-KorusEd25519HostKey -SerialPath (Join-Path $runDir "integrations-serial.log") -Role integrations -SshPort 12223
if (-not $hostKey) {
    Write-Host "[WARN] integrations SSH host key not ready (skip plink repair)" -ForegroundColor Yellow
    exit 3
}

$guestScript = @'
set -e
cd /mnt/korus/integrations
sudo docker compose -f docker-compose.integrations.yml up -d --remove-orphans 2>&1
for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20; do
  if curl -sf http://127.0.0.1:8090/health >/dev/null && curl -sf http://127.0.0.1:8088/health >/dev/null; then
    echo integrations-stack-ok
    exit 0
  fi
  sleep 6
done
sudo docker compose -f docker-compose.integrations.yml ps
exit 1
'@

Write-Host "Repair: integrations compose up + gateway wait..." -ForegroundColor Cyan
$out = Invoke-PlinkShell -Plink $plink -HostKey $hostKey -Port 12223 -Script $guestScript
Write-Host $out
if ($out -notmatch "integrations-stack-ok") {
    Write-Host "[FAIL] gateway or echo-php health not ready on guest" -ForegroundColor Red
    exit 1
}

try {
    Invoke-WebRequest -Uri "http://127.0.0.1:18190/health" -UseBasicParsing -TimeoutSec 10 | Out-Null
    Invoke-WebRequest -Uri "http://127.0.0.1:18088/health" -UseBasicParsing -TimeoutSec 10 | Out-Null
    Write-Host "[OK] host forwards :18190 and :18088 health" -ForegroundColor Green
} catch {
    Write-Host "[FAIL] host health after guest repair: $_" -ForegroundColor Red
    exit 1
}
