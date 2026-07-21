# Ensure org IP allowlist is disabled after lab enforce smokes (QEMU :18080).
function Reset-QemuLabOrgIpAllowlist {
    param(
        [string]$BaseUrl = "http://127.0.0.1:18080",
        [string]$User = "csadmin",
        [string]$Pass = "csadmin"
    )
    if ($BaseUrl -notmatch ':18080') { return }

    $api = "$BaseUrl/api/v1"
    try {
        $login = Invoke-RestMethod -Uri "$api/auth/login" -Method Post `
            -Body (@{ username = $User; password = $Pass } | ConvertTo-Json) `
            -ContentType "application/json; charset=utf-8"
        $token = if ($login.access_token) { $login.access_token } else { $login.accessToken }
        if (-not $token) { return }
        $headers = @{ Authorization = "Bearer $token" }
        $me = Invoke-RestMethod -Uri "$api/users/me" -Headers $headers
        $orgId = $me.org_id
        if (-not $orgId) { $orgId = $me.organization_id }
        if (-not $orgId) {
            $orgs = Invoke-RestMethod -Uri "$api/admin/organizations" -Headers $headers
            if ($orgs -and @($orgs).Count -gt 0) {
                $orgId = $orgs[0].id
                if (-not $orgId) { $orgId = $orgs[0].org_id }
            }
        }
        if (-not $orgId) { return }
        Invoke-RestMethod -Uri "$api/admin/orgs/$orgId/ip-allowlist" -Method Patch -Headers $headers `
            -ContentType "application/json; charset=utf-8" `
            -Body (@{ enabled = $false; allowed_cidrs = "" } | ConvertTo-Json) | Out-Null
        Write-Host "[OK] org ip-allowlist reset (disabled) org=$orgId" -ForegroundColor DarkGray
    } catch {
        Write-Host "[WARN] ip-allowlist reset skipped: $($_.Exception.Message)" -ForegroundColor Yellow
    }
}
