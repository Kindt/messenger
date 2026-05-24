# Sustained read load against core-api for profiling / heap sampling.
# Example: .\scripts\profiling\load-core-api.ps1 -BaseUrl http://127.0.0.1:18080 -DurationSeconds 45
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$User = "csadmin",
    [string]$Pass = "csadmin",
    [int]$DurationSeconds = 60,
    [int]$Concurrency = 4
)
$ErrorActionPreference = "Stop"

$login = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post `
    -Body (@{ username = $User; password = $Pass } | ConvertTo-Json) `
    -ContentType "application/json; charset=utf-8"
$token = $login.access_token
if (-not $token) { $token = $login.accessToken }
if (-not $token) { throw "No access token" }
$headers = @{ Authorization = "Bearer $token" }

$chats = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats" -Headers $headers
if (-not $chats -or $chats.Count -eq 0) { throw "No chats" }
$chatId = $chats[0].id
if (-not $chatId) { $chatId = $chats[0].chat_id }

Write-Host "Load: ${DurationSeconds}s chat=$chatId (sequential mixed endpoints)" -ForegroundColor Cyan
$end = (Get-Date).AddSeconds($DurationSeconds)
$ok = 0
$fail = 0
while ((Get-Date) -lt $end) {
    for ($w = 0; $w -lt $Concurrency; $w++) {
        try {
            Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats/$chatId/messages?limit=20" -Headers $headers | Out-Null
            Invoke-RestMethod -Uri "$BaseUrl/api/v1/health/ready" -Headers $headers | Out-Null
            $ok += 2
        } catch {
            $fail++
        }
    }
}
Write-Host "[OK] load finished: ok=$ok fail=$fail" -ForegroundColor Green
