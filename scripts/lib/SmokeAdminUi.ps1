# Общие проверки встроенной админ-консоли для smoke-скриптов.
# Подключение: в начале скрипта после param:  . "$PSScriptRoot\lib\SmokeAdminUi.ps1"

function Test-SmokeAdminConsoleRedirect {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$BaseUrl)
    Write-Host "GET $BaseUrl/api/v1/admin/console (no auto-redirect) ..." -ForegroundColor Cyan
    $uri = "$BaseUrl/api/v1/admin/console"
    $req = [System.Net.HttpWebRequest]::Create($uri)
    $req.AllowAutoRedirect = $false
    $req.Method = "GET"
    $req.Timeout = 15000
    $resp = $null
    try {
        $resp = $req.GetResponse()
    } catch [System.Net.WebException] {
        $resp = $_.Exception.Response
    }
    if (-not $resp) {
        throw "admin console: no response"
    }
    try {
        $code = [int]$resp.StatusCode
        if ($code -ne 303) {
            throw "admin console redirect: expected 303, got $code"
        }
        $loc = $resp.Headers["Location"]
        if (-not $loc) {
            throw "admin console: no Location header"
        }
        if ($loc -notmatch '/admin') {
            throw "admin console: unexpected Location: $loc"
        }
    } finally {
        if ($resp) { $resp.Close() }
    }
}

function Test-SmokeAdminStaticPage {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$BaseUrl)
    Write-Host "GET $BaseUrl/admin/ ..." -ForegroundColor Cyan
    $adminPage = Invoke-WebRequest -Uri "$BaseUrl/admin/" -UseBasicParsing -Method Get
    if ($adminPage.StatusCode -ne 200) {
        throw "admin static: status $($adminPage.StatusCode)"
    }
    if ($adminPage.Content -notmatch "Админ-консоль|admin-ui") {
        throw "admin static: unexpected body"
    }
}

function Test-SmokeAdminUiApi {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)][string]$BaseUrl,
        [Parameter(Mandatory)][hashtable]$AuthHeaders
    )
    Write-Host "GET $BaseUrl/api/v1/admin/ui/manifest ..." -ForegroundColor Cyan
    $manifest = Invoke-RestMethod -Uri "$BaseUrl/api/v1/admin/ui/manifest" -Headers $AuthHeaders -Method Get
    if (-not $manifest.api_version) {
        throw "admin/ui/manifest: no api_version"
    }
    $sectionList = @($manifest.sections)
    if ($null -eq $manifest.sections -or $sectionList.Count -lt 1) {
        throw "admin/ui/manifest: no sections"
    }
    if ($sectionList.Count -lt 7) {
        throw "admin/ui/manifest: expected at least 7 core sections, got $($sectionList.Count)"
    }
    $hasRetention = $false
    $hasUserOrg = $false
    $hasSession = $false
    $hasUiManifest = $false
    foreach ($s in $sectionList) {
        if ($s.id -eq 'core-retention') {
            $hasRetention = $true
        }
        if ($s.id -eq 'core-user-organization') {
            $hasUserOrg = $true
        }
        if ($s.id -eq 'core-admin-session') {
            $hasSession = $true
        }
        if ($s.id -eq 'core-admin-manifest') {
            $hasUiManifest = $true
        }
    }
    if (-not $hasRetention) {
        throw "admin/ui/manifest: missing section id core-retention"
    }
    if (-not $hasUserOrg) {
        throw "admin/ui/manifest: missing section id core-user-organization"
    }
    if (-not $hasSession) {
        throw "admin/ui/manifest: missing section id core-admin-session"
    }
    if (-not $hasUiManifest) {
        throw "admin/ui/manifest: missing section id core-admin-manifest"
    }
    Write-Host "GET $BaseUrl/api/v1/admin/ui/stats ..." -ForegroundColor Cyan
    $stats = Invoke-RestMethod -Uri "$BaseUrl/api/v1/admin/ui/stats" -Headers $AuthHeaders -Method Get
    if (-not $stats.api_version) {
        throw "admin/ui/stats: no api_version"
    }
    $sectionCount = $sectionList.Count
    return @{
        Manifest = $manifest
        Stats    = $stats
        SectionCount = $sectionCount
    }
}
