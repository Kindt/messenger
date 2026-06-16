# Forward host :17880 -> server guest LiveKit :7880 (no qemu-down; safe with parallel agents).
param(
    [int]$LocalPort = 17880,
    [int]$GuestPort = 7880,
    [int]$SshPort = 12221,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"
Usage: .\scripts\livekit-host-tunnel.ps1

Starts plink SSH tunnel: 127.0.0.1:$LocalPort -> guest 127.0.0.1:$GuestPort
Matches LIVEKIT_URL=ws://127.0.0.1:$LocalPort in docker/.env.livekit (QEMU L2).

Run in a dedicated terminal; stop with Ctrl+C. Does not restart VMs.
Prereq: server VM SSH on :$SshPort, livekit container on guest :$GuestPort.
"@
    exit 0
}

$Root = Split-Path -Parent $PSScriptRoot
$RunDir = Join-Path $Root "deploy\qemu\run"
$Plink = "${env:ProgramFiles}\PuTTY\plink.exe"
if (-not (Test-Path $Plink)) { throw "PuTTY plink not found: $Plink" }

. (Join-Path $Root "deploy\qemu\lib\Update-KorusGuestRepo.ps1")
$hk = Get-KorusEd25519HostKey -SerialPath (Join-Path $RunDir "server-serial.log") -Role server -SshPort $SshPort
if (-not $hk) { throw "server SSH host key not ready (QEMU up?)" }

Write-Host "LiveKit tunnel 127.0.0.1:$LocalPort -> guest:$GuestPort (Ctrl+C to stop)" -ForegroundColor Cyan
& $Plink -batch -hostkey $hk -pw korus -P $SshPort -L "${LocalPort}:127.0.0.1:${GuestPort}" -N korus@127.0.0.1
