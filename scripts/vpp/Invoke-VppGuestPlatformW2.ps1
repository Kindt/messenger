#Requires -Version 5.1
# Guest platform W2 smokes with export-replay purge (VPP fortress).
param(
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\scripts\vpp\Invoke-VppGuestPlatformW2.ps1"
    exit 0
}

$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$RunDir = Join-Path $Root "deploy\qemu\run"
$Plink = Join-Path ${env:ProgramFiles} "PuTTY\plink.exe"
if (-not (Test-Path $Plink)) {
    Write-Host "[FAIL] plink not found" -ForegroundColor Red
    exit 1
}

. (Join-Path $Root "deploy\qemu\lib\Update-KorusGuestRepo.ps1")
$hk = Get-KorusEd25519HostKey -SerialPath (Join-Path $RunDir "server-serial.log") -Role server -SshPort 12221
if (-not $hk) {
    Write-Host "[FAIL] server SSH host key not ready" -ForegroundColor Red
    exit 1
}

Update-KorusGuestRepo -Role server -SshPort 12221 -HostKey $hk -Plink $Plink | Out-Null
$remote = @'
cd /mnt/korus
export KORUS_RUN_EXPORT_PURGE_SMOKE=1
export KORUS_API_URL=http://127.0.0.1:8080
sed -i 's/\r$//' scripts/guest-smoke-platform-w2.sh scripts/verify-nats-queue-group.sh
bash scripts/guest-smoke-platform-w2.sh
'@
Invoke-PlinkShell -Plink $Plink -HostKey $hk -Port 12221 -Script $remote
exit $LASTEXITCODE
