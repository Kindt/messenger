# k6 baseline against stage/prod HTTPS API (T604).
param(
    [Parameter(Mandatory = $true)]
    [string]$BaseUrl,
    [string]$OutJson = "deploy/qemu/run/k6-stage-baseline.json",
    [int]$DurationSec = 60
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$api = $BaseUrl.TrimEnd("/")
if (-not $api.EndsWith("/api")) { $api = "$api/api" }
& "$repoRoot\scripts\run-k6-qemu-baseline.ps1" -BaseUrl $api -OutJson $OutJson -DurationSec $DurationSec
