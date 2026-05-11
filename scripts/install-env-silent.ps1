# Silent install of missing tooling on Windows via winget: JDK (Temurin 25 / fallbacks), Git, Docker Desktop.
# Run from repo root (admin recommended): .\scripts\install-env-silent.ps1
# Quiet (minimal output): .\scripts\install-env-silent.ps1 -Quiet
# Dry run: .\scripts\install-env-silent.ps1 -WhatIf
# Skip Docker: .\scripts\install-env-silent.ps1 -SkipDocker
# Help: .\scripts\install-env-silent.ps1 -Help

param(
    [switch]$WhatIf,
    [switch]$SkipDocker,
    [switch]$Quiet,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\scripts\install-env-silent.ps1 [-WhatIf] [-SkipDocker] [-Quiet] [-Help]"
    Write-Host "  winget: JDK (Temurin 25 / fallbacks), Git, Docker Desktop. Admin recommended."
    Write-Host "  Linux/macOS: ./scripts/install-env-silent.sh --help"
    exit 0
}

function Test-CommandExists([string]$Name) {
    return [bool](Get-Command $Name -ErrorAction SilentlyContinue)
}

function Get-JavaMajor {
    if (-not (Test-CommandExists "java")) {
        return $null
    }
    $javaOut = cmd /c "java -version 2>&1"
    foreach ($line in $javaOut) {
        if ($line -match 'version\s+"(\d+)') {
            return [int]$Matches[1]
        }
        if ($line -match 'version\s+"(\d+)\.(\d+)') {
            return [int]$Matches[1]
        }
    }
    return $null
}

function Update-SessionPath {
    $machine = [Environment]::GetEnvironmentVariable("Path", "Machine")
    $user = [Environment]::GetEnvironmentVariable("Path", "User")
    if ($machine -or $user) {
        $env:Path = ($machine, $user | Where-Object { $_ }) -join ";"
    }
}

function Invoke-WingetInstall {
    param(
        [Parameter(Mandatory)][string]$Id,
        [Parameter(Mandatory)][string]$Title
    )
    if ($WhatIf) {
        if (-not $Quiet) {
            Write-Host "[WhatIf] winget install --id $Id" -ForegroundColor Yellow
        }
        return
    }
    if (-not $Quiet) {
        Write-Host "Installing: $Title ($Id) ..." -ForegroundColor Cyan
    }
    $wingetArgs = @(
        "install", "--id", $Id, "-e",
        "--silent",
        "--accept-package-agreements",
        "--accept-source-agreements",
        "--disable-interactivity"
    )
    & winget @wingetArgs | Out-Null
    $code = $LASTEXITCODE
    if ($code -ne 0 -and -not $Quiet) {
        Write-Host "  winget exit code $code (package may already be installed)." -ForegroundColor DarkYellow
    }
}

if (-not $Quiet) {
    Write-Host "=== Silent environment install (Windows, winget) ===" -ForegroundColor Cyan
}

if (-not (Test-CommandExists "winget")) {
    throw "winget not found. Install App Installer from Microsoft Store or use Windows 10/11 with winget."
}

$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
    [Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin -and -not $Quiet) {
    Write-Warning "Not running elevated: Docker Desktop and machine-wide PATH updates may fail."
}

$javaMajor = Get-JavaMajor
if ($null -eq $javaMajor -or $javaMajor -lt 17) {
    $jdkIds = @(
        "EclipseAdoptium.Temurin.25.JDK",
        "EclipseAdoptium.Temurin.21.JDK",
        "Microsoft.OpenJDK.21"
    )
    $installed = $false
    foreach ($id in $jdkIds) {
        Invoke-WingetInstall -Id $id -Title "JDK"
        Update-SessionPath
        $javaMajor = Get-JavaMajor
        if ($null -ne $javaMajor -and $javaMajor -ge 17) {
            $installed = $true
            break
        }
        if (-not $Quiet) {
            Write-Warning "After $id : java still missing or major < 17, trying next package..."
        }
    }
    if (-not $installed -and -not $WhatIf) {
        throw "Could not install JDK 17+ via winget. Install JDK 25 manually: https://adoptium.net/"
    }
} else {
    if (-not $Quiet) {
        Write-Host "Java already on PATH (major $javaMajor)." -ForegroundColor Green
    }
}

if (-not (Test-CommandExists "git")) {
    Invoke-WingetInstall -Id "Git.Git" -Title "Git"
    Update-SessionPath
} else {
    if (-not $Quiet) {
        Write-Host "Git already on PATH." -ForegroundColor Green
    }
}

if (-not $SkipDocker) {
    if (-not (Test-CommandExists "docker")) {
        Invoke-WingetInstall -Id "Docker.DockerDesktop" -Title "Docker Desktop"
        if (-not $Quiet) {
            Write-Warning "After Docker Desktop install: sign out or reboot, start Docker Desktop (WSL2, license)."
        }
    } else {
        if (-not $Quiet) {
            Write-Host "Docker already on PATH." -ForegroundColor Green
        }
    }
} else {
    if (-not $Quiet) {
        Write-Host "Skipping Docker (-SkipDocker)." -ForegroundColor DarkGray
    }
}

Update-SessionPath

$bs = Join-Path $PSScriptRoot "lib\Bootstrap-DevEnv.ps1"
if (Test-Path -LiteralPath $bs) {
    . $bs
    Initialize-KorusDevToolPaths
}

if ($Quiet) {
    Write-Host "[OK] install-env-silent (quiet). Re-open terminal if needed; then: .\scripts\install-environment.ps1 -Quiet" -ForegroundColor Green
} else {
    Write-Host "=== Done. Re-open terminal if needed, then: .\scripts\install-environment.ps1 ===" -ForegroundColor Cyan
}
