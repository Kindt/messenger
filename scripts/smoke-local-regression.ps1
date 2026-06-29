#Requires -Version 5.1
# Canonical local QEMU regression smoke runner. No CI integration by design.
param(
    [string]$ApiBaseUrl = "http://127.0.0.1:18080",
    [string]$WebBaseUrl = "http://127.0.0.1:19088",
    [int]$ServerSshPort = 12221,
    [switch]$NoPrepareProfile,
    [switch]$SkipContainerPortability,
    [switch]$SkipW1b,
    [switch]$WriteEvidence,
    [int]$ContainerPollSeconds = 20,
    [int]$ContainerPollCount = 20,
    [switch]$Help
)

$ErrorActionPreference = "Stop"

if ($Help) {
    Write-Host @"
Usage:
  .\scripts\smoke-local-regression.ps1
  .\scripts\smoke-local-regression.ps1 -SkipContainerPortability
  .\scripts\smoke-local-regression.ps1 -NoPrepareProfile
  .\scripts\smoke-local-regression.ps1 -SkipW1b -WriteEvidence

Runs W1b: voice message, federation trust/cross-org, bot-api (spec 029).

Runs the canonical local QEMU smoke regression against:
  API: $ApiBaseUrl
  Web: $WebBaseUrl

Default behavior:
  - prepares core-api with fleet lab targets and regression add-ons;
  - normalizes guest shell scripts before guest bash smokes;
  - runs container portability as a guest background job with fast polling.

No CI integration. This is a local/QEMU operator gate.
"@
    exit 0
}

$Root = Split-Path -Parent $PSScriptRoot
$RunDir = Join-Path $Root "deploy\qemu\run"
$Plink = Join-Path $env:ProgramFiles "PuTTY\plink.exe"

. (Join-Path $Root "deploy\qemu\lib\Update-KorusGuestRepo.ps1")

function Fail([string]$Message) {
    Write-Host "[FAIL] $Message" -ForegroundColor Red
    exit 1
}

function Invoke-RegressionStep {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][scriptblock]$Body
    )

    Write-Host ""
    Write-Host "=== $Name ===" -ForegroundColor Cyan
    $global:LASTEXITCODE = 0
    & $Body
    if ($LASTEXITCODE -ne 0) {
        Fail "$Name failed exit $LASTEXITCODE"
    }
    Write-Host "[OK] $Name" -ForegroundColor Green
}

function Get-ServerHostKey {
    $key = Get-KorusEd25519HostKey -SerialPath (Join-Path $RunDir "server-serial.log") -Role server -SshPort $ServerSshPort
    if (-not $key) { Fail "server SSH host key not ready" }
    return $key
}

function Invoke-ServerGuestScript {
    param([Parameter(Mandatory = $true)][string]$Script)
    $hostKey = Get-ServerHostKey
    Invoke-PlinkShell -Plink $Plink -HostKey $hostKey -Port $ServerSshPort -Script $Script
}

function Prepare-RegressionProfile {
    $addons = "addon-productivity,addon-engage,addon-search,addon-collaboration,addon-ai,addon-live,addon-retention,addon-archive,addon-deep-archive,addon-export,addon-enterprise-auth,addon-e2ee,addon-bots,addon-integrations,addon-federation,addon-dlp,addon-migration-import"
    $script = @"
set -e
cd /mnt/korus
python3 - <<'PY'
from pathlib import Path
addons = "$addons"
fleet = Path("docker/fleet-targets.qemu.json").read_text(encoding="utf-8").strip()
Path("/tmp/korus-qemu-regress.env").write_text(
    f"FLEET_TARGETS_JSON={fleet}\n"
    "FLEET_AGGREGATOR_NODE=core-api@qemu-server\n"
    f"KORUS_PRODUCT_ADDONS={addons}\n"
    "SCIM_BEARER_TOKEN=korus-scim-lab-demo\n",
    encoding="utf-8",
)
PY
sudo docker compose --env-file /tmp/korus-qemu-regress.env -f docker/docker-compose.full-server.yml -f docker/docker-compose.fleet-lab.yml -f docker/docker-compose.qemu-regression-lab.yml up -d core-api
for i in `$(seq 1 40); do
  code=`$(curl -sS -m 5 -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/api/v1/health || true)
  echo health=`$code
  [ "`$code" = 200 ] && exit 0
  sleep 5
done
exit 1
"@
    Invoke-ServerGuestScript -Script $script
}

