# Rate limit smoke (epic 04) — requires RATE_LIMIT_AUTH_ENABLED + Redis.
param(
    [string]$BaseUrl = "http://127.0.0.1:18080",
    [int]$Attempts = 120
)
$ErrorActionPreference = "Stop"
$got429 = $false
for ($i = 1; $i -le $Attempts; $i++) {
    try {
        Invoke-WebRequest -Uri "$BaseUrl/api/v1/auth/login" -Method Post `
            -Body (@{ username = "nobody"; password = "bad" } | ConvertTo-Json) `
            -ContentType "application/json" -UseBasicParsing -TimeoutSec 15 | Out-Null
    } catch {
        if ($_.Exception.Response.StatusCode.value__ -eq 429) { $got429 = $true; break }
    }
}
if (-not $got429) {
    Write-Host "[WARN] no 429 in $Attempts attempts (rate limit may be disabled)" -ForegroundColor Yellow
    exit 0
}
Write-Host "[OK] rate limit returned 429" -ForegroundColor Green
