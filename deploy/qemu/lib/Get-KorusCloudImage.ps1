function Get-KorusCloudImage {
    . (Join-Path $PSScriptRoot "..\config.ps1")
    New-Item -ItemType Directory -Force -Path $KorusQemuImagesDir | Out-Null
    if (Test-Path $KorusQemuCloudImage) {
        return $KorusQemuCloudImage
    }
    Write-Host "Downloading Ubuntu 24.04 server cloud image (one-time, ~400 MiB)..." -ForegroundColor Cyan
    Write-Host "  URL: $KorusQemuCloudImageUrl" -ForegroundColor DarkGray
    $partial = "$KorusQemuCloudImage.partial"
    if (Test-Path $partial) { Remove-Item $partial -Force }
    $curl = Get-Command curl.exe -ErrorAction SilentlyContinue
    if ($curl) {
        & curl.exe -fL --retry 5 --retry-delay 5 -o $partial $KorusQemuCloudImageUrl
        if ($LASTEXITCODE -ne 0) { throw "curl download failed ($LASTEXITCODE)" }
    } else {
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        Invoke-WebRequest -Uri $KorusQemuCloudImageUrl -OutFile $partial -UseBasicParsing
    }
    Move-Item -Force $partial $KorusQemuCloudImage
    Write-Host "Saved: $KorusQemuCloudImage" -ForegroundColor Green
    return $KorusQemuCloudImage
}

function New-KorusVmOverlayDisk {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$BaseImage
    )
    . (Join-Path $PSScriptRoot "..\config.ps1")
    . (Join-Path $PSScriptRoot "Resolve-Qemu.ps1")
    $qemuImg = Resolve-KorusQemuImg
    if (-not $qemuImg) { throw "qemu-img not found" }
    $out = Join-Path $KorusQemuImagesDir "$Name.qcow2"
    if (-not (Test-Path $out)) {
        $role = if ($Name -match '^(server|web)') { $Matches[1] } else { $Name }
        $diskGb = if ($role -eq "server") { $KorusQemuVmDiskGb } else { $KorusQemuWebDiskGb }
        Write-Host "Creating $Name overlay disk ($diskGb GiB)..." -ForegroundColor Cyan
        & $qemuImg create -f qcow2 -F qcow2 -b $BaseImage $out | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "qemu-img create failed for $Name" }
        & $qemuImg resize -f qcow2 $out "${diskGb}G" | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "qemu-img resize failed for $Name" }
    }
    return $out
}
