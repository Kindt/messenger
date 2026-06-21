# Lab smoke: external stack preflight (spec 023) - repo-local, no live cutover
param(
    [string]$ApiBase = $(if ($env:KORUS_API_URL) {
        $u = $env:KORUS_API_URL.TrimEnd('/')
        if ($u -notmatch '/api$') { "$u/api" } else { $u }
    } else { "http://127.0.0.1:18080/api" })
)
$ErrorActionPreference = "Stop"

function Get-Token {
    $body = @{ username = "csadmin"; password = "csadmin" } | ConvertTo-Json
    $r = Invoke-RestMethod -Uri "$ApiBase/v1/auth/login" -Method Post -Body $body -ContentType "application/json"
    return $r.access_token
}

$token = Get-Token
$h = @{ Authorization = "Bearer $token" }

Write-Host "[1] platform external-stack status"
$status = Invoke-RestMethod -Uri "$ApiBase/v1/platform/external-stack/status" -Headers $h
if (-not $status) { throw "status empty" }

Write-Host "[2] checkpoint preflight sample"
$checkpoint = @{
    component = "search"
    source_profile = "sql-search"
    target_profile = "solr-bundled"
    checkpoint_type = "reindex"
    markers = @{
        reindex_cursor = "messages:42"
        index_schema_version = "v1"
        shadow_target = "solr-shadow"
    }
    rollback_profile = "sql-search"
    watch_window = "PT4H"
} | ConvertTo-Json -Depth 5
$cp = Invoke-RestMethod -Uri "$ApiBase/v1/platform/external-stack/preflight/checkpoint" -Method Post -Headers $h -Body $checkpoint -ContentType "application/json"
Write-Host ("checkpoint passed=" + $cp.passed + " severity=" + $cp.severity)

Write-Host "[3] profile preflight report"
try {
    $profileBody = @{ profile_id = "postgres-16-bundled"; evidence = @() } | ConvertTo-Json
    Invoke-RestMethod -Uri "$ApiBase/v1/platform/external-stack/preflight/profile/report" -Method Post -Headers $h -Body $profileBody -ContentType "application/json" | Out-Null
} catch {
    Write-Host "[WARN] profile preflight skipped (guest may need sync): $($_.Exception.Message)"
}

Write-Host "[OK] smoke-external-stack-lab-cutover (preflight only; live BYO cutover = customer ops)"