function Invoke-NodeWebSocketSmoke {
    $nodeScript = @"
const http = (u, o, b) => fetch(u, {
  ...o,
  body: b && JSON.stringify(b),
  headers: {'content-type': 'application/json; charset=utf-8', ...((o && o.headers) || {})}
});
(async () => {
  const login = await http('$ApiBaseUrl/api/v1/auth/login', {method: 'POST'}, {username: 'csadmin', password: 'csadmin'});
  if (!login.ok) throw new Error('login ' + login.status);
  const json = await login.json();
  const token = json.access_token || json.accessToken;
  const wsUrl = '$($ApiBaseUrl -replace '^http', 'ws' -replace ':18080$', ':18082')/ws?token=' + encodeURIComponent(token);
  const ws = new WebSocket(wsUrl);
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('ws open timeout')), 10000);
    ws.onopen = () => { clearTimeout(timer); resolve(); };
    ws.onerror = () => { clearTimeout(timer); reject(new Error('ws error')); };
  });
  ws.send(JSON.stringify({type: 'ping'}));
  await new Promise(resolve => setTimeout(resolve, 300));
  ws.close(1000, 'smoke');
  console.log('[OK] node websocket token connect/send/close');
})().catch(error => {
  console.error(error);
  process.exit(1);
});
"@
    node -e $nodeScript
}

function Invoke-GuestMessagingSmoke {
    $script = @'
set -e
cd /mnt/korus
find scripts -name '*.sh' -type f -exec perl -pi -e 's/\r$//' {} +
BASE_URL=http://127.0.0.1:8080 WS_URL=ws://127.0.0.1:8082/ws bash scripts/smoke-messaging-e2e.sh --skip-ensure-users
'@
    Invoke-ServerGuestScript -Script $script
}

