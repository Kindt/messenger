# One-shot: lab API + host Android Studio AVD window + Korus APK on screen.
# Standalone visual launch on Windows (NOT QEMU mobile-build headless lab).
param(
    [switch]$Help,
    [switch]$NoStartStack,
    [switch]$NoLaunchEmulator,
    [string]$Avd = '',
    [switch]$SkipBuild,
    [switch]$InstallOnly,
    [switch]$RunMaestro,
    [string]$ApiHealth = 'http://127.0.0.1:18080/api/v1/health'
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot

if ($Help) {
    Write-Host @"
Usage: .\scripts\mobile-android-studio.ps1

Standalone host launch (default):
  1) start QEMU lab API :18080 if down
  2) start Android Studio AVD window if no adb device
  3) assembleDebug + adb install + am start

Options:
  -NoStartStack       do not call qemu-up
  -NoLaunchEmulator   do not start emulator.exe (use running AVD)
  -Avd <name>         AVD name (default: first from emulator -list-avds)
  -SkipBuild          skip Gradle; use deploy\mobile\run\korus-mobile-debug.apk
  -InstallOnly        same as -SkipBuild for install path
  -RunMaestro         maestro test mobile\maestro\w0-login.yaml after launch

Doc: deploy\mobile\android-studio-host.md
"@
    exit 0
}

function Find-AndroidHome {
    $candidates = @(
        $env:ANDROID_HOME,
        $env:ANDROID_SDK_ROOT,
        (Join-Path $env:LOCALAPPDATA 'Android\Sdk'),
        (Join-Path $env:USERPROFILE 'AppData\Local\Android\Sdk')
    ) | Where-Object { $_ -and (Test-Path $_) }
    if ($candidates.Count -gt 0) { return $candidates[0] }
    return $null
}

function Test-LabApi {
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $r = Invoke-WebRequest -Uri $ApiHealth -UseBasicParsing -TimeoutSec 12 -Method Get
        return ($r.StatusCode -ge 200 -and $r.StatusCode -lt 300)
    } catch {
        return $false
    } finally {
        $ErrorActionPreference = $prev
    }
}

$androidHome = Find-AndroidHome
if (-not $androidHome) {
    Write-Host '[FAIL] ANDROID_HOME not found. Install Android Studio + SDK.' -ForegroundColor Red
    Write-Host '  https://developer.android.com/studio' -ForegroundColor DarkGray
    Write-Host '  Doc: deploy\mobile\android-studio-host.md' -ForegroundColor DarkGray
    exit 2
}

$adb = Join-Path $androidHome 'platform-tools\adb.exe'
$emulator = Join-Path $androidHome 'emulator\emulator.exe'
if (-not (Test-Path $adb)) {
    Write-Host "[FAIL] adb not found: $adb" -ForegroundColor Red
    Write-Host '  Android Studio -> SDK Manager -> Android SDK Platform-Tools' -ForegroundColor DarkGray
    exit 2
}

Write-Host '=== mobile-android-studio (standalone host launch) ===' -ForegroundColor Cyan
Write-Host "ANDROID_HOME=$androidHome" -ForegroundColor DarkGray

if (-not (Test-LabApi)) {
    if ($NoStartStack) {
        Write-Host "[FAIL] Lab API not ready at $ApiHealth - omit -NoStartStack or run qemu-up" -ForegroundColor Red
        exit 2
    }
    Write-Host 'Lab API down - starting QEMU server stack...' -ForegroundColor Yellow
    & (Join-Path $Root 'scripts\qemu-up.ps1') -KeepDisks
    if (-not (Test-LabApi)) {
        Write-Host "[FAIL] Lab API still not ready at $ApiHealth" -ForegroundColor Red
        exit 2
    }
}
Write-Host '[OK] Lab API' -ForegroundColor Green

