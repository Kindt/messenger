# Export-replay non-stub policy: chat export must finish as export_v1 (not stub_written).
param(
    [string]$BaseUrl = "http://127.0.0.1:18080",
    [int]$PollSeconds = 120,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"
Usage: .\scripts\smoke-export-replay-non-stub.ps1 [-BaseUrl http://127.0.0.1:18080]

Uses first available chat, runs export, asserts status export_v1 (rejects stub_written).
Prereq: full-server stack with export-replay-worker (DB_JDBC_URL + EXPORT_REPLAY_REQUIRE_JDBC).
"@
    exit 0
}

function Fail([string]$msg) {
    Write-Host "[FAIL] $msg" -ForegroundColor Red
    exit 1
}

$scriptDir = $PSScriptRoot
$out = & "$scriptDir\smoke-export-chat.ps1" -BaseUrl $BaseUrl -PollSeconds $PollSeconds -SkipDownload 2>&1
Write-Host $out
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if ($out -match 'stub_written') {
    Fail "export finished stub_written - enable DB_JDBC_URL and export-replay-worker"
}
if ($out -notmatch 'export_v1') {
    Fail "expected export_v1 terminal status"
}
Write-Host "[OK] export-replay non-stub smoke (export_v1 gate)" -ForegroundColor Green
