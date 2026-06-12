function Test-KorusGuestWebHotswapActive {
    param(
        [string]$HostKey,
        [int]$SshPort = 12222,
        [string]$Plink = "${env:ProgramFiles}\PuTTY\plink.exe"
    )
    if (-not $HostKey -or -not (Test-Path $Plink)) { return $false }
    . (Join-Path $PSScriptRoot "Update-KorusGuestRepo.ps1")
    try {
        $out = Invoke-PlinkShell -Plink $Plink -HostKey $HostKey -Port $SshPort -Script @'
cd /mnt/korus/korus-web 2>/dev/null || exit 1
test -f docker-compose.hotswap-qemu.yml || exit 1
ids=$(docker compose --env-file .env -f docker-compose.hotswap-qemu.yml ps -q 2>/dev/null)
test -n "$ids" && echo hotswap-active || echo hotswap-off
'@
        return ($out -match 'hotswap-active')
    } catch {
        return $false
    }
}

function Test-KorusHostTailwindCss {
    try {
        $code = curl.exe -sS -m 8 -o NUL -w "%{http_code}" "http://127.0.0.1:19088/tailwind.css" 2>$null
        return ($code -match '^2')
    } catch { return $false }
}
