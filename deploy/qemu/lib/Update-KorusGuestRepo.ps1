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
        & $Plink -batch -hostkey $HostKey -pw korus -P $Port -m $tmp "korus@127.0.0.1"
        if ($LASTEXITCODE -ne 0) {
            throw "plink failed (exit $LASTEXITCODE)"
        }
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

function Get-KorusEd25519HostKey {
    param(
        [string]$SerialPath,
        [string]$Role = ""
    )
    if (Test-Path $SerialPath) {
        $m = Select-String -Path $SerialPath -Pattern "256 SHA256:([A-Za-z0-9+/=]+)\s+root@.*\(ED25519\)" |
            Select-Object -Last 1
        if ($m) { return "ssh-ed25519 255 SHA256:$($m.Matches[0].Groups[1].Value)" }
    }
    $runDir = if ($SerialPath) { Split-Path -Parent $SerialPath } else { $null }
    $cache = if ($runDir) { Join-Path $runDir "ssh-hostkeys.ps1" } else { $null }
    if ($cache -and (Test-Path $cache) -and $Role) {
        . $cache
        if ($script:KorusQemuSshHostKeys -and $script:KorusQemuSshHostKeys[$Role]) {
            return $script:KorusQemuSshHostKeys[$Role]
        }
    }
    return $null
}
