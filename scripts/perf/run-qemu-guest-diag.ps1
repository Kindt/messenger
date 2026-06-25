# Guest core-api / stack diagnostics via SSH.
$ErrorActionPreference = "Stop"
. "$PSScriptRoot\lib\Invoke-QemuServerGuest.ps1"
$script = Get-Content -Raw (Join-Path $PSScriptRoot "guest-diag-core-api.sh")
Invoke-QemuServerGuest -Script $script
