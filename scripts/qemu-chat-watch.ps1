# Start QEMU minute chat watch: status + auto-remediate + agent loop ticks.
param(
    [int]$IntervalSeconds = 60,
    [switch]$Hidden,
    [switch]$Force,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$RunDir = Join-Path $Root "deploy\qemu\run"
$LoopScript = Join-Path $PSScriptRoot "qemu-status-loop.ps1"

if ($Help) {
    Write-Host @"
Usage: .\scripts\qemu-chat-watch.ps1 [-IntervalSeconds 60] [-Hidden] [-Force]

Поминутные отчёты о состоянии Korus QEMU + auto-remediate (docker pull, bootstrap errors, redeploy server).

  По умолчанию: один отчёт сейчас, затем подсказка запустить loop в Cursor background terminal.
  -Hidden: фоновый loop без окна (лог только в status-minute.log, без chat ticks).
  -Force: перезапустить скрытый loop (start-qemu-status-loop).

Chat ticks: AGENT_LOOP_TICK_qemu_chat (агент читает status-minute.snapshot.json).
Co-hosted VMs: трогаются только korus-server/korus-web.

Stop hidden loop: .\scripts\stop-qemu-status-loop.ps1
"@
    exit 0
}

if ($Hidden) {
    $startArgs = @()
    if ($Force) { $startArgs += "-Force" }
    & (Join-Path $PSScriptRoot "start-qemu-status-loop.ps1") @startArgs
    exit $LASTEXITCODE
}

# Interactive: run one minute status now, then instruct Cursor loop.
& (Join-Path $PSScriptRoot "qemu-status-minute.ps1") -Once

Write-Host ""
Write-Host "Для поминутных отчётов в чат Cursor запустите в background terminal:" -ForegroundColor Cyan
Write-Host "  .\scripts\qemu-status-loop.ps1 -IntervalSeconds $IntervalSeconds" -ForegroundColor White
Write-Host ""
Write-Host "Агент будет будиться на AGENT_LOOP_TICK_qemu_chat и выводить summaryRu + issues." -ForegroundColor DarkGray
Write-Host "Snapshot: deploy/qemu/run/status-minute.snapshot.json" -ForegroundColor DarkGray
Write-Host "Remediate log: deploy/qemu/run/status-remediate.log" -ForegroundColor DarkGray
