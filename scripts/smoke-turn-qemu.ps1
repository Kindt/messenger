# TURN/coturn smoke for QEMU: guest TCP probe + optional host/web ICE check.
param(
    [int]$WebSshPort = 12222,
    [string]$TurnHost = "127.0.0.1",
    [int]$TurnPort = 3478,
    [string]$WebBaseUrl = "http://127.0.0.1:19088",
    [switch]$GuestOnly,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"
Usage: .\scripts\smoke-turn-qemu.ps1 [-GuestOnly] [-WebBaseUrl http://127.0.0.1:19088]

GuestOnly: SSH web guest only (coturn TCP :3478 inside VM).
Default: guest probe + host TCP :3478 (needs web VM hostfwd) + web-client-env.js turn ICE.

Enable TURN on web guest: korus_web_turn in deploy/ansible/inventory/qemu/group_vars/all.yml
then .\scripts\qemu-dev-mode.ps1 -Mode sync-web (restart web VM if hostfwd :3478 was added).
"@
    exit 0
}

$Root = Split-Path -Parent $PSScriptRoot
$RunDir = Join-Path $Root "deploy\qemu\run"
$plink = Join-Path $env:ProgramFiles "PuTTY\plink.exe"
. (Join-Path $Root "deploy\qemu\lib\Update-KorusGuestRepo.ps1")

function Fail([string]$msg) {
    Write-Host "[FAIL] $msg" -ForegroundColor Red
    exit 1
}

if (-not (Test-Path $plink)) { Fail "plink not found: $plink" }

$hostKey = Get-KorusEd25519HostKey -SerialPath (Join-Path $RunDir "web-serial.log") -Role web -SshPort $WebSshPort
if (-not $hostKey) { Fail "web SSH host key not ready" }

$guestProbe = "if ! docker ps --format '{{.Names}}' | grep -q coturn; then echo '[FAIL] coturn container not running (enable korus_web_turn + sync-web)'; exit 1; fi; timeout 2 bash -c 'echo > /dev/tcp/127.0.0.1/3478' >/dev/null 2>&1 || { echo '[FAIL] guest TCP 3478 closed'; exit 1; }; echo '[OK] coturn TCP 127.0.0.1:3478 inside web guest'"
$out = & $plink -batch -hostkey $hostKey -pw korus -P $WebSshPort "korus@127.0.0.1" $guestProbe 2>&1 | ForEach-Object { "$_" }
Write-Host ($out -join "`n")
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

if ($GuestOnly) {
    Write-Host "[OK] TURN QEMU guest smoke" -ForegroundColor Green
    exit 0
}

$tcp = Test-NetConnection -ComputerName $TurnHost -Port $TurnPort -WarningAction SilentlyContinue
if (-not $tcp.TcpTestSucceeded) {
    Fail "host TURN TCP ${TurnHost}:${TurnPort} not reachable (restart web VM after hostfwd :3478)"
}
Write-Host "[OK] TURN TCP ${TurnHost}:${TurnPort} from host"

if ($WebBaseUrl) {
    $envJs = Invoke-WebRequest -Uri "$($WebBaseUrl.TrimEnd('/'))/web-client-env.js" -UseBasicParsing
    if ($envJs.Content -notmatch "turn:") {
        Fail "web-client-env.js has no turn: ICE entry (re-run sync-web with korus_web_turn)"
    }
    Write-Host "[OK] web-client-env.js contains turn ICE servers"
}

Write-Host "[OK] TURN QEMU smoke" -ForegroundColor Green
