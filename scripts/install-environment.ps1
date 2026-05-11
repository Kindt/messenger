# Check Windows environment: Docker, Java, Gradle wrapper (ASCII for Windows PowerShell 5.1).
# Install missing tools then verify: .\scripts\install-environment.ps1 -SilentInstall
# Same, minimal output: .\scripts\install-environment.ps1 -SilentInstall -Quiet
# Install only: .\scripts\install-env-silent.ps1   |   Quiet: .\scripts\install-env-silent.ps1 -Quiet
# Help: .\scripts\install-environment.ps1 -Help

param(
    [switch]$SilentInstall,
    [switch]$Quiet,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\scripts\install-environment.ps1 [-SilentInstall] [-Quiet] [-Help]"
    Write-Host "  -SilentInstall: run install-env-silent.ps1 then verify docker/java/gradlew."
    Write-Host "  -Quiet: minimal output (combine with -SilentInstall for silent setup)."
    Write-Host "  Linux/macOS: ./scripts/install-environment.sh --help"
    exit 0
}
$Root = Split-Path -Parent $PSScriptRoot

$korusLib = Join-Path $PSScriptRoot "lib\korus-env.ps1"
if (Test-Path $korusLib) {
    . $korusLib
    Set-KorusPathEnvironment -RepoRoot $Root
}

function Update-SessionPathFromRegistry {
    $machine = [Environment]::GetEnvironmentVariable("Path", "Machine")
    $user = [Environment]::GetEnvironmentVariable("Path", "User")
    if ($machine -or $user) {
        $env:Path = ($machine, $user | Where-Object { $_ }) -join ";"
    }
}

if ($SilentInstall) {
    $silent = Join-Path $PSScriptRoot "install-env-silent.ps1"
    if (-not (Test-Path $silent)) {
        throw "Missing file: $silent"
    }
    if ($Quiet) {
        & $silent -Quiet
    } else {
        & $silent
    }
    Update-SessionPathFromRegistry
    if (-not $Quiet) {
        Write-Host "PATH refreshed for this session; re-open terminal if checks still fail." -ForegroundColor DarkGray
    }
}

if (-not $Quiet) {
    Write-Host "=== Korus Messenger / AvandocMsg -- environment check (Windows) ===" -ForegroundColor Cyan
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "docker not in PATH. Run: .\scripts\install-env-silent.ps1 or see https://docs.docker.com/desktop/install/windows-install/"
}

$javaCmd = Get-Command java -ErrorAction SilentlyContinue
if (-not $javaCmd) {
    throw "java not in PATH. Need JDK 17+ (project uses JDK 25). Run: .\scripts\install-env-silent.ps1"
}

$javaOut = cmd /c "java -version 2>&1"
$major = $null
foreach ($line in $javaOut) {
    if ($line -match 'version\s+"(\d+)') {
        $major = [int]$Matches[1]
        break
    }
    if ($line -match 'version\s+"(\d+)\.(\d+)') {
        $major = [int]$Matches[1]
        break
    }
}
if ($null -eq $major -or $major -lt 17) {
    throw "Java 17+ required. Output: $($javaOut -join ' | ')"
}

if (-not $Quiet) {
    Write-Host "Docker: OK" -ForegroundColor Green
    Write-Host "Java: OK ($($javaOut[0]))" -ForegroundColor Green
}

$gradlewBat = Join-Path $Root "gradlew.bat"
$gradlew = Join-Path $Root "gradlew"
if (-not (Test-Path $gradlewBat) -and -not (Test-Path $gradlew)) {
    Write-Warning "gradlew.bat / gradlew missing in repo root. Clone with wrapper or run: gradle wrapper"
} else {
    if (-not $Quiet) {
        Write-Host "Gradle wrapper: OK" -ForegroundColor Green
    }
}

if ($Quiet) {
    Write-Host "[OK] environment check (docker, java, gradle wrapper)" -ForegroundColor Green
} else {
    Write-Host "Next: .\scripts\create-stand.ps1 min then .\scripts\start.ps1 min (or full; see README.md)." -ForegroundColor DarkGray
    Write-Host "=== Environment check complete ===" -ForegroundColor Cyan
}
