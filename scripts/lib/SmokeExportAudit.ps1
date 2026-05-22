# Shared helpers for export smoke scripts (dot-source).

function Test-ExportAutoQueuedAudit {
    param(
        [string]$BaseUrl,
        [hashtable]$Headers,
        [string]$ChatId,
        [switch]$SkipAudit
    )
    if ($SkipAudit) {
        Write-Host "  audit check skipped" -ForegroundColor DarkGray
        return $null
    }
    $q = "limit=10&action=export.auto_queued&resource_type=export_job"
    $uri = "$BaseUrl/api/v1/admin/audit-events?$q"
    Write-Host "GET audit export.auto_queued ..." -ForegroundColor DarkGray
    $rows = Invoke-RestMethod -Uri $uri -Headers $Headers -Method Get
    $match = @($rows) | Where-Object {
        $_.details_json -and $_.details_json -match [regex]::Escape($ChatId)
    } | Select-Object -First 1
    if (-not $match) {
        throw "No export.auto_queued audit for chat $ChatId"
    }
    $jobId = $match.resource_id
    Write-Host "[OK] audit export.auto_queued job_id=$jobId" -ForegroundColor Green
    return $jobId
}

function Test-ExportCancelAudit {
    param(
        [string]$BaseUrl,
        [hashtable]$Headers,
        [string]$JobId,
        [string]$Action,
        [switch]$SkipAudit
    )
    if ($SkipAudit) {
        Write-Host "  audit check skipped" -ForegroundColor DarkGray
        return
    }
    $q = "limit=10&action=$([uri]::EscapeDataString($Action))&resource_type=export_job&resource_id=$([uri]::EscapeDataString($JobId))"
    $uri = "$BaseUrl/api/v1/admin/audit-events?$q"
    Write-Host "GET audit ($Action) job=$JobId ..." -ForegroundColor DarkGray
    $rows = Invoke-RestMethod -Uri $uri -Headers $Headers -Method Get
    $count = if ($rows) { @($rows).Count } else { 0 }
    if ($count -lt 1) {
        throw "Expected audit action=$Action resource_id=$JobId, got $count row(s)"
    }
    $match = @($rows) | Where-Object {
        $_.action -eq $Action -and $_.resource_id -eq $JobId -and $_.resource_type -eq "export_job"
    } | Select-Object -First 1
    if (-not $match) {
        throw "No matching audit row for $Action / $JobId"
    }
    Write-Host "[OK] audit $Action ($count row(s))" -ForegroundColor Green
}

function Test-ExportRequestedAudit {
    param(
        [string]$BaseUrl,
        [hashtable]$Headers,
        [string]$JobId,
        [switch]$SkipAudit
    )
    Test-ExportCancelAudit -BaseUrl $BaseUrl -Headers $Headers -JobId $JobId -Action "export.requested" -SkipAudit:$SkipAudit
}
