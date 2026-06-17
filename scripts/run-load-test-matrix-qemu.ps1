# Load test matrix on QEMU server guest (ROADMAP SS8 / spec 007 T604 engineering).
param(
    [ValidateSet("health", "rest", "all")]
    [string]$Scenario = "all",
    [int]$DurationSec = 30,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"
Usage: .\scripts\run-load-test-matrix-qemu.ps1 [-Scenario health|rest|all] [-DurationSec 30]

Runs k6 scripts/load/pilot-*.js inside korus-server guest when k6 is installed.
Writes deploy/qemu/run/k6-pilot-baseline.json on host when guest returns JSON.
Requires QEMU server SSH :12221.
"@
    exit 0
}

$root = Split-Path -Parent $PSScriptRoot
$outDir = Join-Path $root "deploy\qemu\run"
$outFile = Join-Path $outDir "k6-pilot-baseline.json"
. (Join-Path $root "deploy\qemu\run\ssh-hostkeys.ps1")
$hostKey = $script:KorusQemuSshHostKeys['server']
$plink = Join-Path $env:ProgramFiles "PuTTY\plink.exe"
if (-not (Test-Path $plink)) { throw "plink not found" }

$tcp = Test-NetConnection 127.0.0.1 -Port 12221 -WarningAction SilentlyContinue
if (-not $tcp.TcpTestSucceeded) {
    Write-Host "[FAIL] server SSH :12221 down" -ForegroundColor Red
    exit 2
}

$scenarios = @()
if ($Scenario -eq "all" -or $Scenario -eq "health") { $scenarios += "pilot-health.js" }
if ($Scenario -eq "all" -or $Scenario -eq "rest") { $scenarios += "pilot-rest.js" }

$results = @()
foreach ($script in $scenarios) {
    Write-Host "Guest k6: $script (${DurationSec}s)..." -ForegroundColor Cyan
    $guestCmd = @"
set -e
cd /mnt/korus
if ! command -v k6 >/dev/null 2>&1; then
  echo '[SKIP] k6 not installed on guest — apt install or use scripts/load/pilot-health.js from host CI'
  exit 0
fi
export KORUS_BASE_URL=http://127.0.0.1:8080
k6 run --duration ${DurationSec}s --vus 6 scripts/load/$script 2>&1 | tail -n 20
"@
    $log = Invoke-PlinkShell -Plink $plink -HostKey $hostKey -Port 12221 -Script $guestCmd
    Write-Host $log
    $results += @{ script = $script; log_tail = ($log -split "`n" | Select-Object -Last 5) -join "`n" }
}

$payload = @{
    generated_at_utc = (Get-Date).ToUniversalTime().ToString("o")
    environment = "qemu-server-guest"
    scenarios = $results
    note = "Engineering baseline; formal 20% peak soak on stage after ops sign-off"
}
$payload | ConvertTo-Json -Depth 6 | Set-Content -Path $outFile -Encoding UTF8
Write-Host "[OK] wrote $outFile" -ForegroundColor Green
