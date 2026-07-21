function Get-SonarCloudImage {
    . (Join-Path $PSScriptRoot "..\config.ps1")
    New-Item -ItemType Directory -Force -Path $SonarQemuImagesDir | Out-Null
    if (Test-Path $SonarQemuCloudImage) {
        return $SonarQemuCloudImage
    }
    Write-Host "Downloading Ubuntu 24.04 cloud image (one-time, ~400 MiB)..." -ForegroundColor Cyan
    $partial = "$SonarQemuCloudImage.partial"
    if (Test-Path $partial) { Remove-Item $partial -Force }
    $curl = Get-Command curl.exe -ErrorAction SilentlyContinue
    if ($curl) {
        & curl.exe -fL --retry 5 --retry-delay 5 -o $partial $SonarQemuCloudImageUrl
        if ($LASTEXITCODE -ne 0) { throw "curl download failed ($LASTEXITCODE)" }
    } else {
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        Invoke-WebRequest -Uri $SonarQemuCloudImageUrl -OutFile $partial -UseBasicParsing
    }
    Move-Item -Force $partial $SonarQemuCloudImage
    Write-Host "Saved: $SonarQemuCloudImage" -ForegroundColor Green
    return $SonarQemuCloudImage
}

function New-SonarVmDisk {
    param([string]$BaseImage)
    . (Join-Path $PSScriptRoot "..\config.ps1")
    . (Join-Path $PSScriptRoot "Resolve-Qemu.ps1")
    $qemuImg = Resolve-SonarQemuImg
    if (-not $qemuImg) { throw "qemu-img not found" }
    if (-not (Test-Path $SonarQemuDisk)) {
        Write-Host "Creating sonar-vm overlay disk ($SonarQemuVmDiskGb GiB)..." -ForegroundColor Cyan
        & $qemuImg create -f qcow2 -F qcow2 -b $BaseImage $SonarQemuDisk | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "qemu-img create failed" }
        & $qemuImg resize -f qcow2 $SonarQemuDisk "${SonarQemuVmDiskGb}G" | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "qemu-img resize failed" }
    }
    return $SonarQemuDisk
}
