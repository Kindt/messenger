# Project-agnostic helpers: read sonar-project.properties, build guest payload paths.

function Resolve-SonarLabRepoRoot {
    param([string]$RepoRoot)
    if ($RepoRoot) {
        return (Resolve-Path $RepoRoot).Path
    }
    return (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
}

function Get-SonarPropertiesMap {
    param([Parameter(Mandatory)][string]$RepoRoot)
    $file = Join-Path $RepoRoot "sonar-project.properties"
    if (-not (Test-Path $file)) {
        throw "sonar-project.properties not found in $RepoRoot"
    }
    $map = @{}
    Get-Content $file -Encoding UTF8 | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#")) { return }
        $i = $line.IndexOf("=")
        if ($i -lt 1) { return }
        $k = $line.Substring(0, $i).Trim()
        $v = $line.Substring($i + 1).Trim()
        $map[$k] = $v
    }
    return $map
}

function Get-SonarProjectIdentity {
    param([Parameter(Mandatory)][hashtable]$Props)
    $key = $Props["sonar.projectKey"]
    if (-not $key) { throw "sonar.projectKey missing in sonar-project.properties" }
    $name = $Props["sonar.projectName"]
    if (-not $name) { $name = ($key -split '[:.]' | Select-Object -Last 1) }
    return [pscustomobject]@{ Key = $key; Name = $name }
}

function Get-SonarPayloadRelativePaths {
    param(
        [Parameter(Mandatory)][string]$RepoRoot,
        [Parameter(Mandatory)][hashtable]$Props
    )
    $paths = New-Object System.Collections.Generic.List[string]
    $paths.Add("sonar-project.properties") | Out-Null

    $modulesCsv = $Props["sonar.modules"]
    if ($modulesCsv) {
        foreach ($mod in ($modulesCsv -split "," | ForEach-Object { $_.Trim() } | Where-Object { $_ })) {
            $base = $Props["$mod.sonar.projectBaseDir"]
            if (-not $base) { $base = $mod }
            foreach ($key in @("sources", "tests", "java.binaries", "java.test.binaries")) {
                $rel = $Props["$mod.sonar.$key"]
                if (-not $rel) { continue }
                $fullRel = Join-Path $base ($rel -replace '/', '\')
                $paths.Add($fullRel) | Out-Null
            }
        }
    } else {
        $base = "."
        foreach ($key in @("sonar.sources", "sonar.tests", "sonar.java.binaries", "sonar.java.test.binaries")) {
            $rel = $Props[$key]
            if (-not $rel) { continue }
            foreach ($part in ($rel -split "," | ForEach-Object { $_.Trim() } | Where-Object { $_ })) {
                $paths.Add(($part -replace '/', '\')) | Out-Null
            }
        }
    }

    # de-dupe, keep order
    $seen = @{}
    $out = @()
    foreach ($p in $paths) {
        $norm = $p -replace '/', '\'
        if ($seen.ContainsKey($norm)) { continue }
        $seen[$norm] = $true
        $out += $norm
    }
    return $out
}

function Invoke-SonarLabCompile {
    param(
        [Parameter(Mandatory)][string]$RepoRoot,
        [string]$JdkHome = $env:JAVA_HOME,
        [string]$CompileCommand
    )
    Push-Location $RepoRoot
    try {
        if ($CompileCommand) {
            Write-Host "Compile: $CompileCommand" -ForegroundColor Cyan
            cmd /c $CompileCommand
            if ($LASTEXITCODE -ne 0) { throw "CompileCommand failed ($LASTEXITCODE)" }
            return
        }
        $jdk25 = Join-Path $RepoRoot "scripts\mvn-jdk25.ps1"
        if (Test-Path $jdk25) {
            if (-not $JdkHome) { $JdkHome = "C:\Program Files\Java\jdk-25.0.2" }
            Write-Host "Compile: scripts/mvn-jdk25.ps1 test-compile" -ForegroundColor Cyan
            & $jdk25 -JdkHome $JdkHome test-compile "-DskipTests"
            if ($LASTEXITCODE -ne 0) { throw "Maven test-compile failed ($LASTEXITCODE)" }
            return
        }
        if (Get-Command mvn -ErrorAction SilentlyContinue) {
            Write-Host "Compile: mvn test-compile" -ForegroundColor Cyan
            & mvn test-compile "-DskipTests"
            if ($LASTEXITCODE -ne 0) { throw "Maven test-compile failed ($LASTEXITCODE)" }
            return
        }
        throw "No compile strategy: pass -CompileCommand, or add scripts/mvn-jdk25.ps1, or install mvn"
    } finally {
        Pop-Location
    }
}
