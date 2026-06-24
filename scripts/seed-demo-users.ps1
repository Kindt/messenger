# Seed demo users and chats for local/QEMU lab.
param(
    [string]$BaseUrl = "http://127.0.0.1:18080",
    [string]$KeycloakUrl = "http://127.0.0.1:18081",
    [string]$Realm = "avandocmsg",
    [string]$KeycloakAdminUser = "admin",
    [string]$KeycloakAdminPassword = "admin",
    [string]$Password = "12345",
    [string]$GroupTitle = "Demo group user1 user2 user3"
)

$ErrorActionPreference = "Stop"
$ApiBase = "$BaseUrl/api/v1"
$P2PMessage = [string]::Concat(([char[]]@(1055, 1088, 1080, 1074, 1077, 1090, 33, 32, 1069, 1090, 1086, 32, 1103, 33)))
$GroupMessage = [string]::Concat(([char[]]@(1071, 32, 1090, 1086, 1078, 1077, 32, 1079, 1076, 1077, 1089, 1100, 33)))
$DemoUsers = @(
    @{ username = "user1"; email = "user1@korus.local"; firstName = "user1"; lastName = "user1" },
    @{ username = "user2"; email = "user2@korus.local"; firstName = "user2"; lastName = "user2" },
    @{ username = "user3"; email = "user3@korus.local"; firstName = "user3"; lastName = "user3" }
)

function ConvertTo-JsonBody {
    param([hashtable]$Body)
    $json = ($Body | ConvertTo-Json -Depth 8 -Compress)
    $asciiJson = [regex]::Replace($json, "[^\u0000-\u007F]", {
        param($match)
        return "\u{0:x4}" -f [int][char]$match.Value[0]
    })
    Write-Output -NoEnumerate ([System.Text.Encoding]::ASCII.GetBytes($asciiJson))
}

function Get-ObjectId {
    param($Object, [string]$Label)
    $id = $Object.id
    if (-not $id) { $id = $Object.user_id }
    if (-not $id) { $id = $Object.chat_id }
    if (-not $id) { throw "$Label response missing id" }
    return [string]$id
}

function Get-KeycloakMasterToken {
    $body = "client_id=admin-cli&username=$([uri]::EscapeDataString($KeycloakAdminUser))&password=$([uri]::EscapeDataString($KeycloakAdminPassword))&grant_type=password"
    $token = Invoke-RestMethod -Method Post `
        -Uri "$KeycloakUrl/realms/master/protocol/openid-connect/token" `
        -ContentType "application/x-www-form-urlencoded" `
        -Body $body
    if (-not $token.access_token) { throw "Keycloak master token missing" }
    return $token.access_token
}

function Get-KeycloakUser {
    param([string]$Token, [string]$Username)
    $encoded = [uri]::EscapeDataString($Username)
    $users = Invoke-RestMethod -Method Get `
        -Uri "$KeycloakUrl/admin/realms/$Realm/users?username=$encoded&exact=true" `
        -Headers @{ Authorization = "Bearer $Token" }
    if ($users -and @($users).Count -gt 0) {
        return @($users)[0]
    }
    return $null
}

function Set-KeycloakPassword {
    param([string]$Token, [string]$UserId)
    $cred = @{
        type      = "password"
        value     = $Password
        temporary = $false
    }
    Invoke-RestMethod -Method Put `
        -Uri "$KeycloakUrl/admin/realms/$Realm/users/$UserId/reset-password" `
        -Headers @{ Authorization = "Bearer $Token" } `
        -ContentType "application/json; charset=utf-8" `
        -Body (ConvertTo-JsonBody $cred) | Out-Null
}

function Ensure-KeycloakUser {
    param([string]$Token, [hashtable]$User)

    $username = [string]$User.username
    $existing = Get-KeycloakUser -Token $Token -Username $username
    $profile = @{
        username      = $username
        email         = [string]$User.email
        emailVerified = $true
        firstName     = [string]$User.firstName
        lastName      = [string]$User.lastName
        enabled       = $true
    }

    if ($existing) {
        Invoke-RestMethod -Method Put `
            -Uri "$KeycloakUrl/admin/realms/$Realm/users/$($existing.id)" `
            -Headers @{ Authorization = "Bearer $Token" } `
            -ContentType "application/json; charset=utf-8" `
            -Body (ConvertTo-JsonBody $profile) | Out-Null
        Set-KeycloakPassword -Token $Token -UserId $existing.id
        Write-Host "  Keycloak updated $username" -ForegroundColor Green
        return [string]$existing.id
    }

    $create = $profile.Clone()
    $create.credentials = @(
        @{
            type      = "password"
            value     = $Password
            temporary = $false
        }
    )
    Invoke-WebRequest -Method Post `
        -Uri "$KeycloakUrl/admin/realms/$Realm/users" `
        -Headers @{ Authorization = "Bearer $Token" } `
        -ContentType "application/json; charset=utf-8" `
        -Body (ConvertTo-JsonBody $create) `
        -UseBasicParsing | Out-Null

    $created = Get-KeycloakUser -Token $Token -Username $username
    if (-not $created) { throw "Keycloak user $username was not found after create" }
    Write-Host "  Keycloak created $username" -ForegroundColor Green
    return [string]$created.id
}

