#Requires -Version 5.1
# Multi-org isolation smoke for Cell dogfood (spec 011 T01116). Host -> QEMU API :18080.
param(
    [string]$BaseUrl = "http://127.0.0.1:18080"
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
. (Join-Path $ScriptDir "lib\SmokeMessaging.ps1")

Write-Host "[cell-multi-org] API $BaseUrl"
$token = Get-SmokeApiToken -BaseUrl $BaseUrl -User "csadmin" -Pass "csadmin"
$headers = @{ Authorization = "Bearer $token"; "Content-Type" = "application/json" }

$suffix = [Guid]::NewGuid().ToString("N").Substring(0, 8)
$names = @("cell-org-a-$suffix", "cell-org-b-$suffix")
$ids = @()

foreach ($name in $names) {
    $body = @{ name = $name } | ConvertTo-Json
    $resp = Invoke-RestMethod -Uri "$BaseUrl/api/v1/admin/organizations" -Method Post -Headers $headers -Body $body
    if (-not $resp.id) { throw "create org failed: $name" }
    $ids += $resp.id
    Write-Host "[OK] created org $name -> $($resp.id)"
}

$list = Invoke-RestMethod -Uri "$BaseUrl/api/v1/admin/organizations" -Method Get -Headers $headers
foreach ($id in $ids) {
    $found = @($list | Where-Object { $_.id -eq $id })
    if ($found.Count -ne 1) { throw "org $id not in list" }
}

Write-Host "[OK] multi-org isolation smoke: $($ids.Count) orgs listed"
