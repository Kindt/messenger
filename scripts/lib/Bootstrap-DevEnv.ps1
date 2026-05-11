# Shared bootstrap for local dev scripts (ASCII / Windows PowerShell 5.1).
# - Prepends Docker CLI and JDK bin to PATH when tools are installed but not on PATH.
# - Sets KORUS_DOCKER_EXE, JAVA_HOME (and KORUS_JAVA_HOME mirror) when found.

function Initialize-KorusDevToolPaths {
    $dockerCandidates = @(
        "$env:ProgramFiles\Docker\Docker\resources\bin\docker.exe",
        "${env:ProgramFiles(x86)}\Docker\Docker\resources\bin\docker.exe",
        "$env:LocalAppData\Programs\Docker\Docker\resources\bin\docker.exe",
        "$env:ProgramFiles\Docker\Docker\docker.exe"
    )
    foreach ($p in $dockerCandidates) {
        if ($p -and (Test-Path -LiteralPath $p)) {
            $env:KORUS_DOCKER_EXE = $p
            $binDir = Split-Path -Parent $p
            if ($env:Path -notlike "*${binDir}*") {
                $env:Path = "${binDir};${env:Path}"
            }
            break
        }
    }

    if (-not $env:JAVA_HOME) {
        $javaRoot = Join-Path $env:ProgramFiles "Java"
        if (Test-Path -LiteralPath $javaRoot) {
            $jdkDirs = Get-ChildItem -LiteralPath $javaRoot -Directory -ErrorAction SilentlyContinue |
                Where-Object { $_.Name -match '^jdk-' }
            $best = $null
            $bestMajor = -1
            foreach ($d in $jdkDirs) {
                if ($d.Name -match '^jdk-(\d+)') {
                    $m = [int]$Matches[1]
                    if ($m -ge 17 -and $m -gt $bestMajor) {
                        $bestMajor = $m
                        $best = $d
                    }
                }
            }
            if ($best) {
                $env:JAVA_HOME = $best.FullName
                $env:KORUS_JAVA_HOME = $best.FullName
                $jb = Join-Path $best.FullName "bin"
                if ($env:Path -notlike "*${jb}*") {
                    $env:Path = "${jb};${env:Path}"
                }
            }
        }
    }
}
