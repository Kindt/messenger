# Cross-org add-member gate: blocked without trust, allowed with trust (spec 022 T02308).
param(
    [string]$BaseUrl = "http://127.0.0.1:18080"
)

$ErrorActionPreference = "Stop"
$API = "$BaseUrl/api/v1"

function Login-User($user, $pass) {
    $login = Invoke-RestMethod -Method POST -Uri "$API/auth/login" -ContentType "application/json" `
      -Body (@{ username = $user; password = $pass } | ConvertTo-Json -Compress)
    return $login.access_token
}

function Get-MeId($token) {
    $me = Invoke-RestMethod -Method GET -Uri "$API/users/me" -Headers @{ Authorization = "Bearer $token" }
    $id = $me.id
    if (-not $id) { $id = $me.user_id }
    if (-not $id) { throw "users/me missing id" }
    return $id
}

function Invoke-ApiStatus($method, $uri, $token, $bodyJson) {
    $params = @{
        Uri = $uri
        Method = $method
        Headers = @{ Authorization = "Bearer $token" }
        ContentType = "application/json"
        ErrorAction = "SilentlyContinue"
    }
    if ($bodyJson) { $params.Body = $bodyJson }
    try {
        $resp = Invoke-WebRequest @params
        return [int]$resp.StatusCode
    } catch {
        if ($_.Exception.Response) {
            return [int]$_.Exception.Response.StatusCode
        }
        throw
    }
}

$adminToken = Login-User "admin" "admin"
$csToken = Login-User "csadmin" "csadmin"
$adminHeaders = @{ Authorization = "Bearer $adminToken" }
$csHeaders = @{ Authorization = "Bearer $csToken" }

$adminId = Get-MeId $adminToken
$csId = Get-MeId $csToken

$orgs = Invoke-RestMethod -Method GET -Uri "$API/admin/organizations" -Headers $csHeaders
if (-not $orgs -or $orgs.Count -lt 2) {
    Write-Host "[SKIP] federation cross-org: need 2+ orgs"
    exit 0
}

$orgA = if ($orgs[0].id) { $orgs[0].id } else { $orgs[0].org_id }
$orgB = if ($orgs[1].id) { $orgs[1].id } else { $orgs[1].org_id }
if ($orgA -eq $orgB) {
    Write-Host "[SKIP] federation cross-org: single org only"
    exit 0
}

$orgBodyA = (@{ org_id = $orgA } | ConvertTo-Json -Compress)
Invoke-RestMethod -Method PATCH -Uri "$API/admin/users/$adminId/organization" `
  -Headers $csHeaders -ContentType "application/json" -Body $orgBodyA | Out-Null
$orgBodyB = (@{ org_id = $orgB } | ConvertTo-Json -Compress)
Invoke-RestMethod -Method PATCH -Uri "$API/admin/users/$csId/organization" `
  -Headers $csHeaders -ContentType "application/json" -Body $orgBodyB | Out-Null

$chatBody = (@{
  type = "group"
  title = "Fed cross-org smoke"
  member_ids = @($adminId)
} | ConvertTo-Json -Compress)
$chat = Invoke-RestMethod -Method POST -Uri "$API/chats" -Headers $adminHeaders `
  -ContentType "application/json" -Body $chatBody
$chatId = $chat.id
if (-not $chatId) { throw "group create missing id" }

$addBody = (@{ user_id = $csId } | ConvertTo-Json -Compress)
$blocked = Invoke-ApiStatus "POST" "$API/chats/$chatId/members" $adminToken $addBody
if ($blocked -ne 403) {
    throw "expected 403 without trust, got $blocked"
}

$trustBody = (@{ partner_org_id = $orgB; status = "active" } | ConvertTo-Json -Compress)
Invoke-RestMethod -Method POST -Uri "$API/admin/federation/trust" -Headers $adminHeaders `
  -ContentType "application/json" -Body $trustBody | Out-Null

$allowed = Invoke-ApiStatus "POST" "$API/chats/$chatId/members" $adminToken $addBody
if ($allowed -ne 201 -and $allowed -ne 200) {
    throw "expected 201 with trust, got $allowed"
}

Write-Host "[OK] federation cross-org smoke orgA=$orgA orgB=$orgB chat=$chatId"
