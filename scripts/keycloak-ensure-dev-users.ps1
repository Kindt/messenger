# Fix dev users in realm avandocmsg (email required for Keycloak 24+ password grant).
param(
    [string]$KeycloakUrl = "http://127.0.0.1:18081",
    [string]$AdminUser = "admin",
    [string]$AdminPassword = "admin",
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"
Usage: .\scripts\keycloak-ensure-dev-users.ps1 [-KeycloakUrl http://127.0.0.1:18081]

Sets email + password for admin/csadmin in realm avandocmsg.
On server VM use: -KeycloakUrl http://127.0.0.1:8080
"@
    exit 0
}

function Get-MasterToken {
    $body = "client_id=admin-cli&username=$AdminUser&password=$AdminPassword&grant_type=password"
    $r = Invoke-RestMethod -Method Post -Uri "$KeycloakUrl/realms/master/protocol/openid-connect/token" `
        -ContentType "application/x-www-form-urlencoded" -Body $body
    return $r.access_token
}

function Fix-DevUser {
    param([string]$Token, [string]$Username, [string]$Email, [string]$First, [string]$Last, [string]$Password)
    $users = Invoke-RestMethod -Uri "$KeycloakUrl/admin/realms/avandocmsg/users?username=$Username" `
        -Headers @{ Authorization = "Bearer $Token" }
    if (-not $users -or $users.Count -eq 0) {
        Write-Host "  skip $Username (not found)"
        return
    }
    $id = $users[0].id
    $patch = @{ email = $Email; emailVerified = $true; firstName = $First; lastName = $Last; enabled = $true }
    Invoke-RestMethod -Method Put -Uri "$KeycloakUrl/admin/realms/avandocmsg/users/$id" `
        -Headers @{ Authorization = "Bearer $Token" } -ContentType "application/json" -Body ($patch | ConvertTo-Json)
    $cred = @{ type = "password"; value = $Password; temporary = $false }
    Invoke-RestMethod -Method Put -Uri "$KeycloakUrl/admin/realms/avandocmsg/users/$id/reset-password" `
        -Headers @{ Authorization = "Bearer $Token" } -ContentType "application/json" -Body ($cred | ConvertTo-Json)
    Write-Host "  ok $Username ($Email)" -ForegroundColor Green
}

Write-Host "=== keycloak-ensure-dev-users ($KeycloakUrl) ===" -ForegroundColor Cyan
$tok = Get-MasterToken
Fix-DevUser -Token $tok -Username admin -Email admin@korus.local -First System -Last Admin -Password admin
Fix-DevUser -Token $tok -Username csadmin -Email csadmin@korus.local -First Console -Last Superuser -Password csadmin
Write-Host "=== done ===" -ForegroundColor Cyan
