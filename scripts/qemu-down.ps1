$Root = Split-Path -Parent $PSScriptRoot
& (Join-Path $Root "deploy\qemu\qemu-down.ps1")
