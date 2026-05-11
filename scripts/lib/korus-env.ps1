# Shared: KORUS_* path env vars, optional tooling install, docker compose with retry.
# Dot-source: . (Join-Path $PSScriptRoot "lib\korus-env.ps1")

function Set-KorusPathEnvironment {
    param([Parameter(Mandatory)][string]$RepoRoot)
    $dockerDir = Join-Path $RepoRoot "docker"
    $env:KORUS_REPO_ROOT = $RepoRoot
    $env:KORUS_DOCKER_DIR = $dockerDir
    $env:KORUS_COMPOSE_DEV_MIN = Join-Path $dockerDir "docker-compose.dev-min.yml"
    $env:KORUS_COMPOSE_FULL_SERVER = Join-Path $dockerDir "docker-compose.full-server.yml"
    $env:KORUS_SCRIPTS_DIR = Join-Path $RepoRoot "scripts"
    $kw = Join-Path $RepoRoot "korus-web"
    $env:KORUS_KORUS_WEB_DIR = $kw
    $env:KORUS_KORUS_WEB_COMPOSE = Join-Path $kw "docker-compose.yml"
    $env:KORUS_KORUS_WEB_COMPOSE_ATTACH = Join-Path $kw "docker-compose.attach.yml"
}

function Update-KorusSessionPathFromMachine {
    $machine = [Environment]::GetEnvironmentVariable("Path", "Machine")
    $user = [Environment]::GetEnvironmentVariable("Path", "User")
    if ($machine -or $user) {
        $env:Path = ($machine, $user | Where-Object { $_ }) -join ";"
    }
}

function Invoke-KorusEnsureDevTooling {
    param([Parameter(Mandatory)][string]$ScriptsRoot)
    $check = Join-Path $ScriptsRoot "install-environment.ps1"
    $silent = Join-Path $ScriptsRoot "install-env-silent.ps1"
    if (-not (Test-Path $check)) {
        throw "Missing: $check"
    }
    try {
        & $check -Quiet
        return
    } catch {
        if (-not (Test-Path $silent)) {
            throw $_
        }
        & $silent -Quiet
        Update-KorusSessionPathFromMachine
        & $check -Quiet
    }
}

function Invoke-KorusDockerComposeUp {
    param(
        [Parameter(Mandatory)][string]$ComposeFile,
        [switch]$Build,
        [int]$Retries = 2
    )
    $dockerArgs = @("compose", "-f", $ComposeFile, "up", "-d")
    if ($Build) {
        $dockerArgs += "--build"
    }
    for ($attempt = 1; $attempt -le $Retries; $attempt++) {
        & docker @dockerArgs
        if ($LASTEXITCODE -eq 0) {
            return
        }
        if ($attempt -lt $Retries) {
            Write-Host "docker compose exit $LASTEXITCODE; retry in 10s ($attempt/$Retries)..." -ForegroundColor Yellow
            Start-Sleep -Seconds 10
        }
    }
    throw "docker compose failed after $Retries attempts (last exit: $LASTEXITCODE)"
}

function Invoke-KorusDockerComposeInvoke {
    param(
        [Parameter(Mandatory)][string[]]$DockerArgs,
        [int]$Retries = 2,
        [string]$WorkingDirectory = ""
    )
    $pushed = $false
    if ($WorkingDirectory) {
        Push-Location $WorkingDirectory
        $pushed = $true
    }
    try {
        for ($attempt = 1; $attempt -le $Retries; $attempt++) {
            & docker @DockerArgs
            if ($LASTEXITCODE -eq 0) {
                return
            }
            if ($attempt -lt $Retries) {
                Write-Host "docker compose exit $LASTEXITCODE; retry in 10s ($attempt/$Retries)..." -ForegroundColor Yellow
                Start-Sleep -Seconds 10
            }
        }
        throw "docker compose failed after $Retries attempts (last exit: $LASTEXITCODE)"
    } finally {
        if ($pushed) {
            Pop-Location
        }
    }
}
