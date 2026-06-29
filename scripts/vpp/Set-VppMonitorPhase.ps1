#Requires -Version 5.1
param([string]$Phase)
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$path = Join-Path $Root 'deploy\qemu\run\vpp-evidence\vpp-monitor-phase.txt'
if (-not (Test-Path (Split-Path $path))) { New-Item -ItemType Directory -Path (Split-Path $path) -Force | Out-Null }
if ($Phase) { Set-Content -Path $path -Value $Phase -Encoding utf8 }
else { Remove-Item -Path $path -Force -ErrorAction SilentlyContinue }
