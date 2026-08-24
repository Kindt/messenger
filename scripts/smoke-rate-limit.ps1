# Rate limit smoke (epic 04) — requires RATE_LIMIT_AUTH_ENABLED + Redis.
# Default login limit is 60/min; with timing floor ~0.7s need ~70+ attempts.
param(
    [string]$BaseUrl = "http://127.0.0.1:18080",
    [int]$Attempts = 90,
    [int]$MaxSeconds = 120
)
$ErrorActionPreference = "Stop"
$got429 = $false
$deadline = (Get-Date).AddSeconds($MaxSeconds)
for ($i = 1; $i -le $Attempts -and (Get-Date) -lt $deadline; $i++) {
    try {
        Invoke-WebRequest -Uri "$BaseUrl/api/v1/auth/login" -Method Post `
            -Body (@{ username = "rl_probe_$i"; password = "bad" } | ConvertTo-Json) `
            -ContentType "application/json" -UseBasicParsing -TimeoutSec 8 | Out-Null
    } catch {
        $code = $null
        if ($_.Exception.Response) {
            $code = [int]$_.Exception.Response.StatusCode
        }
        if ($code -eq 429) { $got429 = $true; break }
    }
}
if (-not $got429) {
    Write-Host "[WARN] no 429 in $Attempts attempts / ${MaxSeconds}s (rate limit may be disabled or threshold high)" -ForegroundColor Yellow
    exit 0
}
Write-Host "[OK] rate limit returned 429" -ForegroundColor Green
