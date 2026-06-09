param(
    [switch]$InstallQemuOnly,
    [switch]$SkipQemuInstall,
    [switch]$KeepDisks,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "lib\Write-KorusDebugLog.ps1")
Write-KorusDebugLog -Location "qemu-up.ps1:start" -Message "qemu-up begin" -HypothesisId "ALL" -Data @{
    KeepDisks = [bool]$KeepDisks; SkipQemuInstall = [bool]$SkipQemuInstall
}
if ($Help) {
    Write-Host "Usage: .\deploy\qemu\qemu-up.ps1 [-InstallQemuOnly] [-SkipQemuInstall] [-KeepDisks]"
    exit 0
}

$lib = Join-Path $PSScriptRoot "lib"
$runDir = Join-Path $PSScriptRoot "run"
. (Join-Path $lib "Resolve-Qemu.ps1")

if (-not $SkipQemuInstall -and -not (Resolve-KorusQemu)) {
    & (Join-Path $PSScriptRoot "install-qemu.ps1")
}
if ($InstallQemuOnly) { exit 0 }
if (-not (Resolve-KorusQemu)) {
    Write-Error "QEMU required. Run: .\deploy\qemu\install-qemu.ps1"
}

if (-not $KeepDisks) {
    . (Join-Path $lib "Reset-KorusVmDisks.ps1")
    Write-KorusDebugLog -Location "qemu-up.ps1:disks" -Message "resetting VM disks" -HypothesisId "A"
    Reset-KorusVmDisks
    Remove-Item (Join-Path $runDir "ssh-hostkeys.ps1") -Force -ErrorAction SilentlyContinue
    Write-KorusDebugLog -Location "qemu-up.ps1:disks" -Message "disks reset, ssh cache cleared" -HypothesisId "C"
}

. (Join-Path $lib "Get-KorusLanHostIp.ps1")
. (Join-Path $lib "Start-KorusRepoHttp.ps1")
$lanIp = Write-KorusQemuLanHostInfo -RunDir $runDir
$repoHttp = Start-KorusRepoHttp
Write-KorusDebugLog -Location "qemu-up.ps1:repo-http" -Message "repo HTTP started" -HypothesisId "A" -Data @{ lanIp = $lanIp; repoHttp = ($repoHttp | Out-String).Trim() }

. (Join-Path $lib "Start-KorusVm.ps1")
Write-KorusDebugLog -Location "qemu-up.ps1:vm" -Message "starting server VM" -HypothesisId "B"
Start-KorusQemuVm -Role server | Out-Null
Write-Host "Waiting for server SSH (cloud-init) before web VM..." -ForegroundColor DarkGray
$sshReady = $false
for ($i = 1; $i -le 60; $i++) {
    $tcp = Test-NetConnection -ComputerName 127.0.0.1 -Port 12221 -WarningAction SilentlyContinue -ErrorAction SilentlyContinue
    if ($tcp.TcpTestSucceeded) {
        $sshReady = $true
        Write-Host "  SSH on :12221 ready (${i}0s)" -ForegroundColor DarkGray
        Write-KorusDebugLog -Location "qemu-up.ps1:ssh" -Message "server SSH ready" -HypothesisId "C" -Data @{ waitSec = ($i * 10) }
        break
    }
    Start-Sleep -Seconds 10
}
if (-not $sshReady) {
    Write-Warning "Server SSH not ready after 10 min; starting web VM anyway"
    Write-KorusDebugLog -Location "qemu-up.ps1:ssh" -Message "server SSH timeout" -HypothesisId "C" -Data @{ waitSec = 600 }
}
Write-KorusDebugLog -Location "qemu-up.ps1:vm" -Message "starting web VM" -HypothesisId "D"
Start-KorusQemuVm -Role web | Out-Null

Write-KorusDebugLog -Location "qemu-up.ps1:end" -Message "both VMs started, bootstrap async on guests" -HypothesisId "ALL" -Data @{
    note = "cloud-init runs KORUS_BUILD=1 ansible in background; monitor /var/log/korus-bootstrap.log"
}

Write-Host ""
Write-Host "[OK] QEMU VMs started (server first, then web)" -ForegroundColor Green
Write-Host "  API:  http://127.0.0.1:18080/api/v1/health"
Write-Host "  UI:   http://127.0.0.1:19088/"
Write-Host "  LAN ($lanIp):"
Write-Host "    API  http://${lanIp}:18080/api/v1/health"
Write-Host "    UI   http://${lanIp}:19088/"
Write-Host "  SSH:  ssh korus@127.0.0.1 -p 12221 / -p 12222  (pass: korus)"
Write-Host "  Ports bind 0.0.0.0 on host; allow 18080,18082,19088 in Windows Firewall for LAN clients."
Write-Host "  Logs: deploy\qemu\run\*-serial.log  (guest: /var/log/korus-bootstrap.log)"
Write-Host "  Stop: .\scripts\qemu-down.ps1"
