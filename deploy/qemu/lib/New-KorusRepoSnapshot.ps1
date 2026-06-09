function New-KorusRepoSnapshot {
    . (Join-Path $PSScriptRoot "..\config.ps1")
    $archive = Join-Path $KorusQemuRunDir "repo.tgz"
    $staging = Join-Path $KorusQemuRunDir "repo.staging.tgz"
    $pidFile = Join-Path $KorusQemuRunDir "repo-http.pid"
    if (Test-Path $pidFile) {
        $old = (Get-Content $pidFile -Raw).Trim()
        if ($old -match '^\d+$') {
            Stop-Process -Id ([int]$old) -Force -ErrorAction SilentlyContinue
        }
        Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
        Start-Sleep -Milliseconds 300
    }
    New-Item -ItemType Directory -Force -Path $KorusQemuRunDir | Out-Null
    if (Test-Path $staging) {
        Remove-Item -Force $staging
    }
    Write-Host "Packing repo snapshot (exclude .git, images, build caches)..." -ForegroundColor Cyan
    $tar = Get-Command tar -ErrorAction Stop
    $excludes = @(
        ".git",
        "deploy/qemu/images",
        "deploy/qemu/run",
        "node_modules",
        ".gradle",
        "build",
        "dist",
        "out",
        "target"
    )
    $args = @("-czf", $staging)
    foreach ($e in $excludes) {
        $args += "--exclude=$e"
    }
    $args += "-C", $KorusQemuRepoRoot, "."
    & $tar.Source @args
    if ($LASTEXITCODE -ne 0) { throw "tar failed creating $staging" }
    Move-Item -Force $staging $archive
    $mb = [math]::Round((Get-Item $archive).Length / 1MB, 1)
    Write-Host "  $archive ($mb MiB)" -ForegroundColor DarkGray
    return $archive
}
