# Federation trust registry smoke (spec 022 T02308 MVP).
param(
    [string]$BaseUrl = "http://127.0.0.1:18080"
)

$ErrorActionPreference = "Stop"
$API = "$BaseUrl/api/v1"
$login = Invoke-RestMethod -Method POST -Uri "$API/auth/login" -ContentType "application/json" `
  -Body '{"username":"csadmin","password":"csadmin"}'
$token = $login.access_token
$headers = @{ Authorization = "Bearer $token" }

$orgs = Invoke-RestMethod -Method GET -Uri "$API/admin/organizations" -Headers $headers
if (-not $orgs -or $orgs.Count -lt 2) {
  Write-Host "[SKIP] federation trust smoke: need 2+ orgs on stack"
  exit 0
}
$orgA = $orgs[0].id
if (-not $orgA) { $orgA = $orgs[0].org_id }
$orgB = $orgs[1].id
if (-not $orgB) { $orgB = $orgs[1].org_id }
if ($orgA -eq $orgB) {
  Write-Host "[SKIP] federation trust smoke: single org only"
  exit 0
}

$me = Invoke-RestMethod -Method GET -Uri "$API/users/me" -Headers $headers
$meId = $me.id
if (-not $meId) { $meId = $me.user_id }
$orgPatch = (@{ org_id = $orgA } | ConvertTo-Json -Compress)
Invoke-RestMethod -Method PATCH -Uri "$API/admin/users/$meId/organization" `
  -Headers $headers -ContentType "application/json" -Body $orgPatch | Out-Null

$listBefore = Invoke-RestMethod -Method GET -Uri "$API/admin/federation/trust" -Headers $headers
if (-not ($listBefore -is [array])) {
  throw "expected array from GET federation/trust"
}

$body = @{
  partner_org_id = $orgB
  status = "active"
} | ConvertTo-Json -Compress
$created = Invoke-RestMethod -Method POST -Uri "$API/admin/federation/trust" `
  -Headers $headers -ContentType "application/json" -Body $body
if (-not $created.id) { throw "federation trust create missing id" }

$status = Invoke-RestMethod -Method GET -Uri "$API/platform/federation/status" -Headers $headers
if ($status.mode -ne "mvp" -and $status.mode -ne "scaffold") {
  throw "unexpected federation status mode=$($status.mode)"
}

Write-Host "[OK] federation trust smoke partner=$orgB mode=$($status.mode)"
