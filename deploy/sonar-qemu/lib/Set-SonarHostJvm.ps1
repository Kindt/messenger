# Isolate SonarScanner JVM from corporate CryptoPro CSP hooks on Windows.

function Get-SonarNoCryptoProSecurityFile {
    . (Join-Path $PSScriptRoot "..\config.ps1")
    $file = Join-Path $SonarQemuToolsDir "java-security-no-cryptopro.properties"
    if (-not (Test-Path -LiteralPath $file)) {
        throw "Missing $file"
    }
    return (Resolve-Path $file).Path
}

function Get-SonarNoCryptoProJvmOpts {
    $sec = Get-SonarNoCryptoProSecurityFile
    # Forward slashes avoid escaping issues in SONAR_SCANNER_OPTS on Windows.
    $path = $sec -replace '\\', '/'
    return "-Djava.security.properties==$path"
}

function Use-SonarNoCryptoProJvm {
  param([switch]$AllowCryptoPro)

    $script:SonarNoCryptoProPrev = @{
        JAVA_TOOL_OPTIONS  = $env:JAVA_TOOL_OPTIONS
        _JAVA_OPTIONS      = $env:_JAVA_OPTIONS
        SONAR_SCANNER_OPTS = $env:SONAR_SCANNER_OPTS
    }
    if ($AllowCryptoPro) {
        return
    }
    $extra = Get-SonarNoCryptoProJvmOpts
    $env:JAVA_TOOL_OPTIONS = $extra
    $env:_JAVA_OPTIONS = $null
    if ($env:SONAR_SCANNER_OPTS) {
        $env:SONAR_SCANNER_OPTS = "$extra $($env:SONAR_SCANNER_OPTS)"
    } else {
        $env:SONAR_SCANNER_OPTS = $extra
    }
}

function Restore-SonarNoCryptoProJvm {
    if (-not $script:SonarNoCryptoProPrev) { return }
    foreach ($key in $script:SonarNoCryptoProPrev.Keys) {
        $val = $script:SonarNoCryptoProPrev[$key]
        if ($null -eq $val) {
            Remove-Item -Path "Env:$key" -ErrorAction SilentlyContinue
        } else {
            Set-Item -Path "Env:$key" -Value $val
        }
    }
    $script:SonarNoCryptoProPrev = $null
}
