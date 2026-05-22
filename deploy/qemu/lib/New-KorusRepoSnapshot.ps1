function New-KorusRepoSnapshot {
    . (Join-Path $PSScriptRoot "..\config.ps1")
    $archive = Join-Path $KorusQemuRunDir "repo.tgz"
    New-Item -ItemType Directory -Force -Path $KorusQemuRunDir | Out-Null
    if (Test-Path $archive) {
        Remove-Item -Force $archive
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
    $args = @("-czf", $archive)
    foreach ($e in $excludes) {
        $args += "--exclude=$e"
    }
    $args += "-C", $KorusQemuRepoRoot, "."
    & $tar.Source @args
    if ($LASTEXITCODE -ne 0) { throw "tar failed creating $archive" }
    $mb = [math]::Round((Get-Item $archive).Length / 1MB, 1)
    Write-Host "  $archive ($mb MiB)" -ForegroundColor DarkGray
    return $archive
}
