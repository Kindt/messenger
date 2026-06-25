# Short SSH to QEMU server guest (port 12221). Requires deploy/qemu/run host keys.
$script:KorusQemuGuestLibRoot = Split-Path -Parent (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
$guestLib = Join-Path $script:KorusQemuGuestLibRoot "deploy\qemu\lib\Update-KorusGuestRepo.ps1"
if (Test-Path $guestLib) {
    . $guestLib
}

function Get-QemuRepoRoot {
    return $script:KorusQemuGuestLibRoot
}

function Get-QemuServerGuestSession {
    if (-not (Test-Path $guestLib)) {
        throw "QEMU lib missing: $guestLib (start VMs with scripts/qemu-up.ps1)"
    }
    $Root = $script:KorusQemuGuestLibRoot
    $RunDir = Join-Path $Root "deploy\qemu\run"
    $Plink = "${env:ProgramFiles}\PuTTY\plink.exe"
    if (-not (Test-Path $Plink)) { throw "PuTTY plink not found at $Plink" }
    $serial = Join-Path $RunDir "server-serial.log"
    $hk = Get-KorusEd25519HostKey -SerialPath $serial -Role "server" -SshPort 12221
    if (-not $hk) { throw "Server SSH host key not ready (see $serial)" }
    return @{
        Root    = $Root
        Plink   = $Plink
        HostKey = $hk
        Port    = 12221
    }
}

function Invoke-QemuServerGuest {
    param([string]$Script)
    $s = Get-QemuServerGuestSession
    Invoke-PlinkShell -Plink $s.Plink -HostKey $s.HostKey -Port $s.Port -Script $Script
}
