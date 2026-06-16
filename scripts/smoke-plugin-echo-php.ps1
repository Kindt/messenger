# Spec 014 — smoke: echo PHP plugin sidecar (local or integrations VM)

param(
    [string]$BaseUrl = "http://127.0.0.1:8088"
)

$ErrorActionPreference = "Stop"

$body = @{
    event_id = [guid]::NewGuid().ToString()
    type = "mention"
    text = "ping"
} | ConvertTo-Json -Compress

Write-Host "POST $BaseUrl/v1/plugin/handle"
$response = Invoke-RestMethod -Method Post -Uri "$BaseUrl/v1/plugin/handle" -ContentType "application/json" -Body $body
if (-not $response.messages -or $response.messages[0].text -notmatch "pong") {
    throw "Expected pong in response: $($response | ConvertTo-Json -Compress)"
}
Write-Host "OK: echo-php plugin pong"
