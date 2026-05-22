function Reset-KorusVmDisks {
    . (Join-Path $PSScriptRoot "..\config.ps1")
    foreach ($role in @("server", "web")) {
        $overlay = Join-Path $KorusQemuImagesDir "$role.qcow2"
        if (Test-Path $overlay) {
            Remove-Item -Force $overlay
            Write-Host "Removed overlay: $overlay" -ForegroundColor DarkGray
        }
    }
}
