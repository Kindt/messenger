#Requires -Version 5.1
# Light fan-out burst on server guest (VPP fortress).
param(
    [int]$Burst = 25,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\scripts\vpp\Invoke-VppLoadFanoutLight.ps1 [-Burst 25]"
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
$fanout = "cd /mnt/korus && BURST=$Burst BASE_URL=http://127.0.0.1:8080 PIPELINE_METRICS_URL=http://127.0.0.1:9197/metrics bash scripts/load-fanout-synthetic.sh"
Invoke-PlinkShell -Plink $Plink -HostKey $hk -Port 12221 -Script $fanout
exit $LASTEXITCODE
