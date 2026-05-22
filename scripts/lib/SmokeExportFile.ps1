# Dot-source: upload file + post file message for export smokes.

function New-ComplianceSmokeFileBytes {
    param([string]$Label = "export-compliance")
    $text = "$Label attachment " + (Get-Date -Format o)
    return [System.Text.Encoding]::UTF8.GetBytes($text)
}

function Send-KorusFileMessage {
    param(
        [Parameter(Mandatory = $true)]
        [string]$BaseUrl,
        [Parameter(Mandatory = $true)]
        [hashtable]$Headers,
        [Parameter(Mandatory = $true)]
        [string]$ChatId,
        [Parameter(Mandatory = $true)]
        [string]$FileId,
        [ValidateSet("file", "image", "video")]
        [string]$MessageType = "file"
    )
    $body = @{
        type    = $MessageType
        content = $FileId
    } | ConvertTo-Json
    return Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats/$ChatId/messages" -Method Post -Headers $Headers `
        -ContentType "application/json; charset=utf-8" -Body $body
}

function Upload-KorusComplianceFile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$BaseUrl,
        [Parameter(Mandatory = $true)]
        [hashtable]$Headers,
        [string]$Filename = "compliance-smoke.txt",
        [string]$MimeType = "text/plain",
        [byte[]]$Bytes = $(New-ComplianceSmokeFileBytes)
    )
    $uploadHeaders = @{}
    foreach ($k in $Headers.Keys) { $uploadHeaders[$k] = $Headers[$k] }
    $uploadHeaders["Content-Type"] = $MimeType
    $uploadHeaders["X-Filename"] = $Filename
    $upload = Invoke-RestMethod -Uri "$BaseUrl/api/v1/files/upload" -Method Post -Headers $uploadHeaders -Body $Bytes
    $fileId = $upload.id
    if (-not $fileId) { $fileId = $upload.file_id }
    if (-not $fileId) { $fileId = $upload.fileId }
    if (-not $fileId) { throw "upload: no file id" }
    return $fileId
}
