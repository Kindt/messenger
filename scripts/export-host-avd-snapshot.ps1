# Export quickboot snapshot from host Android Studio AVD (WHPX) for guest import.
# Output: deploy/mobile/avd-snapshot/korus_api28-boot.tgz
param(
    [string]$HostAvd = 'korus_host_api28',
    [string]$GuestAvd = 'korus_api28',
    [string]$SnapshotName = 'boot',
    [switch]$SkipBoot,
    [switch]$Help
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$OutDir = Join-Path $Root 'deploy\mobile\avd-snapshot'
$OutTgz = Join-Path $OutDir "${GuestAvd}-${SnapshotName}.tgz"

if ($Help) {
    Write-Host @"
Usage: .\scripts\export-host-avd-snapshot.ps1 [-HostAvd korus_host_api28]

Boots host AVD (WHPX), saves quickboot snapshot, packs for guest import:
  .\scripts\qemu-mobile-avd-snapshot.ps1 import

Requires Android SDK emulator on host. First run ~3-8 min cold boot.
"@
    exit 0
}

$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$adb = Join-Path $sdk 'platform-tools\adb.exe'
$emulator = Join-Path $sdk 'emulator\emulator.exe'
if (-not (Test-Path $emulator)) { throw "emulator.exe not found under $sdk" }

$avdDir = Join-Path $env:USERPROFILE ".android\avd\${HostAvd}.avd"
$snapDir = Join-Path $avdDir "snapshots\$SnapshotName"
if (-not (Test-Path $avdDir)) { throw "Host AVD dir missing: $avdDir" }

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$prevEap = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
$devices = & $adb devices 2>&1 | Select-String '^\S+\s+device$'
$ErrorActionPreference = $prevEap

if (-not $SkipBoot -and -not $devices) {
    Write-Host "Starting host AVD '$HostAvd' (WHPX, cold boot for clean snapshot)..." -ForegroundColor Cyan
    Start-Process -FilePath $emulator -ArgumentList @(
        '-avd', $HostAvd,
        '-no-window',
        '-gpu', 'swiftshader_indirect',
        '-no-audio',
        '-no-boot-anim',
        '-no-metrics',
        '-no-snapshot-load',
        '-no-snapshot-save'
    ) -WindowStyle Hidden
    Write-Host 'Waiting for adb device (up to 300s)...' -ForegroundColor DarkGray
    $deadline = (Get-Date).AddSeconds(300)
    while ((Get-Date) -lt $deadline) {
        $ErrorActionPreference = 'Continue'
        $boot = (& $adb shell getprop sys.boot_completed 2>$null).Trim()
        $devices = & $adb devices 2>&1 | Select-String '^\S+\s+device$'
        $ErrorActionPreference = $prevEap
        if ($devices -and $boot -eq '1') { break }
        Start-Sleep -Seconds 5
    }
    if (-not $devices) { throw 'Host emulator did not become ready' }
    Write-Host '[OK] Host emulator booted' -ForegroundColor Green
}

Write-Host "Saving snapshot '$SnapshotName'..." -ForegroundColor Cyan
$ErrorActionPreference = 'Continue'
& $adb -e emu avd snapshot save $SnapshotName 2>&1 | Out-Host
$ErrorActionPreference = $prevEap
if (-not (Test-Path $snapDir)) { throw "Snapshot dir missing after save: $snapDir" }

Write-Host "Packing $OutTgz ..." -ForegroundColor Cyan
if (Test-Path $OutTgz) { Remove-Item $OutTgz -Force }
$tar = Get-Command tar -ErrorAction SilentlyContinue
if ($tar) {
    Push-Location (Join-Path $avdDir 'snapshots')
    try {
        & tar -czf $OutTgz $SnapshotName
    } finally {
        Pop-Location
    }
} else {
    throw 'tar not found on PATH (Windows 10+ includes tar.exe)'
}

Get-Item $OutTgz | Format-List FullName, Length, LastWriteTime
Write-Host '[OK] Import on guest: .\scripts\qemu-mobile-avd-snapshot.ps1 import' -ForegroundColor Green
