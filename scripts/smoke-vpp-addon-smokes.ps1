#Requires -Version 5.1
# Mandatory smoke per addon on full regression stack (spec 030).
param(
    [string]$ApiBaseUrl = "http://127.0.0.1:18080",
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\scripts\smoke-vpp-addon-smokes.ps1 - one smoke chain per catalog addon (no skips)."
    exit 0
}

$Root = Split-Path -Parent $PSScriptRoot
$env:BASE_URL = $ApiBaseUrl
$env:SCIM_BEARER_TOKEN = "korus-scim-lab-demo"
$WebBaseUrl = "http://127.0.0.1:19088"

function Ensure-NatsTunnel {
    $port = 14222
    $tcp = Test-NetConnection -ComputerName 127.0.0.1 -Port $port -WarningAction SilentlyContinue -ErrorAction SilentlyContinue
    if ($tcp.TcpTestSucceeded) { return "nats://127.0.0.1:$port" }
    $plink = Join-Path $env:ProgramFiles "PuTTY\plink.exe"
    if (-not (Test-Path $plink)) { throw "PuTTY plink not found for NATS tunnel" }
    $runDir = Join-Path $Root "deploy\qemu\run"
    . (Join-Path $Root "deploy\qemu\lib\Update-KorusGuestRepo.ps1")
    $hostKey = Get-KorusEd25519HostKey -SerialPath (Join-Path $runDir "server-serial.log") -Role server -SshPort 12221
    if (-not $hostKey) { throw "server SSH host key not ready for NATS tunnel" }
    Write-Host "  starting NATS tunnel :$port -> server guest :4222..." -ForegroundColor DarkGray
    $argLine = "-batch -N -hostkey `"$hostKey`" -pw korus -P 12221 -L ${port}:127.0.0.1:4222 korus@127.0.0.1"
    $proc = Start-Process -FilePath $plink -ArgumentList $argLine -PassThru -WindowStyle Hidden
    Start-Sleep -Seconds 2
    if ($proc.HasExited) {
        throw "NATS tunnel plink exited early (code=$($proc.ExitCode))"
    }
    $deadline = (Get-Date).AddSeconds(20)
    while ((Get-Date) -lt $deadline) {
        $tcp2 = Test-NetConnection -ComputerName 127.0.0.1 -Port $port -WarningAction SilentlyContinue -ErrorAction SilentlyContinue
        if ($tcp2.TcpTestSucceeded) { return "nats://127.0.0.1:$port" }
        if ($proc.HasExited) { throw "NATS tunnel plink exited before :$port opened (code=$($proc.ExitCode))" }
        Start-Sleep -Milliseconds 500
    }
    throw "NATS tunnel :$port not open"
}

$runs = @(
    @{ addon = "addon-productivity"; scripts = @("scripts/smoke-phase5-messaging.ps1") },
    @{ addon = "addon-engage"; scripts = @("scripts/smoke-push-worker-qemu.ps1", "scripts/smoke-preview-worker-qemu.ps1") },
    @{ addon = "addon-search"; scripts = @("scripts/smoke-hotplug-indexer.ps1") },
    @{ addon = "addon-collaboration"; scripts = @("scripts/smoke-phase5-adr-scaffolds.ps1") },
    @{ addon = "addon-ai"; scripts = @("scripts/smoke-plugin-ai-triage.ps1") },
    @{ addon = "addon-live"; scripts = @("scripts/smoke-live-session.ps1", "scripts/smoke-turn-relay.ps1") },
    @{ addon = "addon-retention"; scripts = @("scripts/smoke-retention-worker-qemu.ps1") },
    @{ addon = "addon-archive"; scripts = @("scripts/smoke-ready.ps1") },
    @{ addon = "addon-deep-archive"; scripts = @("scripts/smoke-deep-archive-chunks.ps1") },
    @{ addon = "addon-export"; scripts = @("scripts/smoke-export-compliance-flow.ps1") },
    @{ addon = "addon-enterprise-auth"; scripts = @("scripts/smoke-ip-allowlist.ps1", "scripts/smoke-scim-lab-token.ps1") },
    @{ addon = "addon-e2ee"; scripts = @("scripts/smoke-openmls-migration.ps1") },
    @{ addon = "addon-bots"; scripts = @("scripts/smoke-bot-api.ps1", "scripts/smoke-bot-delivery-worker.ps1") },
    @{ addon = "addon-integrations"; scripts = @("scripts/smoke-integrations-gate.ps1") },
    @{ addon = "addon-federation"; scripts = @("scripts/smoke-federation-trust.ps1", "scripts/smoke-federation-cross-org.ps1") },
    @{ addon = "addon-dlp"; scripts = @("scripts/smoke-dlp-mock.ps1") },
    @{ addon = "addon-migration-import"; scripts = @("scripts/smoke-migration-import.ps1") },
    @{ addon = "addon-directory"; scripts = @("scripts/smoke-ldap-auth.sh") }
)

foreach ($entry in $runs) {
    Write-Host ""
    Write-Host "=== addon smoke: $($entry.addon) ===" -ForegroundColor Cyan
    foreach ($rel in $entry.scripts) {
        $path = Join-Path $Root ($rel -replace '/', '\')
        if (-not (Test-Path $path)) { throw "missing smoke for $($entry.addon): $rel" }
        Write-Host "  -> $rel" -ForegroundColor DarkGray
        $scriptExit = 0
        try {
            if ($rel -like "*.sh") {
                & bash $path
                $scriptExit = $LASTEXITCODE
            } elseif ($rel -match "hotplug-indexer") {
                $natsUrl = Ensure-NatsTunnel
                & $path -NatsUrl $natsUrl
                $scriptExit = $LASTEXITCODE
            } elseif ($rel -match "turn-relay") {
                & $path -WebBaseUrl $WebBaseUrl
                $scriptExit = $LASTEXITCODE
            } elseif ($rel -match "ip-allowlist") {
                & $path -BaseUrl $ApiBaseUrl -RequireEnforce
                $scriptExit = $LASTEXITCODE
            } elseif ($rel -match "scim") {
                & $path -BaseUrl $ApiBaseUrl -Mandatory
                $scriptExit = $LASTEXITCODE
            } elseif ($rel -match "smoke-ready") {
                & $path -BaseUrl $ApiBaseUrl
                $scriptExit = $LASTEXITCODE
            } elseif ($rel -match "deep-archive-chunks") {
                & $path -BaseUrl $ApiBaseUrl -UseSshMinioTunnel
                $scriptExit = $LASTEXITCODE
            } elseif ($rel -match "export-compliance-flow") {
                & $path -BaseUrl $ApiBaseUrl
                $scriptExit = $LASTEXITCODE
            } elseif ($rel -match "phase5-messaging|federation|migration-import|bot-api|live-session|openmls") {
                & $path -BaseUrl $ApiBaseUrl
                $scriptExit = $LASTEXITCODE
            } else {
                & $path
                $scriptExit = $LASTEXITCODE
            }
        } catch {
            $scriptExit = 1
            $env:VPP_LAST_GATE_DETAIL = "$($entry.addon) $rel : $($_.Exception.Message)"
            Write-Host "[FAIL] $($env:VPP_LAST_GATE_DETAIL)" -ForegroundColor Red
        }
        if (-not $?) { $scriptExit = if ($scriptExit) { $scriptExit } else { 1 } }
        if ($scriptExit -ne 0) {
            $failMsg = "$($entry.addon) $rel exit $scriptExit"
            $env:VPP_LAST_GATE_DETAIL = $failMsg
            Write-Host "[FAIL] $failMsg" -ForegroundColor Red
            exit $scriptExit
        }
    }
    Write-Host "[OK] $($entry.addon)" -ForegroundColor Green
}

Write-Host ""
Write-Host "[OK] all addon smokes ($($runs.Count) addons)" -ForegroundColor Green
