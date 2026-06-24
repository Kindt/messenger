param(
    [string]$ScriptPath = (Join-Path $PSScriptRoot "seed-demo-users.ps1")
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $ScriptPath)) {
    throw "Not found: $ScriptPath"
}

$text = Get-Content -Raw -Path $ScriptPath

foreach ($needle in @(
    "user1",
    "user2",
    "user3",
    "12345",
    "1055, 1088, 1080, 1074, 1077, 1090",
    "1071, 32, 1090, 1086, 1078, 1077",
    "/api/v1",
    "auth/login",
    "chats",
    "/admin/realms/"
)) {
    if ($text -notlike "*$needle*") {
        throw "seed-demo-users missing required text: $needle"
    }
}

if ($text -notmatch 'reset-password') {
    throw "seed-demo-users must reset Keycloak passwords"
}

if ($text -notmatch 'type\s*=\s*"p2p"') {
    throw "seed-demo-users must create P2P chats"
}

if ($text -notmatch 'e2ee_scheme\s*=\s*"legacy"') {
    throw "seed-demo-users must send demo messages as plaintext legacy scheme"
}

if ($text -notmatch 'Send-TextMessage\s+-Token\s+\$state\[\$right\]\.token') {
    throw "seed-demo-users must send a P2P demo message from the second participant too"
}

if ($text -notmatch 'type\s*=\s*"group"') {
    throw "seed-demo-users must create a group chat"
}

Write-Host "seed-demo-users static test OK" -ForegroundColor Green
