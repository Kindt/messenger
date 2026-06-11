# Build/reuse docker-base-images.tar on host for QEMU guests (requires Docker Desktop).
param(
    [switch]$Force,
    [switch]$Background,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot

if ($Help) {
    Write-Host @"
Usage: .\scripts\preload-qemu-docker-images.ps1 [-Force] [-Background]

Pulls base images on the host and writes deploy/qemu/run/docker-base-images.tar.
Guests load via: http://10.0.2.2:18890/docker-base-images.tar

Skip: set KORUS_QEMU_SKIP_DOCKER_CACHE=1 or omit Docker on host.
"@
    exit 0
}

. (Join-Path $Root "deploy\qemu\lib\Korus-DockerImageCache.ps1")

if ($Background) {
    Start-KorusDockerImageCacheBackground -Force:$Force | Out-Null
} else {
    Build-KorusDockerImageCache -Force:$Force
}
