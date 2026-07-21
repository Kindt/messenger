# Wait until auth/login accepts csadmin (rate limit cooldown for VPP security smokes).
param(
    [string]$BaseUrl = "http://127.0.0.1:18080",
    [string]$User = "csadmin",
    [string]$Pass = "csadmin",
    [int]$MaxSec = 120
)

$ErrorActionPreference = "Stop"
$deadline = (Get-Date).AddSeconds($MaxSec)
$attempt = 0
while ((Get-Date) -lt $deadline) {
    $attempt++
    try {
        $login = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post `
            -Body (@{ username = $User; password = $Pass } | ConvertTo-Json) `
            -ContentType "application/json; charset=utf-8"
        if ($login.access_token -or $login.accessToken) {
            Write-Host "[OK] auth login ready for $User (attempt $attempt)" -ForegroundColor Green
            exit 0
        }
    } catch {
        $code = $null
        if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
        Write-Host "  login attempt $attempt -> HTTP $code" -ForegroundColor DarkGray
        if ($code -ne 429) { throw }
    }
    Start-Sleep -Seconds 5
}
Write-Host "[FAIL] auth still rate limited after ${MaxSec}s" -ForegroundColor Red
exit 1
