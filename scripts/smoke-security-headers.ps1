# Security headers smoke (epic 04).
param([string]$BaseUrl = "http://127.0.0.1:18080")
$ErrorActionPreference = "Stop"
$r = Invoke-WebRequest -Uri "$BaseUrl/api/v1/health" -UseBasicParsing
foreach ($h in @("Strict-Transport-Security", "X-Content-Type-Options", "X-Frame-Options", "Referrer-Policy")) {
    if (-not $r.Headers[$h]) { throw "Missing header: $h" }
}
Write-Host "[OK] security headers present on $BaseUrl" -ForegroundColor Green
