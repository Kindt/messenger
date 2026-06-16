# Smoke GET /api/v1/files/{id}/resize (embedded image resize, P1-4).
param(
    [string]$BaseUrl = "http://127.0.0.1:18080",
    [string]$User = "csadmin",
    [string]$Pass = "csadmin",
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"
Usage: .\scripts\smoke-file-resize.ps1 [-BaseUrl http://127.0.0.1:18080]

Uploads a PNG (multipart), probes /resize?w=32&h=32, expects JPEG response.
Prereq: QEMU stack API health on BaseUrl.
"@
    exit 0
}

function Fail([string]$msg) {
    Write-Host "[FAIL] $msg" -ForegroundColor Red
    exit 1
}

function Invoke-MultipartFileUpload {
    param(
        [string]$Url,
        [hashtable]$Headers,
        [byte[]]$Bytes,
        [string]$Filename,
        [string]$MimeType
    )
    $boundary = [guid]::NewGuid().ToString()
    $enc = [System.Text.Encoding]::GetEncoding("iso-8859-1")
    $bodyText = "--$boundary`r`n" +
        "Content-Disposition: form-data; name=`"file`"; filename=`"$Filename`"`r`n" +
        "Content-Type: $MimeType`r`n`r`n" +
        $enc.GetString($Bytes) +
        "`r`n--$boundary--`r`n"
    $reqHeaders = @{}
    foreach ($k in $Headers.Keys) { $reqHeaders[$k] = $Headers[$k] }
    $reqHeaders["Content-Type"] = "multipart/form-data; boundary=$boundary"
    return Invoke-RestMethod -Uri $Url -Method Post -Headers $reqHeaders -Body ($enc.GetBytes($bodyText))
}

$login = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post `
    -Body (@{ username = $User; password = $Pass } | ConvertTo-Json) `
    -ContentType "application/json; charset=utf-8"
$token = $login.access_token
if (-not $token) { $token = $login.accessToken }
if (-not $token) { Fail "No access token" }

$hdr = @{ Authorization = "Bearer $token" }

Add-Type -AssemblyName System.Drawing
$bmp = New-Object System.Drawing.Bitmap 96, 64
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.Clear([System.Drawing.Color]::FromArgb(255, 40, 120, 200))
$g.Dispose()
$ms = New-Object System.IO.MemoryStream
$bmp.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png)
$pngBytes = $ms.ToArray()
$bmp.Dispose()
$ms.Dispose()

$upload = Invoke-MultipartFileUpload -Url "$BaseUrl/api/v1/files/upload" -Headers $hdr `
    -Bytes $pngBytes -Filename "smoke-resize.png" -MimeType "image/png"
$fileId = $upload.file_id
if (-not $fileId) { $fileId = $upload.id }
if (-not $fileId) { Fail "upload returned no file_id" }
Write-Host "file_id=$fileId png_bytes=$($pngBytes.Length)" -ForegroundColor DarkGray

$resize = Invoke-WebRequest -Uri "$BaseUrl/api/v1/files/$fileId/resize?w=32&h=32" -Headers $hdr -UseBasicParsing
if ($resize.StatusCode -ne 200) { Fail "resize status $($resize.StatusCode)" }
$ct = $resize.Headers["Content-Type"]
if ($ct -notmatch "image/jpeg") { Fail "expected image/jpeg, got $ct" }
if ($resize.RawContentLength -le 0) { Fail "empty resize body" }

$textBytes = [System.Text.Encoding]::UTF8.GetBytes("plain smoke")
$textUpload = Invoke-MultipartFileUpload -Url "$BaseUrl/api/v1/files/upload" -Headers $hdr `
    -Bytes $textBytes -Filename "smoke-resize.txt" -MimeType "text/plain"
$textId = $textUpload.file_id
if (-not $textId) { $textId = $textUpload.id }
try {
    Invoke-WebRequest -Uri "$BaseUrl/api/v1/files/$textId/resize?w=32&h=32" -Headers $hdr -UseBasicParsing | Out-Null
    Fail "expected 400 for non-image resize"
} catch {
    $resp = $_.Exception.Response
    if (-not $resp -or [int]$resp.StatusCode -ne 400) {
        Fail "non-image resize: expected 400, got $($_.Exception.Message)"
    }
}

Write-Host "[OK] file resize smoke (JPEG thumb + non-image 400)" -ForegroundColor Green
