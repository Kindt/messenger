#Requires -Version 5.1
param(
    [string] $BaseUrl = 'http://127.0.0.1:18080',
    [string] $Username = $(if ($env:KORUS_DESKTOP_SMOKE_USER) { $env:KORUS_DESKTOP_SMOKE_USER } else { 'admin' }),
    [string] $Password = $(if ($env:KORUS_DESKTOP_SMOKE_PASSWORD) { $env:KORUS_DESKTOP_SMOKE_PASSWORD } else { 'admin' })
)
$ErrorActionPreference = 'Stop'

$login = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method POST `
    -Body (@{ username = $Username; password = $Password } | ConvertTo-Json) -ContentType 'application/json'
$token = $login.access_token
if (-not $token) { Write-Error 'login failed for ws smoke' }

$wsPort = if ($BaseUrl -match ':18080') { '18082' } else { '18082' }
$hostName = ([uri]$BaseUrl).Host
$wsUrl = "ws://${hostName}:${wsPort}/ws?token=$token"

# Lightweight probe: TCP reachability of WS port (full frame protocol in liveServerTest).
$tcp = New-Object System.Net.Sockets.TcpClient
try {
    $tcp.Connect($hostName, [int]$wsPort)
} finally {
    $tcp.Close()
}

Write-Host "PASS smoke-desktop-websocket (tcp $hostName`:$wsPort token ok)"
exit 0
