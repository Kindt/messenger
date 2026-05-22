# Dot-source: REST login helper for smokes.

function Get-KorusApiToken {
    param(
        [string]$BaseUrl,
        [string]$User,
        [string]$Pass
    )
    $login = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post `
        -Body (@{ username = $User; password = $Pass } | ConvertTo-Json) `
        -ContentType "application/json; charset=utf-8"
    $t = $login.access_token
    if (-not $t) { $t = $login.accessToken }
    if (-not $t) { throw "No access token for $User" }
    return $t
}

function New-KorusAuthHeaders {
    param([string]$Token)
    return @{ Authorization = "Bearer $Token" }
}
