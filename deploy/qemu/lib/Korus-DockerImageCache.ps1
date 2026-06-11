# Pre-pull Docker base images on Windows host; guests load via HTTP (10.0.2.2:18890).
. (Join-Path $PSScriptRoot "..\config.ps1")

$script:KorusQemuDockerCacheImages = @(
    "gradle:jdk25-noble",
    "eclipse-temurin:25-jre",
    "postgres:16-alpine",
    "redis:7-alpine",
    "nats:2.10-alpine",
    "minio/minio:latest",
    "zookeeper:3.9",
    "solr:9.4",
    "quay.io/keycloak/keycloak:24.0",
    "nginx:1.27-alpine"
)

function Get-KorusDockerCachePaths {
    $tar = Join-Path $KorusQemuRunDir "docker-base-images.tar"
    $staging = Join-Path $KorusQemuRunDir "docker-base-images.staging.tar"
    $manifest = Join-Path $KorusQemuRunDir "docker-base-images.manifest"
    $pidFile = Join-Path $KorusQemuRunDir "docker-cache.pid"
    $log = Join-Path $KorusQemuRunDir "docker-cache.log"
    return @{
        Tar       = $tar
        Staging   = $staging
        Manifest  = $manifest
        PidFile   = $pidFile
        Log       = $log
    }
}

function Test-KorusDockerOnHost {
    if ($env:KORUS_QEMU_SKIP_DOCKER_CACHE -eq "1") { return $false }
    $docker = Get-Command docker -ErrorAction SilentlyContinue
    if (-not $docker) { return $false }
    try {
        & docker version --format "{{.Server.Version}}" 2>$null | Out-Null
        return $LASTEXITCODE -eq 0
    } catch {
        return $false
    }
}

function Test-KorusDockerCacheFresh {
    param(
        [hashtable]$Paths,
        [int]$MaxAgeHours = 72
    )
    if (-not (Test-Path $Paths.Tar)) { return $false }
    if (-not (Test-Path $Paths.Manifest)) { return $false }
    $age = (Get-Date) - (Get-Item $Paths.Tar).LastWriteTime
    if ($age.TotalHours -gt $MaxAgeHours) { return $false }
    $saved = Get-Content $Paths.Manifest -Raw
    $expected = ($script:KorusQemuDockerCacheImages -join "`n")
    return ($saved.Trim() -eq $expected.Trim())
}

