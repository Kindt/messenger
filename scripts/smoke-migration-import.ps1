# Migration import smoke (spec 022 US9 scaffold).
param(
    [string]$BaseUrl = "http://127.0.0.1:18080"
)

$ErrorActionPreference = "Stop"
$API = "$BaseUrl/api/v1"
$login = Invoke-RestMethod -Method POST -Uri "$API/auth/login" -ContentType "application/json" `
  -Body '{"username":"csadmin","password":"csadmin"}'
$token = $login.access_token
$job = Invoke-RestMethod -Method POST -Uri "$API/admin/migration-import" `
  -Headers @{ Authorization = "Bearer $token" } `
  -ContentType "application/json" `
  -Body '{"source":"telegram_export_v1","config_json":"{\"fixture\":true}"}'
if (-not $job.id) { throw "migration import job missing id" }
Write-Host "[OK] migration import job $($job.id) status=$($job.status)"
