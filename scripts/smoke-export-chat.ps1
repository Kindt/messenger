# Smoke: POST chat export -> poll GET status -> download bundle/json/manifest.
# Requires export-replay-worker + DB; download needs EXPORT_DIR and/or MinIO on core-api.
# Example: .\scripts\smoke-export-chat.ps1 -BaseUrl http://127.0.0.1:8080 -ChatId <uuid>
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$User = "csadmin",
    [string]$Pass = "csadmin",
    [string]$ChatId = "",
    [string]$JobId = "",
    [int]$PollSeconds = 90,
    [int]$PollIntervalSec = 2,
    [switch]$SkipDownload
)
$ErrorActionPreference = "Stop"

function Get-ExportBytes {
    param($Response)
    $bytes = $Response.RawContentLength
    if ($bytes -and $bytes -gt 0) { return $bytes }
    if ($null -ne $Response.Content -and $Response.Content -is [byte[]]) {
        return $Response.Content.Length
    }
    if ($null -ne $Response.Content) {
        return [System.Text.Encoding]::UTF8.GetByteCount([string]$Response.Content)
    }
    return 0
}

function Invoke-ExportDownload {
    param(
        [string]$Uri,
        [hashtable]$Headers,
        [string]$Label
    )
    Write-Host "GET $Uri ($Label) ..." -ForegroundColor Cyan
    try {
        $dl = Invoke-WebRequest -Uri $Uri -Headers $Headers -Method Get -UseBasicParsing
        if ($dl.StatusCode -eq 200) {
            $bytes = Get-ExportBytes -Response $dl
            $ct = $dl.Headers["Content-Type"]
            if (-not $ct) { $ct = $dl.Headers["content-type"] }
            Write-Host "[OK] $Label : $bytes bytes content-type=$ct" -ForegroundColor Green
            return $true
        }
    } catch {
        Write-Host "[WARN] $Label failed: $($_.Exception.Message)" -ForegroundColor Yellow
    }
    return $false
}

$loginUri = "$BaseUrl/api/v1/auth/login"
Write-Host "POST $loginUri (user=$User)..." -ForegroundColor Cyan
$loginBody = @{ username = $User; password = $Pass } | ConvertTo-Json
$login = Invoke-RestMethod -Uri $loginUri -Method Post -Body $loginBody -ContentType "application/json; charset=utf-8"
$token = $login.access_token
if (-not $token) { $token = $login.accessToken }
if (-not $token) { throw "No access token from login" }

$headers = @{ Authorization = "Bearer $token" }

if (-not $ChatId) {
    Write-Host "GET $BaseUrl/api/v1/chats ..." -ForegroundColor Cyan
    $chats = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats" -Headers $headers -Method Get
    if (-not $chats -or $chats.Count -eq 0) {
        throw "No chats for user; pass -ChatId"
    }
    $first = $chats[0]
    $ChatId = $first.id
    if (-not $ChatId) { $ChatId = $first.chat_id }
    Write-Host "Using first chat: $ChatId" -ForegroundColor DarkGray
}

$exportUri = "$BaseUrl/api/v1/chats/$ChatId/export"
if ($JobId) {
    Write-Host "Using existing job_id=$JobId (skip POST)" -ForegroundColor DarkGray
    $jobId = $JobId
} else {
    Write-Host "POST $exportUri ..." -ForegroundColor Cyan
    try {
        $accepted = Invoke-WebRequest -Uri $exportUri -Method Post -Headers $headers -ContentType "application/json" -UseBasicParsing
    } catch {
        if ($_.Exception.Response.StatusCode.value__ -eq 403) {
            Write-Host "Export forbidden (need owner/admin on chat). Try another -ChatId." -ForegroundColor Yellow
        }
        throw
    }
    if ($accepted.StatusCode -ne 202) {
        throw "Expected 202 Accepted, got $($accepted.StatusCode)"
    }
    $body = $accepted.Content | ConvertFrom-Json
    $jobId = $body.job_id
    if (-not $jobId) { $jobId = $body.jobId }
    if (-not $jobId) { throw "No job_id in response" }
    Write-Host "[OK] job_id=$jobId" -ForegroundColor Green
}

$statusUri = "$exportUri/$jobId"
$deadline = (Get-Date).AddSeconds($PollSeconds)
$terminal = @("export_v1", "stub_written", "export_failed")

while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds $PollIntervalSec
    $st = Invoke-RestMethod -Uri $statusUri -Headers $headers -Method Get
    $status = $st.status
    Write-Host "  status=$status" -ForegroundColor DarkGray
    if ($terminal -contains $status) {
        Write-Host "[OK] Export finished: $status" -ForegroundColor Green
        if ($st.output_path) { Write-Host "  output_path=$($st.output_path)" -ForegroundColor DarkGray }
        if ($st.output_storage) { Write-Host "  output_storage=$($st.output_storage)" -ForegroundColor DarkGray }
        if ($st.output_format) { Write-Host "  output_format=$($st.output_format)" -ForegroundColor DarkGray }
        if ($null -ne $st.message_ttl_filter_applied) {
            Write-Host "  message_ttl_filter_applied=$($st.message_ttl_filter_applied)" -ForegroundColor DarkGray
        }

        if (-not $SkipDownload -and ($status -eq "export_v1" -or $status -eq "stub_written" -or $status -eq "export_failed")) {
            $dlBase = "$statusUri/download"
            $isZip = ($st.output_format -eq "zip") -or ($st.output_path -like "*.export.zip")
            Invoke-ExportDownload -Uri $dlBase -Headers $headers -Label "bundle" | Out-Null
            if ($isZip) {
                Invoke-ExportDownload -Uri "$dlBase`?part=json" -Headers $headers -Label "json" | Out-Null
                Invoke-ExportDownload -Uri "$dlBase`?part=manifest" -Headers $headers -Label "manifest" | Out-Null
                try {
                    $att = Invoke-RestMethod -Uri "$statusUri/attachments" -Headers $headers -Method Get
                    $fc = $att.file_count
                    if (-not $fc) { $fc = $att.fileCount }
                    Write-Host "[OK] attachments list: $fc file(s) zip_bundle=$($att.zip_bundle)" -ForegroundColor Green
                } catch {
                    Write-Host "[WARN] attachments list skipped: $($_.Exception.Message)" -ForegroundColor Yellow
                }
                try {
                    $manifest = Invoke-RestMethod -Uri "$dlBase`?part=manifest" -Headers $headers -Method Get
                    $ids = @($manifest.files | ForEach-Object { $_.fileId } | Where-Object { $_ } | Select-Object -First 2)
                    if ($ids.Count -ge 1) {
                        Invoke-ExportDownload -Uri "$dlBase`?part=binary&file_id=$($ids[0])" -Headers $headers -Label "binary" | Out-Null
                    }
                    if ($ids.Count -ge 2) {
                        $joined = ($ids -join ",")
                        Invoke-ExportDownload -Uri "$dlBase`?part=binaries&file_ids=$joined" -Headers $headers -Label "binaries" | Out-Null
                    }
                } catch {
                    Write-Host "[WARN] binary/binaries smoke skipped: $($_.Exception.Message)" -ForegroundColor Yellow
                }
            } else {
                Invoke-ExportDownload -Uri "$dlBase`?part=json" -Headers $headers -Label "json" | Out-Null
            }
        } elseif ($SkipDownload) {
            Write-Host "Skipped download (-SkipDownload)" -ForegroundColor DarkGray
        }
        exit 0
    }
}

throw "Timed out after ${PollSeconds}s waiting for export job $jobId"
