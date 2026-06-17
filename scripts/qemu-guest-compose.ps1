# Per-service docker compose on QEMU guest (spec 014 ops: hot-plug one bridge).
param(
    [Parameter(Mandatory)]
    [ValidateSet("integrations", "server")]
    [string]$Guest,
    [Parameter(Mandatory)]
    [ValidateSet("build", "up", "down", "restart", "ps")]
    [string]$Action,
    [Parameter(Mandatory)]
    [string[]]$Services,
    [switch]$ForceLock,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"
Usage: .\scripts\qemu-guest-compose.ps1 -Guest integrations -Action build -Services onec-bridge
       .\scripts\qemu-guest-compose.ps1 -Guest integrations -Action up -Services mock-apis,connector-runtime,integrations-gateway

Ops primitive: one compose service at a time (production hot-plug path on integrations VM).
Uses guest-task lock; -ForceLock to override stale/running lock.
"@
    exit 0
}

$root = Split-Path -Parent $PSScriptRoot
$runDir = Join-Path $root "deploy\qemu\run"
$plink = Join-Path $env:ProgramFiles "PuTTY\plink.exe"
. (Join-Path $root "deploy\qemu\lib\Korus-QemuGuestTaskLock.ps1")
. (Join-Path $root "deploy\qemu\lib\Update-KorusGuestRepo.ps1")

if (-not (Test-Path $plink)) { throw "PuTTY plink not found: $plink" }

$sshPort = if ($Guest -eq "integrations") { 12223 } else { 12221 }
$serial = if ($Guest -eq "integrations") { "integrations-serial.log" } else { "server-serial.log" }
$composeFile = if ($Guest -eq "integrations") {
    "/mnt/korus/integrations/docker-compose.integrations.yml"
} else {
    "/mnt/korus/docker/docker-compose.full-server.yml"
}

$tcp = Test-NetConnection -ComputerName 127.0.0.1 -Port $sshPort -WarningAction SilentlyContinue -ErrorAction SilentlyContinue
if (-not $tcp.TcpTestSucceeded) {
    Write-Host "SSH :$sshPort down for guest $Guest" -ForegroundColor Red
    exit 2
}

$hostKey = Get-KorusEd25519HostKey -SerialPath (Join-Path $runDir $serial) -Role $Guest -SshPort $sshPort
if (-not $hostKey) { throw "SSH host key not ready ($serial)" }

$svcList = ($Services | ForEach-Object { $_.Trim() } | Where-Object { $_ }) -join " "
if (-not $svcList) { throw "Services required" }

$composeCmd = switch ($Action) {
    "build" { "sudo docker compose -f $composeFile build $svcList 2>&1" }
    "up" { "sudo docker compose -f $composeFile up -d $svcList 2>&1" }
    "down" { "sudo docker compose -f $composeFile stop $svcList 2>&1" }
    "restart" { "sudo docker compose -f $composeFile restart $svcList 2>&1" }
    "ps" { "sudo docker compose -f $composeFile ps $svcList 2>&1" }
}
$script = "set -e`ncd /mnt/korus`n$composeCmd`necho guest-compose-ok"

$taskName = "guest-compose-$Guest-$Action-$($Services -join '-')"
Write-Host "$Action on $Guest : $svcList" -ForegroundColor Cyan

Invoke-KorusGuestTaskLocked -RunDir $runDir -Guest $Guest -TaskName $taskName -ForceLock:$ForceLock -Action {
    $out = Invoke-PlinkShell -Plink $plink -HostKey $hostKey -Port $sshPort -Script $script
    Write-Host $out
    if ($out -notmatch "guest-compose-ok") {
        throw "guest compose $Action failed"
    }
}

Write-Host "[OK] $Action $svcList on $Guest" -ForegroundColor Green
