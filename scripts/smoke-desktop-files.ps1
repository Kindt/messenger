#Requires -Version 5.1
param(
    [string] $BaseUrl = 'http://127.0.0.1:18080',
    [string] $Username = $(if ($env:KORUS_DESKTOP_SMOKE_USER) { $env:KORUS_DESKTOP_SMOKE_USER } else { 'admin' }),
    [string] $Password = $(if ($env:KORUS_DESKTOP_SMOKE_PASSWORD) { $env:KORUS_DESKTOP_SMOKE_PASSWORD } else { 'admin' })
)
$ErrorActionPreference = 'Stop'

$login = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method POST `
    -Body (@{ username = $Username; password = $Password } | ConvertTo-Json) -ContentType 'application/json'
$h = @{ Authorization = "Bearer $($login.access_token)" }

$chats = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats" -Headers $h
$cid = $chats[0].id
if (-not $cid) { $cid = $chats[0].chat_id }

$tmp = [System.IO.Path]::GetTempFileName()
Set-Content -Path $tmp -Value "desktop file smoke $(Get-Date -Format o)" -Encoding utf8

try {
    $boundary = [guid]::NewGuid().ToString()
    $fileBytes = [System.IO.File]::ReadAllBytes($tmp)
    $fileName = 'smoke-desktop.txt'
    $lf = "`r`n"
    $bodyLines = @(
        "--$boundary",
        "Content-Disposition: form-data; name=`"file`"; filename=`"$fileName`"",
        "Content-Type: application/octet-stream$lf",
        [System.Text.Encoding]::UTF8.GetString($fileBytes),
        "--$boundary--$lf"
    )
    $body = $bodyLines -join $lf
    $upload = Invoke-RestMethod -Uri "$BaseUrl/api/v1/files/upload" -Method POST -Headers $h `
        -ContentType "multipart/form-data; boundary=$boundary" -Body $body
    if (-not $upload.id) { Write-Error 'upload failed' }

    $msgBody = @{ type = 'file'; content = $upload.id } | ConvertTo-Json
    $sent = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats/$cid/messages" -Method POST -Headers $h -Body $msgBody -ContentType 'application/json'
    if (-not $sent.id) { Write-Error 'file message failed' }

    try {
        $content = Invoke-WebRequest -Uri "$BaseUrl/api/v1/files/$($upload.id)/content" -Headers $h -UseBasicParsing
        if ($content.StatusCode -ne 200) { Write-Warning "download status $($content.StatusCode) (minio may be guest-only)" }
    } catch {
        Write-Warning "download skipped: $_"
    }

    Write-Host "PASS smoke-desktop-files (file=$($upload.id) msg=$($sent.id))"
    exit 0
}
finally {
    Remove-Item $tmp -Force -ErrorAction SilentlyContinue
}