$apkDir = Join-Path $Root 'mobile\mobile-client-android\build\outputs\apk\debug'
$packApk = Join-Path $Root 'deploy\mobile\run\korus-mobile-debug.apk'
New-Item -ItemType Directory -Force -Path (Split-Path $packApk) | Out-Null

if (-not $SkipBuild -and -not $InstallOnly) {
    Write-Host 'Building debug APK (Gradle)...' -ForegroundColor Cyan
    Push-Location $Root
    try {
        & .\gradlew.bat ':mobile:mobile-client-android:assembleDebug' --no-daemon
        if ($LASTEXITCODE -ne 0) { throw "gradlew exit $LASTEXITCODE" }
    } finally {
        Pop-Location
    }
    $built = Get-ChildItem -Path $apkDir -Filter '*-debug.apk' -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $built) {
        Write-Host "[FAIL] APK not found under $apkDir" -ForegroundColor Red
        exit 2
    }
    Copy-Item -LiteralPath $built.FullName -Destination $packApk -Force
    Write-Host "[OK] APK $($built.Name) -> $packApk" -ForegroundColor Green
}

if (-not (Test-Path $packApk)) {
    Write-Host "[FAIL] APK missing: $packApk (run without -SkipBuild)" -ForegroundColor Red
    exit 2
}

$adbDir = Split-Path $adb
$env:PATH = "$adbDir;$env:PATH"

$prevEap = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
try {
    $null = & $adb start-server 2>&1
    $devices = & $adb devices 2>&1 | Select-String '^\S+\s+device$'
} finally {
    $ErrorActionPreference = $prevEap
}
if (-not $devices) {
    if ($NoLaunchEmulator) {
        Write-Host '[FAIL] No adb device. Start AVD in Android Studio or omit -NoLaunchEmulator' -ForegroundColor Red
        exit 2
    }
    if (-not (Test-Path $emulator)) {
        Write-Host "[FAIL] emulator.exe not found: $emulator" -ForegroundColor Red
        Write-Host '  Android Studio -> SDK Manager -> Android Emulator + system image' -ForegroundColor DarkGray
        exit 2
    }
    $ErrorActionPreference = 'Continue'
    $avds = @(& $emulator -list-avds 2>&1 | ForEach-Object { "$_".Trim() } | Where-Object { $_ })
    $ErrorActionPreference = $prevEap
    $pick = $Avd
    if (-not $pick) { $pick = $avds | Select-Object -First 1 }
    if (-not $pick) {
        Write-Host '[FAIL] No AVD - create one in Android Studio Device Manager' -ForegroundColor Red
        exit 2
    }
    Write-Host "Starting AVD '$pick' (window on screen)..." -ForegroundColor Cyan
    Start-Process -FilePath $emulator -ArgumentList @('-avd', $pick) -WindowStyle Normal
    Write-Host 'Waiting for adb device (up to 180s)...' -ForegroundColor DarkGray
    $ErrorActionPreference = 'Continue'
    & $adb wait-for-device 2>&1 | Out-Null
    $deadline = (Get-Date).AddSeconds(180)
    while ((Get-Date) -lt $deadline) {
        $boot = & $adb shell getprop sys.boot_completed 2>$null
        if ("$boot".Trim() -eq '1') { break }
        Start-Sleep -Seconds 3
    }
    $devices = & $adb devices 2>&1 | Select-String '^\S+\s+device$'
    $ErrorActionPreference = $prevEap
}

$ErrorActionPreference = 'Continue'
$devices = & $adb devices 2>&1 | Select-String '^\S+\s+device$'
$ErrorActionPreference = $prevEap
if (-not $devices) {
    Write-Host '[FAIL] No adb device after emulator start' -ForegroundColor Red
    exit 2
}
Write-Host "[OK] adb: $($devices.Line)" -ForegroundColor Green

