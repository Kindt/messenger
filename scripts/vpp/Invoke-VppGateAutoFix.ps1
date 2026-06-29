# Known lab auto-repairs before re-running a failed gate (QEMU VPP).
param(
    [Parameter(Mandatory)][string]$GateKey,
    [switch]$Help
)

$ErrorActionPreference = 'Continue'
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if ($Help) {
    Write-Host 'Usage: Invoke-VppGateAutoFix.ps1 -GateKey integrations_gate'
    exit 0
}

$ran = $false

function Run-Fix([string]$Name, [string]$Script, [string[]]$Args = @()) {
    Write-Host "  [auto-fix] $Name ..." -ForegroundColor Yellow
    if (-not (Test-Path $Script)) { return $false }
    & $Script @Args
    if ($LASTEXITCODE -ne 0) { Write-Host "  [auto-fix] $Name exit $LASTEXITCODE" -ForegroundColor DarkYellow; return $false }
    $script:ran = $true
    return $true
}

$integrationsGates = @(
    'integrations_gate', 'integrations_preflight_online', 'integrations_vitrine',
    'plugin_lifecycle', 'plugin_qemu', 'plugin_echo_php', 'plugin_exchange',
    'plugin_storage', 'plugin_ocr_mock', 'plugin_ai_triage', 'plugin_1c', 'plugin_outbound'
)

if ($integrationsGates -contains $GateKey) {
    $t = Test-NetConnection -ComputerName 127.0.0.1 -Port 12223 -WarningAction SilentlyContinue -ErrorAction SilentlyContinue
    if (-not $t.TcpTestSucceeded) {
        Run-Fix 'qemu-integrations-up' (Join-Path $Root 'scripts\qemu-integrations-up.ps1') | Out-Null
    }
    Run-Fix 'Wait-IntegrationsOnline' (Join-Path $Root 'scripts\vpp\Wait-IntegrationsOnline.ps1') @('-MaxSec', '600', '-StartVmIfDown', '-RepairGateway') | Out-Null
}

switch ($GateKey) {
    'lifecycle_physical' {
        Run-Fix 'Wait-IntegrationsOnline' (Join-Path $Root 'scripts\vpp\Wait-IntegrationsOnline.ps1') @('-MaxSec', '600', '-StartVmIfDown', '-RepairGateway') | Out-Null
    }
    'module_lifecycle_full' {
        Run-Fix 'Wait-IntegrationsOnline' (Join-Path $Root 'scripts\vpp\Wait-IntegrationsOnline.ps1') @('-MaxSec', '600', '-StartVmIfDown', '-RepairGateway') | Out-Null
    }
    { $_ -in @('addon_smokes_all', 'addon-deep-archive') } {
        $detail = $env:VPP_LAST_GATE_DETAIL
        if ($detail -match 'turn|web-client-env') {
            Run-Fix 'Repair-WebTurnIce' (Join-Path $Root 'scripts\vpp\Repair-WebTurnIce.ps1') | Out-Null
        }
        if ($detail -match 'manifest\.json|deep-archive|MinIO') {
            Run-Fix 'Repair-MinioDeepArchive' (Join-Path $Root 'scripts\vpp\Repair-MinioDeepArchive.ps1') | Out-Null
        }
        if ($detail -match 'NATS|14222|hotplug') {
            Get-CimInstance Win32_Process -Filter "name='plink.exe'" -ErrorAction SilentlyContinue |
                Where-Object { $_.CommandLine -match ':14222' } |
                ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
        }
        if ($detail -match 'EXPORT_ADMIN|export-compliance|export-suggest|addon-export') {
            Run-Fix 'Repair-ExportAdmin' (Join-Path $Root 'scripts\vpp\Repair-ExportAdmin.ps1') | Out-Null
        }
    }
    'stack_health' {
        Run-Fix 'wait-api-health' (Join-Path $Root 'deploy\qemu\run\wait-api-health.ps1') @('-MaxMinutes', '10') | Out-Null
    }
    'korus_web' {
        Run-Fix 'Repair-WebTurnIce' (Join-Path $Root 'scripts\vpp\Repair-WebTurnIce.ps1') | Out-Null
    }
}

if ($ran) { exit 0 }
exit 1
