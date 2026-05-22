function Resolve-KorusQemu {
    . (Join-Path $PSScriptRoot "..\config.ps1")
    $candidates = @(
        (Join-Path $KorusQemuToolsDir "qemu\qemu-system-x86_64.exe"),
        "${env:ProgramFiles}\qemu\qemu-system-x86_64.exe",
        "${env:ProgramFiles(x86)}\qemu\qemu-system-x86_64.exe"
    )
    foreach ($p in $candidates) {
        if (Test-Path $p) { return (Resolve-Path $p).Path }
    }
    $cmd = Get-Command qemu-system-x86_64.exe -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    return $null
}

function Resolve-KorusQemuImg {
    $qemu = Resolve-KorusQemu
    if ($qemu) {
        $img = Join-Path (Split-Path $qemu) "qemu-img.exe"
        if (Test-Path $img) { return $img }
    }
    $cmd = Get-Command qemu-img.exe -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    return $null
}