Write-Host 'Installing APK...' -ForegroundColor Cyan
$ErrorActionPreference = 'Continue'
& $adb install -r -d $packApk 2>&1 | Out-Host
$installRc = $LASTEXITCODE
if ($installRc -ne 0) {
    Write-Host 'Stream install failed - trying push + pm install...' -ForegroundColor Yellow
    & $adb push $packApk /data/local/tmp/korus-mobile-debug.apk 2>&1 | Out-Host
    & $adb shell pm install -r -d /data/local/tmp/korus-mobile-debug.apk 2>&1 | Out-Host
    $installRc = $LASTEXITCODE
}
$ErrorActionPreference = $prevEap
if ($installRc -ne 0) {
    Write-Host '[FAIL] adb install failed' -ForegroundColor Red
    exit 2
}

$component = 'com.avandocmsg.messenger.mobile/.MainActivity'
Write-Host "Launching $component ..." -ForegroundColor Cyan
$ErrorActionPreference = 'Continue'
& $adb shell am start -n $component 2>&1 | Out-Host
$ErrorActionPreference = $prevEap
Write-Host '[OK] Korus Messenger should be visible on the emulator window.' -ForegroundColor Green
Write-Host 'API URL in app: http://10.0.2.2:18080' -ForegroundColor DarkGray

function Install-MaestroHost {
    $maestroRoot = Join-Path $env:USERPROFILE '.maestro'
    $zipUrl = 'https://github.com/mobile-dev-inc/maestro/releases/latest/download/maestro.zip'
    $zipPath = Join-Path $env:TEMP 'maestro.zip'
    Write-Host 'Downloading Maestro from GitHub (Windows native)...' -ForegroundColor Yellow
    Invoke-WebRequest -Uri $zipUrl -UseBasicParsing -OutFile $zipPath
    if (Test-Path $maestroRoot) {
        Remove-Item $maestroRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
    Expand-Archive -Path $zipPath -DestinationPath $maestroRoot -Force
    $bat = Get-ChildItem -Path $maestroRoot -Recurse -Filter 'maestro.bat' -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if (-not $bat) { throw 'maestro.bat not found after extract' }
    return $bat.DirectoryName
}

if ($RunMaestro) {
    $maestro = Get-Command maestro -ErrorAction SilentlyContinue
    if (-not $maestro) {
        $maestroBin = Join-Path $env:USERPROFILE '.maestro\bin'
        if (Test-Path (Join-Path $maestroBin 'maestro.bat')) {
            $env:PATH = "$maestroBin;$env:PATH"
            $maestro = Get-Command maestro -ErrorAction SilentlyContinue
        }
    }
    if (-not $maestro) {
        $prevEap = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        try {
            $maestroBin = Install-MaestroHost
            $env:PATH = "$maestroBin;$env:PATH"
            $maestro = Get-Command maestro -ErrorAction SilentlyContinue
        } catch {
            Write-Host "[WARN] Maestro install failed: $($_.Exception.Message)" -ForegroundColor Yellow
        } finally {
            $ErrorActionPreference = $prevEap
        }
    }
    if (-not $maestro) {
        Write-Host '[WARN] maestro CLI not in PATH - skip or install from maestro.mobile.dev' -ForegroundColor Yellow
    } else {
        $prevEapMaestro = $ErrorActionPreference
        Write-Host 'Clearing app data for deterministic Maestro W0...' -ForegroundColor DarkGray
        $ErrorActionPreference = 'Continue'
        & $adb shell pm clear com.avandocmsg.messenger.mobile 2>&1 | Out-Null
        & $adb shell am start -n $component 2>&1 | Out-Null
        Start-Sleep -Seconds 3
        $ErrorActionPreference = $prevEapMaestro
        Write-Host 'Running Maestro w0-login...' -ForegroundColor Cyan
        & maestro test (Join-Path $Root 'mobile\maestro\w0-login.yaml')
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
        Write-Host '[OK] Maestro PASS' -ForegroundColor Green
    }
}
