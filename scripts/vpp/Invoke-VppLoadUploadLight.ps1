#Requires -Version 5.1
# Light parallel upload load (VPP fortress).
param(
    [string]$ApiBaseUrl = "http://127.0.0.1:18080",
    [int]$Parallel = 2,
    [int]$UploadsPerWorker = 2,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\scripts\vpp\Invoke-VppLoadUploadLight.ps1"
    exit 0
}

$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
& (Join-Path $Root "scripts\load-api-upload.ps1") `
    -BaseUrl $ApiBaseUrl -Parallel $Parallel -UploadsPerWorker $UploadsPerWorker -FileSizeKb 128
exit $LASTEXITCODE
