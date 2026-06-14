function Get-KorusQemuStackProfile {
    if ($env:KORUS_QEMU_STACK_PROFILE -match '^(dev|full)$') {
        return $Matches[1]
    }
    . (Join-Path $PSScriptRoot "..\config.ps1")
    $profileFile = Join-Path $KorusQemuRunDir "stack-profile.txt"
    if (Test-Path $profileFile) {
        $p = (Get-Content $profileFile -Raw).Trim()
        if ($p -match '^(dev|full)$') { return $Matches[1] }
    }
    return "dev"
}

function Set-KorusQemuStackProfile {
    param(
        [Parameter(Mandatory)]
        [ValidateSet("dev", "full")]
        [string]$Profile
    )
    . (Join-Path $PSScriptRoot "..\config.ps1")
    New-Item -ItemType Directory -Force -Path $KorusQemuRunDir | Out-Null
    $profileFile = Join-Path $KorusQemuRunDir "stack-profile.txt"
    Set-Content -Path $profileFile -Value $Profile -Encoding ascii -NoNewline
    Add-Content -Path $profileFile -Value "" -Encoding ascii
    $env:KORUS_QEMU_STACK_PROFILE = $Profile
}

function Get-KorusVmDiskName {
    param(
        [Parameter(Mandatory)]
        [ValidateSet("server", "web")]
        [string]$Role,
        [string]$StackProfile = (Get-KorusQemuStackProfile)
    )
    return "${Role}-${StackProfile}"
}

function Initialize-KorusLegacyVmDisks {
    . (Join-Path $PSScriptRoot "..\config.ps1")
    foreach ($role in @("server", "web")) {
        $legacy = Join-Path $KorusQemuImagesDir "$role.qcow2"
        $devDisk = Join-Path $KorusQemuImagesDir "${role}-dev.qcow2"
        if ((Test-Path $legacy) -and -not (Test-Path $devDisk)) {
            Move-Item -Path $legacy -Destination $devDisk -Force
            Write-Host "Migrated legacy disk: $role.qcow2 -> ${role}-dev.qcow2" -ForegroundColor Yellow
        }
    }
}

function Test-KorusQemuProfileDisksExist {
    param(
        [Parameter(Mandatory)]
        [ValidateSet("dev", "full")]
        [string]$StackProfile
    )
    . (Join-Path $PSScriptRoot "..\config.ps1")
    foreach ($role in @("server", "web")) {
        $disk = Join-Path $KorusQemuImagesDir "${role}-${StackProfile}.qcow2"
        if (-not (Test-Path $disk)) { return $false }
    }
    return $true
}

function Stop-KorusQemuIfProfileMismatch {
    param(
        [Parameter(Mandatory)]
        [ValidateSet("dev", "full")]
        [string]$TargetProfile,
        [Parameter(Mandatory)]
        [string]$RunDir,
        [Parameter(Mandatory)]
        [string]$QemuDownScript
    )
    . (Join-Path $PSScriptRoot "Test-KorusQemuProcess.ps1")
    if (-not (Test-KorusQemuStackRunning -RunDir $RunDir)) { return }
    $active = Get-KorusQemuStackProfile
    if ($active -eq $TargetProfile) { return }
    Write-Host "Stack profile '$active' is running; need '$TargetProfile'. Stopping VMs..." -ForegroundColor Yellow
    & $QemuDownScript
    if ($LASTEXITCODE -ne 0) { throw "qemu-down failed (exit $LASTEXITCODE)" }
}
