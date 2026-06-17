# Spec 011 T01131: provision Keycloak realm + dev admin user for a SaaS tenant cell.
param(
    [Parameter(Mandatory)][string]$RealmName,
    [string]$KeycloakUrl = "http://127.0.0.1:18081",
    [string]$MasterUser = "admin",
    [string]$MasterPassword = "admin",
    [string]$TenantAdminUser = "tenant-admin",
    [string]$TenantAdminPassword = "tenant-admin",
    [string]$TenantAdminEmail = "",
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"
Usage: .\scripts\keycloak-provision-realm.ps1 -RealmName acme [-KeycloakUrl http://127.0.0.1:18081]

Creates realm (if missing), client avandocmsg-web, and tenant admin user.
On server guest use KeycloakUrl http://127.0.0.1:8080 inside VM or host forward :18081.
"@
    exit 0
}

function Get-MasterToken {
    $body = "client_id=admin-cli&username=$MasterUser&password=$MasterPassword&grant_type=password"
    $r = Invoke-RestMethod -Method Post -Uri "$KeycloakUrl/realms/master/protocol/openid-connect/token" `
        -ContentType "application/x-www-form-urlencoded" -Body $body
    return $r.access_token
}

function Invoke-Kc {
    param([string]$Token, [string]$Method, [string]$Path, [object]$Body = $null)
    $headers = @{ Authorization = "Bearer $Token" }
    $uri = "$KeycloakUrl/admin$Path"
    if ($Body -ne $null) {
        return Invoke-RestMethod -Method $Method -Uri $uri -Headers $headers `
            -ContentType "application/json" -Body ($Body | ConvertTo-Json -Depth 8)
    }
    Invoke-RestMethod -Method $Method -Uri $uri -Headers $headers
}

Write-Host "=== keycloak-provision-realm: $RealmName ===" -ForegroundColor Cyan
$tok = Get-MasterToken

$existing = Invoke-Kc -Token $tok -Method Get -Path "/realms/$RealmName" -ErrorAction SilentlyContinue
if (-not $existing) {
    Write-Host "Creating realm $RealmName..."
    Invoke-Kc -Token $tok -Method Post -Path "/realms" -Body @{
        realm = $RealmName
        enabled = $true
        registrationAllowed = $false
        loginWithEmailAllowed = $true
        duplicateEmailsAllowed = $false
    } | Out-Null
} else {
    Write-Host "[OK] realm exists"
}

$clients = Invoke-Kc -Token $tok -Method Get -Path "/realms/$RealmName/clients?clientId=avandocmsg-web"
if (-not $clients -or $clients.Count -eq 0) {
    Write-Host "Creating client avandocmsg-web..."
    Invoke-Kc -Token $tok -Method Post -Path "/realms/$RealmName/clients" -Body @{
        clientId = "avandocmsg-web"
        name = "Korus Web"
        enabled = $true
        publicClient = $true
        directAccessGrantsEnabled = $true
        standardFlowEnabled = $true
        redirectUris = @("*")
        webOrigins = @("*")
    } | Out-Null
} else {
    Write-Host "[OK] client avandocmsg-web"
}

$email = if ($TenantAdminEmail) { $TenantAdminEmail } else { "$TenantAdminUser@$RealmName.local" }
$users = Invoke-Kc -Token $tok -Method Get -Path "/realms/$RealmName/users?username=$TenantAdminUser"
if (-not $users -or $users.Count -eq 0) {
    Write-Host "Creating user $TenantAdminUser..."
    Invoke-Kc -Token $tok -Method Post -Path "/realms/$RealmName/users" -Body @{
        username = $TenantAdminUser
        email = $email
        emailVerified = $true
        enabled = $true
        firstName = "Tenant"
        lastName = "Admin"
    } | Out-Null
    $users = Invoke-Kc -Token $tok -Method Get -Path "/realms/$RealmName/users?username=$TenantAdminUser"
}
$uid = $users[0].id
$cred = @{ type = "password"; value = $TenantAdminPassword; temporary = $false }
Invoke-Kc -Token $tok -Method Put -Path "/realms/$RealmName/users/$uid/reset-password" -Body $cred | Out-Null
Write-Host "[OK] tenant admin $TenantAdminUser ($email)" -ForegroundColor Green
Write-Host "=== done ===" -ForegroundColor Cyan