function Invoke-ContainerPortabilitySmoke {
    $hostKey = Get-ServerHostKey
    & $Plink -batch -hostkey $hostKey -pw korus -P $ServerSshPort "korus@127.0.0.1" `
        "docker rm -f korus-ws-gateway-war-smoke korus-core-api-war-smoke 2>/dev/null || true; rm -f /tmp/korus-job-container-portability-smoke.exit"
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    & (Join-Path $PSScriptRoot "smoke-container-portability-guest.ps1")
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    for ($i = 1; $i -le $ContainerPollCount; $i++) {
        Write-Host "=== fast poll $i $(Get-Date -Format HH:mm:ss) ===" -ForegroundColor Cyan
        & (Join-Path $PSScriptRoot "qemu-guest-job.ps1") -JobName container-portability-smoke
        $code = $LASTEXITCODE
        if ($code -eq 0) { return }
        if ($code -eq 1) { exit 1 }
        Start-Sleep -Seconds $ContainerPollSeconds
    }
    Fail "container-portability-smoke timed out after $($ContainerPollSeconds * $ContainerPollCount)s"
}

if (-not (Test-Path $Plink)) { Fail "plink not found: $Plink" }

Invoke-RegressionStep "qemu status" {
    & (Join-Path $PSScriptRoot "qemu-status-minute.ps1") -Once -NoRemediate
}

if (-not $NoPrepareProfile) {
    Invoke-RegressionStep "prepare regression profile" {
        Prepare-RegressionProfile
    }
}

Invoke-RegressionStep "ready strict fleet web" {
    & (Join-Path $PSScriptRoot "smoke-ready.ps1") -BaseUrl $ApiBaseUrl -StrictDependencies -StrictFleetWeb
}
Invoke-RegressionStep "auth" {
    & (Join-Path $PSScriptRoot "smoke-auth.ps1") -BaseUrl $ApiBaseUrl
}
Invoke-RegressionStep "korus web" {
    & (Join-Path $PSScriptRoot "smoke-korus-web.ps1") -WebBaseUrl $WebBaseUrl -CheckApi
}
Invoke-RegressionStep "web parity api" {
    & (Join-Path $PSScriptRoot "smoke-web-parity-api.ps1") -BaseUrl $ApiBaseUrl
}
Invoke-RegressionStep "node websocket" {
    Invoke-NodeWebSocketSmoke
}
Invoke-RegressionStep "phase5 messaging" {
    & (Join-Path $PSScriptRoot "smoke-phase5-messaging.ps1") -BaseUrl $ApiBaseUrl
}
Invoke-RegressionStep "migration import" {
    & (Join-Path $PSScriptRoot "smoke-migration-import.ps1") -BaseUrl $ApiBaseUrl
}
Invoke-RegressionStep "file resize" {
    & (Join-Path $PSScriptRoot "smoke-file-resize.ps1") -BaseUrl $ApiBaseUrl
}
Invoke-RegressionStep "read receipts" {
    $login = Invoke-RestMethod -Uri "$ApiBaseUrl/api/v1/auth/login" -Method Post -Body (@{ username = "csadmin"; password = "csadmin" } | ConvertTo-Json) -ContentType "application/json; charset=utf-8"
    $env:SMOKE_ACCESS_TOKEN = if ($login.access_token) { $login.access_token } else { $login.accessToken }
    & (Join-Path $PSScriptRoot "smoke-read-receipts.ps1") -BaseUrl "$ApiBaseUrl/api"
}
Invoke-RegressionStep "guest messaging e2e" {
    Invoke-GuestMessagingSmoke
}
Invoke-RegressionStep "push worker qemu" {
    & (Join-Path $PSScriptRoot "smoke-push-worker-qemu.ps1") -ServerSshPort $ServerSshPort
}
Invoke-RegressionStep "preview worker qemu" {
    & (Join-Path $PSScriptRoot "smoke-preview-worker-qemu.ps1") -ServerSshPort $ServerSshPort
}
Invoke-RegressionStep "turn qemu guest" {
    & (Join-Path $PSScriptRoot "smoke-turn-qemu.ps1") -GuestOnly
}
Invoke-RegressionStep "cell multi-org qemu" {
    & (Join-Path $PSScriptRoot "smoke-cell-multi-org-qemu.ps1") -BaseUrl $ApiBaseUrl
}

if (-not $SkipW1b) {
    Invoke-RegressionStep "voice message" {
        & (Join-Path $PSScriptRoot "smoke-voice-message.ps1") -BaseUrl $ApiBaseUrl -User "csadmin" -Pass "csadmin"
    }
    Invoke-RegressionStep "federation trust" {
        & (Join-Path $PSScriptRoot "smoke-federation-trust.ps1") -BaseUrl $ApiBaseUrl
    }
    Invoke-RegressionStep "federation cross-org" {
        & (Join-Path $PSScriptRoot "smoke-federation-cross-org.ps1") -BaseUrl $ApiBaseUrl
    }
    Invoke-RegressionStep "bot api" {
        & (Join-Path $PSScriptRoot "smoke-bot-api.ps1") -BaseUrl $ApiBaseUrl
    }
    Invoke-RegressionStep "ip allowlist" {
        $env:SCIM_BEARER_TOKEN = "korus-scim-lab-demo"
        & (Join-Path $PSScriptRoot "smoke-ip-allowlist.ps1") -BaseUrl $ApiBaseUrl -RequireEnforce
    }
    Invoke-RegressionStep "scim lab token" {
        $env:SCIM_BEARER_TOKEN = "korus-scim-lab-demo"
        & (Join-Path $PSScriptRoot "smoke-scim-lab-token.ps1") -BaseUrl $ApiBaseUrl
    }
}

if (-not $SkipContainerPortability) {
    Invoke-RegressionStep "container portability qemu" {
        Invoke-ContainerPortabilitySmoke
    }
}

Write-Host ""
Write-Host "[OK] QEMU one-pass regression green" -ForegroundColor Green

if ($WriteEvidence) {
    $addons = @(
        "addon-productivity", "addon-engage", "addon-search", "addon-collaboration", "addon-ai",
        "addon-live", "addon-retention", "addon-archive", "addon-deep-archive", "addon-export",
        "addon-enterprise-auth", "addon-e2ee", "addon-bots", "addon-integrations",
        "addon-federation", "addon-dlp", "addon-migration-import"
    )
    & (Join-Path $PSScriptRoot "Write-VmaEvidence.ps1") -Level L2 -Gates @{ W1_regression = "PASS"; buildIntegrity = "NOT_RUN" } -AddonsEnabled $addons
}
