. (Join-Path $PSScriptRoot "Get-KorusQemuStackProfile.ps1")

function Reset-KorusVmDisks {
    param(
        [ValidateSet("dev", "full")]
        [string]$StackProfile = (Get-KorusQemuStackProfile),
        [switch]$IncludeIntegrations
    )
    . (Join-Path $PSScriptRoot "..\config.ps1")
    . (Join-Path $PSScriptRoot "Get-KorusQemuStackProfile.ps1")
    foreach ($role in @("server", "web")) {
        $diskName = Get-KorusVmDiskName -Role $role -StackProfile $StackProfile
        $overlay = Join-Path $KorusQemuImagesDir "$diskName.qcow2"
        if (Test-Path $overlay) {
            Remove-Item -Force $overlay
            Write-Host "Removed overlay ($StackProfile): $overlay" -ForegroundColor DarkGray
        }
    }
    if ($IncludeIntegrations) {
        $intDisk = Join-Path $KorusQemuImagesDir "integrations-dev.qcow2"
        if (Test-Path $intDisk) {
            Remove-Item -Force $intDisk
            Write-Host "Removed overlay: $intDisk" -ForegroundColor DarkGray
        }
    }
}
