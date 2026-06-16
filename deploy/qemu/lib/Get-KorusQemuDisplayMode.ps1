function Get-KorusQemuDisplayMode {
    # none | gtk | sdl | default — override via $env:KORUS_QEMU_DISPLAY or -Graphical on qemu-up
    $raw = if ($env:KORUS_QEMU_DISPLAY) { $env:KORUS_QEMU_DISPLAY.Trim().ToLower() } else { "none" }
    switch ($raw) {
        { $_ -in "1", "true", "graphical", "gui", "gtk" } { return "gtk" }
        "sdl" { return "sdl" }
        "default" { return "default" }
        default { return "none" }
    }
}

function Get-KorusQemuDisplayArgs {
    param(
        [Parameter(Mandatory)][ValidateSet("server", "web", "integrations")]
        [string]$Role,
        [ValidateSet("", "none", "gtk", "sdl", "default")]
        [string]$ModeOverride = ""
    )
    $mode = if ($ModeOverride) { $ModeOverride } else { Get-KorusQemuDisplayMode }
    $title = "korus-$Role"
    switch ($mode) {
        "none" {
            return @{
                Mode         = "none"
                Args         = @("-display", "none")
                WindowStyle  = "Hidden"
                Graphical    = $false
            }
        }
        "default" {
            return @{
                Mode         = "default"
                Args         = @("-vga", "std", "-display", "default")
                WindowStyle  = "Normal"
                Graphical    = $true
            }
        }
        default {
            return @{
                Mode         = $mode
                Args         = @("-vga", "std", "-display", $mode)
                WindowStyle  = "Normal"
                Graphical    = $true
            }
        }
    }
}
