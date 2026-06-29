# Enable TURN ICE for nginx-only korus-web (QEMU web guest).
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
. (Join-Path $Root 'deploy\qemu\lib\Update-KorusGuestRepo.ps1')

$plink = Join-Path $env:ProgramFiles 'PuTTY\plink.exe'
$runDir = Join-Path $Root 'deploy\qemu\run'
$hostKey = Get-KorusEd25519HostKey -SerialPath (Join-Path $runDir 'web-serial.log') -Role web -SshPort 12222
if (-not $hostKey) { throw 'web SSH host key not ready' }

. (Join-Path $Root 'deploy\qemu\lib\New-KorusRepoSnapshot.ps1')

Write-Host 'Repair: sync repo + korus-web --turn (nginx-only lb ICE)...' -ForegroundColor Cyan
New-KorusRepoSnapshot -Force | Out-Null
Update-KorusGuestRepo -Role web -SshPort 12222 -HostKey $hostKey -Plink $plink | Out-Null

$ice = '[{"urls":"stun:stun.l.google.com:19302"},{"urls":["turn:127.0.0.1:3478?transport=udp","turn:127.0.0.1:3478?transport=tcp"],"username":"korus","credential":"korus-turn-demo-secret"}]'

$guestScript = @"
set -e
cd /mnt/korus/korus-web
if [ ! -f .env ]; then cp .env.example .env 2>/dev/null || touch .env; fi
python3 - <<'PY'
from pathlib import Path
ice = '$ice'
p = Path('.env')
lines = p.read_text(encoding='utf-8').splitlines() if p.exists() else []
out, found = [], False
for line in lines:
    if line.startswith('WEB_CLIENT_RTC_ICE_SERVERS='):
        out.append('WEB_CLIENT_RTC_ICE_SERVERS=' + ice)
        found = True
    else:
        out.append(line)
if not found:
    out.append('WEB_CLIENT_RTC_ICE_SERVERS=' + ice)
p.write_text('\n'.join(out) + '\n', encoding='utf-8')
PY
export SKIP_KORUS_ENSURE=1
bash ../scripts/korus-web-up.sh --turn --build --force-recreate 2>&1
set +e
curl -sf http://127.0.0.1:9088/web-client-env.js | grep -q turn:
if [ `$? -eq 0 ]; then echo web-turn-ice-ok; else echo web-turn-ice-missing; fi
"@

$out = Invoke-PlinkShell -Plink $plink -HostKey $hostKey -Port 12222 -Script $guestScript
Write-Host $out
$jsProbe = try { (Invoke-WebRequest 'http://127.0.0.1:19088/web-client-env.js' -UseBasicParsing).Content } catch { '' }
if ($jsProbe -match 'turn:') {
    Write-Host '[OK] web-client-env.js contains turn ICE' -ForegroundColor Green
} elseif ($out -match 'web-turn-ice-ok') {
    Write-Host '[OK] guest web-client-env.js contains turn ICE (host probe pending)' -ForegroundColor Green
} else {
    throw 'web TURN ICE repair failed'
}
