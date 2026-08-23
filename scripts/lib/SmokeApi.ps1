# Dot-source: REST login helper for smokes.

function Get-KorusApiToken {
    param(
        [string]$BaseUrl,
        [string]$User,
        [string]$Pass
    )
    $maxAttempts = 6
    for ($attempt = 1; $attempt -le $maxAttempts; $attempt++) {
        try {
            $login = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post `
                -Body (@{ username = $User; password = $Pass } | ConvertTo-Json) `
                -ContentType "application/json; charset=utf-8"
            $t = $login.access_token
            if (-not $t) { $t = $login.accessToken }
            if (-not $t) { throw "No access token for $User" }
            return $t
        } catch {
            $code = $null
            if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
            if (($code -eq 429 -or $code -eq 401) -and $attempt -lt $maxAttempts) {
                $waitSec = [Math]::Min(5 * $attempt, 30)
                Start-Sleep -Seconds $waitSec
                continue
            }
            throw
        }
    }
    throw "No token for $User after $maxAttempts attempts"
}

function New-KorusAuthHeaders {
    param([string]$Token)
    return @{ Authorization = "Bearer $Token" }
}
