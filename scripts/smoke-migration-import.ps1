# Migration import smoke (spec 022 US9).
param(
    [string]$BaseUrl = "http://127.0.0.1:18080"
)

$ErrorActionPreference = "Stop"
$API = "$BaseUrl/api/v1"
$login = Invoke-RestMethod -Method POST -Uri "$API/auth/login" -ContentType "application/json" `
  -Body '{"username":"csadmin","password":"csadmin"}'
$token = $login.access_token
$config = @{
  export_json = @{
    name = "Smoke import"
    messages = @(
      @{ id = 1; type = "message"; text = "smoke one" }
      @{ id = 2; type = "message"; text = "smoke two" }
    )
  }
} | ConvertTo-Json -Depth 5 -Compress
$body = @{
  source = "telegram_export_v1"
  config_json = $config
} | ConvertTo-Json -Compress
$job = Invoke-RestMethod -Method POST -Uri "$API/admin/migration-import" `
  -Headers @{ Authorization = "Bearer $token" } `
  -ContentType "application/json" `
  -Body $body
if (-not $job.id) { throw "migration import job missing id" }
$processed = Invoke-RestMethod -Method POST -Uri "$API/admin/migration-import/$($job.id)/process" `
  -Headers @{ Authorization = "Bearer $token" } `
  -ContentType "application/json" `
  -Body "{}"
if ($processed.status -ne "completed") {
  throw "migration import process failed status=$($processed.status) result=$($processed.result_json)"
}
$result = $processed.result_json | ConvertFrom-Json
if ($result.imported_messages -lt 1) {
  throw "migration import imported_messages=$($result.imported_messages)"
}
Write-Host "[OK] migration import job $($job.id) imported_messages=$($result.imported_messages) chat_id=$($result.chat_id)"
