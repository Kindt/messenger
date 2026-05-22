param(
    [switch]$InstallQemuOnly,
    [switch]$SkipQemuInstall,
    [switch]$KeepDisks,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\deploy\qemu\qemu-up.ps1 [-InstallQemuOnly] [-SkipQemuInstall] [-KeepDisks]"
    exit 0
}

$lib = Join-Path $PSScriptRoot "lib"
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
    Reset-KorusVmDisks
}

. (Join-Path $lib "Get-KorusLanHostIp.ps1")
. (Join-Path $lib "Start-KorusRepoHttp.ps1")
$runDir = Join-Path $PSScriptRoot "run"
$lanIp = Write-KorusQemuLanHostInfo -RunDir $runDir
Start-KorusRepoHttp | Out-Null

. (Join-Path $lib "Start-KorusVm.ps1")
Start-KorusQemuVm -Role server | Out-Null
Write-Host "Waiting for server SSH (cloud-init) before web VM..." -ForegroundColor DarkGray
$sshReady = $false
for ($i = 1; $i -le 60; $i++) {
    $tcp = Test-NetConnection -ComputerName 127.0.0.1 -Port 12221 -WarningAction SilentlyContinue -ErrorAction SilentlyContinue
    if ($tcp.TcpTestSucceeded) {
        $sshReady = $true
        Write-Host "  SSH on :12221 ready (${i}0s)" -ForegroundColor DarkGray
        break
    }
    Start-Sleep -Seconds 10
}
if (-not $sshReady) {
    Write-Warning "Server SSH not ready after 10 min; starting web VM anyway"
}
Start-KorusQemuVm -Role web | Out-Null

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
