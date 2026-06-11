function Get-KorusLanHostIp {
    $candidates = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
        Where-Object {
            $_.IPAddress -notmatch '^127\.' -and
            $_.PrefixOrigin -ne 'WellKnown' -and
            $_.InterfaceAlias -notmatch 'vEthernet|WSL|VirtualBox|VMware|QEMU|Loopback|Hyper-V'
        } |
        Sort-Object -Property @{ Expression = { $_.SkipAsSource }; Descending = $false }, PrefixLength -Descending

    $ip = ($candidates | Select-Object -First 1).IPAddress
    if ($ip) { return $ip }
    return "127.0.0.1"
}

function Write-KorusQemuLanHostInfo {
    param([string]$RunDir)
    . (Join-Path $PSScriptRoot "Get-KorusLanHostIp.ps1")
    $ip = Get-KorusLanHostIp
    New-Item -ItemType Directory -Force -Path $RunDir | Out-Null
    $ip | Set-Content -Path (Join-Path $RunDir "host-lan-ip.txt") -Encoding ascii -NoNewline
    return $ip
}

function Read-KorusQemuLanHostIp {
    param([string]$RunDir)
    $file = Join-Path $RunDir "host-lan-ip.txt"
    if (Test-Path $file) {
        $ip = (Get-Content $file -Raw).Trim()
        if ($ip) { return $ip }
    }
    . (Join-Path $PSScriptRoot "Get-KorusLanHostIp.ps1")
    return Get-KorusLanHostIp
}

function Test-KorusWebClientWsHostMismatch {
    param(
        [Parameter(Mandatory)][string]$RunDir,
        [string]$WebBaseUrl = "http://127.0.0.1:19088"
    )
    $expected = Read-KorusQemuLanHostIp -RunDir $RunDir
    if (-not $expected) { return $false }
    try {
        $r = Invoke-WebRequest -Uri "$WebBaseUrl/web-client-env.js" -UseBasicParsing -TimeoutSec 10
        if ($r.StatusCode -ne 200) { return $false }
        return ($r.Content -notmatch [regex]::Escape($expected))
    } catch {
        return $false
    }
}
