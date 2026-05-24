# POST /api/v1/admin/export-compliance-prep (seed messages + retention). Requires EXPORT_ADMIN_SUGGEST_ENABLED=true.
param(
    [string]$ChatId = "",
    [int]$MessageCount = 3,
    [string]$BaseUrl = "http://localhost:8080",
    [string]$AdminUser = "csadmin",
    [string]$AdminPass = "csadmin",
    [switch]$NoCreateGroup,
    [switch]$IncludeFile,
    [string]$FileName = "compliance-smoke.txt"
)
$ErrorActionPreference = "Stop"

function Get-Token {
    param($User, $Pass)
    $login = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post `
        -Body (@{ username = $User; password = $Pass } | ConvertTo-Json) `
        -ContentType "application/json; charset=utf-8"
    $t = $login.access_token
    if (-not $t) { $t = $login.accessToken }
    if (-not $t) { throw "No token for $User" }
    return $t
}

$hdr = @{ Authorization = "Bearer $(Get-Token -User $AdminUser -Pass $AdminPass)" }
$body = @{
    message_count = $MessageCount
    create_group  = (-not $NoCreateGroup) -and (-not $ChatId)
}
if ($ChatId) { $body.chat_id = $ChatId }
if ($IncludeFile) {
    $body.include_file = $true
    if ($FileName) { $body.file_name = $FileName }
}

Write-Host "POST export-compliance-prep ..." -ForegroundColor Cyan
try {
    $prep = Invoke-RestMethod -Uri "$BaseUrl/api/v1/admin/export-compliance-prep" -Method Post `
        -Headers $hdr -ContentType "application/json; charset=utf-8" `
        -Body ($body | ConvertTo-Json)
} catch {
    if ($_.Exception.Response.StatusCode.value__ -eq 404) {
        Write-Host "[FAIL] disabled? Set EXPORT_ADMIN_SUGGEST_ENABLED=true" -ForegroundColor Red
    }
    throw
}

$cid = $prep.chat_id
if (-not $cid) { $cid = $prep.chatId }
$ids = $prep.message_ids
if (-not $ids) { $ids = $prep.messageIds }
$fid = $prep.file_id
if (-not $fid) { $fid = $prep.fileId }
Write-Host "[OK] chat_id=$cid messages=$($ids.Count) retention_patched=$($prep.retention_patched) file_id=$fid" -ForegroundColor Green
if ($cid) { Write-Host "CHAT_ID=$cid" }
if ($fid) { Write-Host "FILE_ID=$fid" }
if ($cid) { Write-Output "CHAT_ID=$cid" }
if ($fid) { Write-Output "FILE_ID=$fid" }
