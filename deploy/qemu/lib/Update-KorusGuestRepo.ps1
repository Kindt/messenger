function Invoke-PlinkShell {
    param(
        [string]$Plink,
        [string]$HostKey,
        [int]$Port,
        [string]$Script
    )
    $script = (($Script -split "`r?`n") -join "`n").Trim() + "`n"
    $tmp = [System.IO.Path]::Combine([System.IO.Path]::GetTempPath(), "korus-plink-$(New-Guid).sh")
    try {
        $utf8 = [System.Text.UTF8Encoding]::new($false)
        [System.IO.File]::WriteAllBytes($tmp, $utf8.GetBytes($script))
        # plink writes host-key hints and SSH banners to stderr even on success; merge streams and
        # judge by exit code + expected stdout marker instead of treating stderr as failure.
        $prevEap = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        try {
            $merged = & $Plink -batch -hostkey $HostKey -pw korus -P $Port -m $tmp "korus@127.0.0.1" 2>&1
        } finally {
            $ErrorActionPreference = $prevEap
        }
        $text = if ($merged -is [array]) { ($merged | ForEach-Object { "$_" }) -join "`n" } else { "$merged" }
        $exit = $LASTEXITCODE
        if ($exit -ne 0) {
            $benign = $text -match '(?i)(host key|fingerprint|store key in cache|connection reset|connection timed out)'
            if ($benign -and $text -match 'repo-updated') {
                return $text
            }
            throw "plink failed (exit $exit): $($text.Trim())"
        }
        return $text
    } finally {
        Remove-Item -LiteralPath $tmp -Force -ErrorAction SilentlyContinue
    }
}

function Update-KorusGuestRepo {
    param(
        [Parameter(Mandatory)]
        [ValidateSet("server", "web")]
        [string]$Role,
        [int]$SshPort,
        [string]$HostKey,
        [string]$Plink = "${env:ProgramFiles}\PuTTY\plink.exe"
    )
    if (-not (Test-Path $Plink)) {
        throw "PuTTY plink not found at $Plink"
    }
    $cmd = @'
sudo mkdir -p /mnt/korus && curl -fsSL http://10.0.2.2:18890/repo.tgz 2>/dev/null | sudo tar -xzf - -C /mnt/korus
sudo find /mnt/korus -name '*.sh' -exec sed -i 's/\r$//' {} \;
echo repo-updated
'@
    Invoke-PlinkShell -Plink $Plink -HostKey $HostKey -Port $SshPort -Script $cmd
}

function Save-KorusSshHostKey {
    param(
        [string]$RunDir,
        [string]$Role,
        [string]$HostKey
    )
    if (-not $RunDir -or -not $Role -or -not $HostKey) { return }
    $cache = Join-Path $RunDir "ssh-hostkeys.ps1"
    $keys = @{}
    if (Test-Path $cache) {
        . $cache
        if ($script:KorusQemuSshHostKeys) { $keys = @{} + $script:KorusQemuSshHostKeys }
    }
    $keys[$Role] = $HostKey
    $lines = @(
        '# Auto-generated QEMU SSH host keys (PuTTY -hostkey format)',
        '$script:KorusQemuSshHostKeys = @{'
    )
    foreach ($k in ($keys.Keys | Sort-Object)) {
        $lines += "    '$k' = '$($keys[$k])'"
    }
    $lines += '}'
    Set-Content -Path $cache -Value ($lines -join "`n") -Encoding utf8
}

function Get-KorusPlinkHostKeyProbe {
    param(
        [int]$Port,
        [string]$Plink = "${env:ProgramFiles}\PuTTY\plink.exe"
    )
    if (-not (Test-Path $Plink)) { return $null }
    $cmd = "`"$Plink`" -batch -pw korus -P $Port korus@127.0.0.1 exit"
    $err = cmd /c "$cmd 2>&1"
    if ($err -is [array]) { $err = $err -join "`n" }
    $m = [regex]::Match([string]$err, 'ssh-ed25519 255 SHA256:([A-Za-z0-9+/=]+)')
    if ($m.Success) { return "ssh-ed25519 255 SHA256:$($m.Groups[1].Value)" }
    return $null
}

function Get-KorusEd25519HostKey {
    param(
        [string]$SerialPath,
        [string]$Role = "",
        [int]$SshPort = 0
    )
    $runDir = if ($SerialPath) { Split-Path -Parent $SerialPath } else { $null }
    $cache = if ($runDir) { Join-Path $runDir "ssh-hostkeys.ps1" } else { $null }
    if ($cache -and (Test-Path $cache) -and $Role) {
        . $cache
        if ($script:KorusQemuSshHostKeys -and $script:KorusQemuSshHostKeys[$Role]) {
            return $script:KorusQemuSshHostKeys[$Role]
        }
    }
    if (Test-Path $SerialPath) {
        $m = Select-String -Path $SerialPath -Pattern "256 SHA256:([A-Za-z0-9+/=]+)\s+root@.*\(ED25519\)" |
            Select-Object -Last 1
        if (-not $m) {
            $m = Select-String -Path $SerialPath -Pattern "SHA256:([A-Za-z0-9+/=]+)\s+root@" |
                Select-Object -Last 1
        }
        if ($m) {
            $hk = "ssh-ed25519 255 SHA256:$($m.Matches[0].Groups[1].Value)"
            if ($Role) { Save-KorusSshHostKey -RunDir $runDir -Role $Role -HostKey $hk }
            return $hk
        }
    }
    if ($SshPort -gt 0 -and $Role) {
        $probed = Get-KorusPlinkHostKeyProbe -Port $SshPort
        if ($probed) {
            Save-KorusSshHostKey -RunDir $runDir -Role $Role -HostKey $probed
            return $probed
        }
    }
    return $null
}