function Login-DemoUser {
    param([string]$Username)
    $login = Invoke-RestMethod -Method Post `
        -Uri "$ApiBase/auth/login" `
        -ContentType "application/json; charset=utf-8" `
        -Body (ConvertTo-JsonBody @{ username = $Username; password = $Password })
    $token = $login.access_token
    if (-not $token) { $token = $login.accessToken }
    if (-not $token) { throw "No access token for $Username" }
    return $token
}

function New-AuthHeaders {
    param([string]$Token)
    return @{ Authorization = "Bearer $Token" }
}

function Get-CurrentUser {
    param([string]$Token)
    return Invoke-RestMethod -Method Get -Uri "$ApiBase/users/me" -Headers (New-AuthHeaders $Token)
}

function New-P2PChat {
    param([string]$Token, [string]$OtherUserId)
    $body = @{
        type       = "p2p"
        member_ids = @($OtherUserId)
    }
    $chat = Invoke-RestMethod -Method Post `
        -Uri "$ApiBase/chats" `
        -Headers (New-AuthHeaders $Token) `
        -ContentType "application/json; charset=utf-8" `
        -Body (ConvertTo-JsonBody $body)
    return Get-ObjectId $chat "create p2p chat"
}

function New-GroupChat {
    param([string]$Token, [string[]]$MemberIds)
    $body = @{
        type       = "group"
        title      = $GroupTitle
        member_ids = $MemberIds
    }
    $chat = Invoke-RestMethod -Method Post `
        -Uri "$ApiBase/chats" `
        -Headers (New-AuthHeaders $Token) `
        -ContentType "application/json; charset=utf-8" `
        -Body (ConvertTo-JsonBody $body)
    return Get-ObjectId $chat "create group chat"
}

function Send-TextMessage {
    param([string]$Token, [string]$ChatId, [string]$Content)
    $body = @{
        type        = "text"
        content     = $Content
        e2ee_scheme = "legacy"
    }
    $msg = Invoke-RestMethod -Method Post `
        -Uri "$ApiBase/chats/$ChatId/messages" `
        -Headers (New-AuthHeaders $Token) `
        -ContentType "application/json; charset=utf-8" `
        -Body (ConvertTo-JsonBody $body)
    return Get-ObjectId $msg "send message"
}

Write-Host "=== seed-demo-users ===" -ForegroundColor Cyan
Write-Host "API: $BaseUrl" -ForegroundColor DarkGray
Write-Host "Keycloak: $KeycloakUrl realm=$Realm" -ForegroundColor DarkGray

$kcToken = Get-KeycloakMasterToken
foreach ($u in $DemoUsers) {
    Ensure-KeycloakUser -Token $kcToken -User $u | Out-Null
}

$state = @{}
foreach ($u in $DemoUsers) {
    $username = [string]$u.username
    $token = Login-DemoUser -Username $username
    $me = Get-CurrentUser -Token $token
    $state[$username] = @{
        token = $token
        id    = Get-ObjectId $me "users/me $username"
    }
    Write-Host "  API login OK $username id=$($state[$username].id)" -ForegroundColor Green
}

$pairs = @(
    @("user1", "user2"),
    @("user1", "user3"),
    @("user2", "user3")
)
foreach ($pair in $pairs) {
    $left = $pair[0]
    $right = $pair[1]
    $chatId = New-P2PChat -Token $state[$left].token -OtherUserId $state[$right].id
    $leftMsgId = Send-TextMessage -Token $state[$left].token -ChatId $chatId -Content $P2PMessage
    $rightMsgId = Send-TextMessage -Token $state[$right].token -ChatId $chatId -Content $P2PMessage
    Write-Host "  P2P $left/$right chat=$chatId messages=$leftMsgId,$rightMsgId" -ForegroundColor Green
}

$memberIds = @($state["user2"].id, $state["user3"].id)
$groupId = New-GroupChat -Token $state["user1"].token -MemberIds $memberIds
foreach ($username in @("user1", "user2", "user3")) {
    $msgId = Send-TextMessage -Token $state[$username].token -ChatId $groupId -Content $GroupMessage
    Write-Host "  Group $username chat=$groupId message=$msgId" -ForegroundColor Green
}

Write-Host "[OK] demo seed complete: users=user1,user2,user3 group=$groupId" -ForegroundColor Green
Write-Output $groupId
