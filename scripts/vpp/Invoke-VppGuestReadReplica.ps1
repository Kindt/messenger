#Requires -Version 5.1
param([switch]$Help)
$ErrorActionPreference = "Stop"
if ($Help) { Write-Host "Usage: Invoke-VppGuestReadReplica.ps1"; exit 0 }
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$RunDir = Join-Path $Root "deploy\qemu\run"
$Plink = Join-Path ${env:ProgramFiles} "PuTTY\plink.exe"
if (-not (Test-Path $Plink)) { Write-Host "[FAIL] plink not found"; exit 1 }
. (Join-Path $Root "deploy\qemu\lib\Update-KorusGuestRepo.ps1")
$hk = Get-KorusEd25519HostKey -SerialPath (Join-Path $RunDir "server-serial.log") -Role server -SshPort 12221
if (-not $hk) { Write-Host "[FAIL] server SSH host key"; exit 1 }
Update-KorusGuestRepo -Role server -SshPort 12221 -HostKey $hk -Plink $Plink | Out-Null
Invoke-PlinkShell -Plink $Plink -HostKey $hk -Port 12221 -Script "cd /mnt/korus && sed -i 's/\r$//' scripts/smoke-read-replica-env.sh && bash scripts/smoke-read-replica-env.sh"
exit $LASTEXITCODE
