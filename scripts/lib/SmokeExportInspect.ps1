# Dot-source: attachments list + manifest/json parts for admin export smokes.

function Invoke-ExportArtifactsInspect {
    param(
        [Parameter(Mandatory = $true)]
        [string]$BaseUrl,
        [Parameter(Mandatory = $true)]
        [hashtable]$Headers,
        [Parameter(Mandatory = $true)]
        [string]$ChatId,
        [Parameter(Mandatory = $true)]
        [string]$JobId,
        [int]$MinJsonBytes = 32,
        [switch]$VerifyBinary,
        [int]$MinBinaryBytes = 8
    )
    $root = "$BaseUrl/api/v1/admin/chats/$ChatId/export/$JobId"

    Write-Host "GET attachments ..." -ForegroundColor Cyan
    $att = Invoke-RestMethod -Uri "$root/attachments?limit=100" -Headers $Headers -Method Get
    $zipBundle = $att.zip_bundle
    if ($null -eq $zipBundle) { $zipBundle = $att.zipBundle }
    $total = $att.total_count
    if ($null -eq $total) { $total = $att.totalCount }
    $fileCount = $att.file_count
    if ($null -eq $fileCount) { $fileCount = $att.fileCount }
    if (-not $zipBundle) {
        Write-Host "[OK] attachments zip_bundle=false total=$total page=$fileCount (json export mode)" -ForegroundColor Green
        Write-Host "GET download?part=json ..." -ForegroundColor Cyan
        $jsonRes = Invoke-WebRequest -Uri "$root/download?part=json" -Headers $Headers -Method Get -UseBasicParsing
        if ($jsonRes.StatusCode -ne 200) {
            throw "json part status $($jsonRes.StatusCode)"
        }
        $jsonLen = $jsonRes.RawContentLength
        if (-not $jsonLen -and $jsonRes.Content) { $jsonLen = $jsonRes.Content.Length }
        if ($jsonLen -lt $MinJsonBytes) {
            throw "json part too small ($jsonLen bytes)"
        }
        Write-Host "[OK] json part $jsonLen bytes" -ForegroundColor Green
        return @{
            attachments = $att
            manifest    = $null
            json_bytes  = $jsonLen
            binary_file_id = $null
        }
    }
    Write-Host "[OK] attachments zip_bundle=true total=$total page=$fileCount" -ForegroundColor Green

    Write-Host "GET download?part=manifest ..." -ForegroundColor Cyan
    $manifest = Invoke-RestMethod -Uri "$root/download?part=manifest" -Headers $Headers -Method Get
    $files = @($manifest.files)
    if (-not $files -and $manifest.PSObject.Properties.Name -contains "Files") {
        $files = @($manifest.Files)
    }
    Write-Host "[OK] manifest entries=$($files.Count)" -ForegroundColor Green

    $fileId = $null
    if ($files.Count -gt 0) {
        $first = $files[0]
        $fileId = $first.file_id
        if (-not $fileId) { $fileId = $first.fileId }
    }
    if (-not $fileId -and $att.files -and @($att.files).Count -gt 0) {
        $first = @($att.files)[0]
        $fileId = $first.file_id
        if (-not $fileId) { $fileId = $first.fileId }
    }
    if ($VerifyBinary -or ($fileCount -gt 0 -and $fileId)) {
        if (-not $fileId) {
            throw "attachments: file_count>0 but no file_id in manifest"
        }
        Write-Host "GET download?part=binary file_id=$fileId ..." -ForegroundColor Cyan
        $binRes = Invoke-WebRequest -Uri "$root/download?part=binary&file_id=$fileId" -Headers $Headers `
            -Method Get -UseBasicParsing
        if ($binRes.StatusCode -ne 200) {
            throw "binary part status $($binRes.StatusCode)"
        }
        $binLen = $binRes.RawContentLength
        if (-not $binLen -and $binRes.Content) { $binLen = $binRes.Content.Length }
        if ($binLen -lt $MinBinaryBytes) {
            throw "binary part too small ($binLen bytes)"
        }
        Write-Host "[OK] binary part $binLen bytes" -ForegroundColor Green
    }

    Write-Host "GET download?part=json ..." -ForegroundColor Cyan
    $jsonRes = Invoke-WebRequest -Uri "$root/download?part=json" -Headers $Headers -Method Get -UseBasicParsing
    if ($jsonRes.StatusCode -ne 200) {
        throw "json part status $($jsonRes.StatusCode)"
    }
    $jsonLen = $jsonRes.RawContentLength
    if (-not $jsonLen -and $jsonRes.Content) { $jsonLen = $jsonRes.Content.Length }
    if ($jsonLen -lt $MinJsonBytes) {
        throw "json part too small ($jsonLen bytes)"
    }
    Write-Host "[OK] json part $jsonLen bytes" -ForegroundColor Green

    return @{
        attachments = $att
        manifest    = $manifest
        json_bytes  = $jsonLen
        binary_file_id = $fileId
    }
}