function Build-KorusDockerImageCache {
    param(
        [switch]$Force,
        [switch]$Quiet
    )

    $paths = Get-KorusDockerCachePaths
    New-Item -ItemType Directory -Force -Path $KorusQemuRunDir | Out-Null

    if (-not (Test-KorusDockerOnHost)) {
        if (-not $Quiet) {
            Write-Host "Docker cache: skipped (docker not on host or KORUS_QEMU_SKIP_DOCKER_CACHE=1)" -ForegroundColor DarkGray
        }
        return $null
    }

    if ((Test-KorusDockerCacheFresh -Paths $paths) -and -not $Force) {
        $mb = [math]::Round((Get-Item $paths.Tar).Length / 1MB, 1)
        if (-not $Quiet) {
            Write-Host "Docker cache: reusing docker-base-images.tar ($mb MiB)" -ForegroundColor DarkGray
        }
        return $paths.Tar
    }

    if (Test-Path $paths.PidFile) {
        $bpid = [int](Get-Content $paths.PidFile -Raw).Trim()
        if ((Get-Process -Id $bpid -ErrorAction SilentlyContinue) -and -not $Force) {
            if (-not $Quiet) {
                Write-Host "Docker cache: build already running (PID $bpid)" -ForegroundColor DarkGray
            }
            return $paths.Tar
        }
    }

    $ts = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    "[$ts] docker cache build start" | Add-Content -Path $paths.Log -Encoding utf8

    if (-not $Quiet) {
        Write-Host "Docker cache: pulling $($script:KorusQemuDockerCacheImages.Count) base images (linux/amd64)..." -ForegroundColor Cyan
    }

    $pulled = @()
    foreach ($img in $script:KorusQemuDockerCacheImages) {
        if (-not $Quiet) { Write-Host "  pull $img" -ForegroundColor DarkGray }
        & docker pull --platform linux/amd64 $img 2>&1 | Add-Content -Path $paths.Log -Encoding utf8
        if ($LASTEXITCODE -eq 0) {
            $pulled += $img
        } else {
            "[$ts] WARN pull failed: $img" | Add-Content -Path $paths.Log -Encoding utf8
            if (-not $Quiet) {
                Write-Host "  WARN pull failed: $img" -ForegroundColor Yellow
            }
        }
    }

    if ($pulled.Count -eq 0) {
        "[$ts] ERROR no images pulled" | Add-Content -Path $paths.Log -Encoding utf8
        if (-not $Quiet) { Write-Host "Docker cache: no images pulled" -ForegroundColor Yellow }
        return $null
    }

    if (Test-Path $paths.Staging) { Remove-Item -Force $paths.Staging }
    if (-not $Quiet) { Write-Host "Docker cache: saving $($pulled.Count) images to tar..." -ForegroundColor Cyan }
    & docker save -o $paths.Staging @pulled 2>&1 | Add-Content -Path $paths.Log -Encoding utf8
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $paths.Staging)) {
        "[$ts] ERROR docker save failed" | Add-Content -Path $paths.Log -Encoding utf8
        if (-not $Quiet) { Write-Host "Docker cache: docker save failed (see $($paths.Log))" -ForegroundColor Yellow }
        return $null
    }

    Move-Item -Force $paths.Staging $paths.Tar
    $script:KorusQemuDockerCacheImages -join "`n" | Set-Content -Path $paths.Manifest -Encoding ascii -NoNewline
    $mb = [math]::Round((Get-Item $paths.Tar).Length / 1MB, 1)
    "[$ts] OK docker-base-images.tar $mb MiB ($($pulled.Count) images)" | Add-Content -Path $paths.Log -Encoding utf8
    if (-not $Quiet) {
        Write-Host "Docker cache: $($paths.Tar) ($mb MiB)" -ForegroundColor Green
        Write-Host "  Guest: curl http://10.0.2.2:${KorusQemuRepoHttpPort}/docker-base-images.tar | docker load" -ForegroundColor DarkGray
    }
    return $paths.Tar
}

function Start-KorusDockerImageCacheBackground {
    param([switch]$Force)

    $paths = Get-KorusDockerCachePaths
    if (-not (Test-KorusDockerOnHost)) { return $null }

    if ((Test-KorusDockerCacheFresh -Paths $paths) -and -not $Force) {
        $mb = [math]::Round((Get-Item $paths.Tar).Length / 1MB, 1)
        Write-Host "Docker cache: reusing docker-base-images.tar ($mb MiB)" -ForegroundColor DarkGray
        return $null
    }

    if (Test-Path $paths.PidFile) {
        $bpid = [int](Get-Content $paths.PidFile -Raw).Trim()
        if (Get-Process -Id $bpid -ErrorAction SilentlyContinue) {
            Write-Host "Docker cache: background build already running (PID $bpid)" -ForegroundColor DarkGray
            return $bpid
        }
    }

    $libScript = Join-Path $PSScriptRoot "Korus-DockerImageCache.ps1"
    $worker = Join-Path $KorusQemuRunDir "docker-cache-worker.ps1"
    $forceLine = if ($Force) { "Build-KorusDockerImageCache -Quiet -Force | Out-Null" } else { "Build-KorusDockerImageCache -Quiet | Out-Null" }
    @"
`$ErrorActionPreference = 'Continue'
. '$libScript'
try {
    $forceLine
} finally {
    Remove-Item '$($paths.PidFile)' -Force -ErrorAction SilentlyContinue
}
"@ | Set-Content -Path $worker -Encoding utf8

    $proc = Start-Process -FilePath "powershell.exe" -WindowStyle Hidden -PassThru -ArgumentList @(
        "-NoProfile", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden", "-File", $worker
    )
    $proc.Id | Set-Content -Path $paths.PidFile -Encoding ascii -NoNewline
    Write-Host "Docker cache: background pull/save started (PID $($proc.Id))" -ForegroundColor Cyan
    return $proc.Id
}
