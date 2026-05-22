function New-KorusCloudInitFat {
    param(
        [Parameter(Mandatory)][string]$Role
    )
    . (Join-Path $PSScriptRoot "..\config.ps1")
    $src = Join-Path $KorusQemuCloudDir $Role
    $dst = Join-Path $KorusQemuRunDir "cidata-$Role"
    if (Test-Path $dst) {
        Remove-Item -Recurse -Force $dst
    }
    New-Item -ItemType Directory -Force -Path $dst | Out-Null
    Copy-Item -Path (Join-Path $src "*") -Destination $dst -Force
    return $dst
}
