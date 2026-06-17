# Start korus-integrations VM only (spec 014). Does NOT restart server/web or wipe disks.
param(
    [switch]$Help,
    [switch]$Force,
    [switch]$ForceTcg,
    [int]$SshWaitSec = 600
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"
Usage: .\scripts\qemu-integrations-up.ps1 [-SshWaitSec 600] [-Force]

Starts QEMU guest korus-integrations (192.168.76.30) with host forwards:
  18190 gateway, 18088 echo-php, 18091 connector, 18093-18097 bridges, 12223 SSH.

-Force: stop running korus-integrations and start again (refreshes host port forwards).

Requires server/web already up (repo HTTP :18890 for cloud-init on first boot).
Does not run qemu-down.
"@
    exit 0
}

$root = Split-Path -Parent $PSScriptRoot
. (Join-Path $root "deploy\qemu\config.ps1")
. (Join-Path $root "deploy\qemu\lib\Get-KorusQemuVmMemory.ps1")
. (Join-Path $root "deploy\qemu\lib\Start-KorusRepoHttp.ps1")
. (Join-Path $root "deploy\qemu\lib\Start-KorusVm.ps1")

if (-not $ForceTcg) {
    Remove-Item Env:KORUS_QEMU_FORCE_TCG -ErrorAction SilentlyContinue
} elseif ($env:KORUS_QEMU_FORCE_TCG -ne "1") {
    $env:KORUS_QEMU_FORCE_TCG = "1"
    Write-Host "[WARN] -ForceTcg: using TCG (slow). Prefer WHPX on this host." -ForegroundColor Yellow
}
if ($env:KORUS_QEMU_FORCE_TCG -eq "1") {
    Write-Host "[WARN] KORUS_QEMU_FORCE_TCG=1 - integrations build will be very slow. Remove env var for WHPX." -ForegroundColor Yellow
}

$plannedMem = Get-KorusQemuVmMemoryMb -Role integrations
if (-not (Test-KorusQemuIntegrationsMemoryFeasible -MemoryMb $plannedMem)) {
    Write-Host "[FAIL] Host RAM insufficient for integrations ${plannedMem}MB with current server/web sizing." -ForegroundColor Red
    Write-Host "  Option A (recommended): restart full 3-VM stack with lighter RAM:" -ForegroundColor Yellow
    Write-Host "    .\scripts\qemu-down.ps1" -ForegroundColor DarkGray
    Write-Host "    `$env:KORUS_QEMU_THREE_VM='1'; .\scripts\qemu-up.ps1 -KeepDisks -WithIntegrations" -ForegroundColor DarkGray
    Write-Host "  Option B: stop server or web temporarily, then re-run this script." -ForegroundColor Yellow
    exit 1
}

$existing = Get-CimInstance Win32_Process -Filter "name='qemu-system-x86_64.exe'" -ErrorAction SilentlyContinue |
    Where-Object { $_.CommandLine -match "korus-integrations" }
if ($existing -and -not $Force) {
    Write-Host "[OK] korus-integrations already running (pid $($existing.ProcessId))" -ForegroundColor Green
    Write-Host "Use -Force to restart VM and refresh host forwards (e.g. :18097 :18190)" -ForegroundColor DarkGray
    exit 0
}
if ($existing -and $Force) {
    foreach ($p in $existing) {
        Write-Host "Stopping korus-integrations (pid $($p.ProcessId))..." -ForegroundColor Yellow
        Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
    }
    Start-Sleep -Seconds 3
}

Start-KorusRepoHttp | Out-Null
Write-Host "Starting korus-integrations VM..." -ForegroundColor Cyan
Start-KorusQemuVm -Role integrations | Out-Null

$ready = $false
for ($i = 1; $i -le [Math]::Max(1, [int]($SshWaitSec / 10)); $i++) {
    $tcp = Test-NetConnection -ComputerName 127.0.0.1 -Port 12223 -WarningAction SilentlyContinue -ErrorAction SilentlyContinue
    if ($tcp.TcpTestSucceeded) {
        $ready = $true
        Write-Host "[OK] SSH :12223 ready (~$($i * 10)s)" -ForegroundColor Green
        break
    }
    Start-Sleep -Seconds 10
}
if (-not $ready) {
    Write-Warning "SSH :12223 not ready after ${SshWaitSec}s; check deploy/qemu/run/integrations-serial.log"
    exit 1
}

Write-Host "Bootstrap: guest /var/log/korus-bootstrap.log (docker compose build may take 15-30 min first run)"
Write-Host "Probe:    Invoke-WebRequest http://127.0.0.1:18190/health"
Write-Host "Smokes:   .\scripts\smoke-plugin-qemu.ps1 -WaitSec 1800"
