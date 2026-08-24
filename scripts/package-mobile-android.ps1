#Requires -Version 5.1
param([switch]$Help, [switch]$ForceHost)
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$OutApk = Join-Path $Root "deploy\mobile\run\korus-mobile-debug.apk"

if ($Help) {
    Write-Host @"
Usage: .\scripts\package-mobile-android.ps1 [-ForceHost]

Default: build on korus-mobile-build QEMU guest if VM is up (:12224).
-ForceHost: build on Windows host (requires local Android SDK).
"@
    exit 0
}

if (-not $ForceHost) {
    $tcp = Test-NetConnection -ComputerName 127.0.0.1 -Port 12224 -WarningAction SilentlyContinue -ErrorAction SilentlyContinue
    if ($tcp.TcpTestSucceeded) {
        & (Join-Path $Root "scripts\qemu-mobile-build-android.ps1") -Wait
        $Plink = "${env:ProgramFiles}\PuTTY\plink.exe"
        $RunDir = Join-Path $Root "deploy\qemu\run"
        . (Join-Path $Root "deploy\qemu\lib\Update-KorusGuestRepo.ps1")
        $hk = Get-KorusEd25519HostKey -SerialPath (Join-Path $RunDir "mobile-build-serial.log") -Role mobile-build -SshPort 12224
        if ($hk -and (Test-Path $Plink)) {
            New-Item -ItemType Directory -Force -Path (Split-Path $OutApk) | Out-Null
            & $Plink -batch -hostkey $hk -pw korus -P 12224 korus@127.0.0.1 "cat /mnt/korus/deploy/mobile/run/korus-mobile-debug.apk" > $OutApk
            if ($LASTEXITCODE -eq 0 -and (Test-Path $OutApk) -and (Get-Item $OutApk).Length -gt 1000) {
                Write-Host "[OK] package-mobile-android -> $OutApk" -ForegroundColor Green
                exit 0
            }
        }
        Write-Host "[OK] APK on guest; pull manually if needed" -ForegroundColor Green
        exit 0
    }
}

$gradlew = Join-Path $Root "gradlew.bat"
if (-not (Test-Path $gradlew)) { throw "gradlew.bat missing" }

Push-Location $Root
& $gradlew :mobile:mobile-client-android:assembleDebug --no-daemon
$code = $LASTEXITCODE
Pop-Location
if ($code -ne 0) { throw "assembleDebug failed on host" }
Write-Host "[OK] package-mobile-android (host build)" -ForegroundColor Green
