function New-KorusWebuiSnapshot {
    param([switch]$Force)
    . (Join-Path $PSScriptRoot "..\config.ps1")
    $webuiRoot = Join-Path $KorusQemuRepoRoot "modules\web-client\src\main\resources\webui"
    if (-not (Test-Path (Join-Path $webuiRoot "index.html"))) {
        throw "webui not found: $webuiRoot"
    }
    $buildDir = Join-Path $KorusQemuRepoRoot "modules\web-client\webui-build"
    $pkg = Join-Path $buildDir "package.json"
    if (Test-Path $pkg) {
        $npm = if ($env:OS -match 'Windows') { "npm.cmd" } else { "npm" }
        $nodeModules = Join-Path $buildDir "node_modules"
        if (-not (Test-Path $nodeModules)) {
            Write-Host "  webui-build: npm ci..." -ForegroundColor DarkGray
            Push-Location $buildDir
            $prevEap = $ErrorActionPreference
            try {
                $ErrorActionPreference = 'Continue'
                & $npm ci 2>&1 | Out-Null
                if ($LASTEXITCODE -ne 0) { throw "npm ci failed in webui-build" }
            } finally {
                $ErrorActionPreference = $prevEap
                Pop-Location
            }
        }
        Write-Host "  webui-build: locales + tailwind.css..." -ForegroundColor DarkGray
        Push-Location $buildDir
        $prevEap = $ErrorActionPreference
        try {
            $ErrorActionPreference = 'Continue'
            & $npm run build:assets 2>&1 | Out-Null
            if ($LASTEXITCODE -ne 0) { throw "npm run build:assets failed" }
        } finally {
            $ErrorActionPreference = $prevEap
            Pop-Location
        }
    }
    New-Item -ItemType Directory -Force -Path $KorusQemuRunDir | Out-Null
    $archive = Join-Path $KorusQemuRunDir "webui.tgz"
    $staging = Join-Path $KorusQemuRunDir "webui.staging.tgz"
    if (-not $Force -and (Test-Path $archive)) {
        $srcNewer = (Get-Item $webuiRoot).LastWriteTimeUtc
        $arcTime = (Get-Item $archive).LastWriteTimeUtc
        if ($arcTime -ge $srcNewer) {
            $kb = [math]::Round((Get-Item $archive).Length / 1KB, 0)
            Write-Host "  Reusing webui.tgz (${kb} KiB)" -ForegroundColor DarkGray
            return $archive
        }
    }
    if (Test-Path $staging) { Remove-Item -Force $staging }
    Write-Host "Packing webui snapshot..." -ForegroundColor Cyan
    $tar = Get-Command tar -ErrorAction Stop
    $parent = Split-Path -Parent $webuiRoot
    $leaf = Split-Path -Leaf $webuiRoot
    & $tar.Source -czf $staging -C $parent $leaf
    if ($LASTEXITCODE -ne 0) { throw "tar failed creating $staging" }
    Move-Item -Force $staging $archive
    $kb = [math]::Round((Get-Item $archive).Length / 1KB, 0)
    Write-Host "  $archive (${kb} KiB)" -ForegroundColor DarkGray
    return $archive
}
