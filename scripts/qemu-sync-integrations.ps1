# Sync repo to korus-integrations guest and rebuild compose stack (spec 014).
param(
    [switch]$BuildOnly,
    [switch]$MocksOnly,
    [switch]$OneAtATime,
    [string[]]$Services = @(),
    [switch]$ForceLock,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"
Usage: .\scripts\qemu-sync-integrations.ps1 [-BuildOnly] [-MocksOnly] [-OneAtATime] [-Services a,b] [-ForceLock]

Requires: korus-integrations VM (.\scripts\qemu-integrations-up.ps1), repo HTTP :18890.
-BuildOnly: skip repo sync (code already on guest).
-Services: build/up only listed compose services (default: 5 bridges). Example: -Services onec-bridge
-OneAtATime: separate SSH session per service (recommended for Gradle bridges).
-ForceLock: override guest-task lock if previous sync died without cleanup.
Guest lock: deploy/qemu/run/guest-task-integrations.lock (one plink task at a time).
"@
    exit 0
}

$root = Split-Path -Parent $PSScriptRoot
$runDir = Join-Path $root "deploy\qemu\run"
$plink = Join-Path $env:ProgramFiles "PuTTY\plink.exe"
. (Join-Path $root "deploy\qemu\lib\Update-KorusGuestRepo.ps1")
. (Join-Path $root "deploy\qemu\lib\Start-KorusRepoHttp.ps1")
. (Join-Path $root "deploy\qemu\lib\Korus-QemuGuestTaskLock.ps1")
. (Join-Path $root "deploy\qemu\lib\Invoke-KorusGuestRemoteJob.ps1")

if (-not (Test-Path $plink)) { throw "PuTTY plink not found: $plink" }

$tcp = Test-NetConnection -ComputerName 127.0.0.1 -Port 12223 -WarningAction SilentlyContinue -ErrorAction SilentlyContinue
if (-not $tcp.TcpTestSucceeded) {
    Write-Host "Integrations SSH :12223 down - run .\scripts\qemu-integrations-up.ps1" -ForegroundColor Red
    exit 2
}

$hostKey = Get-KorusEd25519HostKey -SerialPath (Join-Path $runDir "integrations-serial.log") -Role integrations -SshPort 12223
if (-not $hostKey) { throw "integrations SSH host key not ready (see integrations-serial.log)" }

$defaultBridges = @("ai-bridge", "ocr-worker", "onec-bridge", "exchange-bridge", "storage-bridge")
$buildServices = if ($Services.Count -gt 0) { @($Services) } else { $defaultBridges }

function Invoke-IntegrationsGuestBuild {
    param([string[]]$BridgeList, [switch]$Sequential)

    Stop-KorusGuestPlinkOnPort -Port 12223

    if ($Sequential) {
        foreach ($bridge in $BridgeList) {
            Write-Host "Building $bridge (background guest job)..." -ForegroundColor Cyan
            $buildScript = @"
cd /mnt/korus/integrations
export COMPOSE_PARALLEL_LIMIT=1
sudo docker compose -f docker-compose.integrations.yml build $bridge
echo bridge-built-$bridge
"@
            $jobName = "build-$bridge"
            $ok = Invoke-KorusGuestRemoteJob -Plink $plink -HostKey $hostKey -Port 12223 -JobName $jobName -Script $buildScript -TimeoutMin 150
            if (-not $ok) {
                throw "build failed for $bridge"
            }
            Write-Host "[OK] $bridge" -ForegroundColor Green
        }
    } else {
        $svc = $BridgeList -join " "
        $composeCmd = @"
set -e
cd /mnt/korus/integrations
sudo docker compose -f docker-compose.integrations.yml build $svc 2>&1
echo integrations-build-ok
"@
        $out = Invoke-PlinkShell -Plink $plink -HostKey $hostKey -Port 12223 -Script $composeCmd
        Write-Host $out
        if ($out -notmatch "integrations-build-ok") {
            throw "integrations compose build failed"
        }
    }

    Write-Host "Starting integrations stack..." -ForegroundColor Cyan
    $upScript = @'
cd /mnt/korus/integrations
sudo docker compose -f docker-compose.integrations.yml up -d --remove-orphans
for i in 1 2 3 4 5 6 7 8 9 10; do
  curl -sf http://127.0.0.1:8090/health && echo integrations-gateway-ok && exit 0
  sleep 3
done
exit 1
'@
    $upOk = Invoke-KorusGuestRemoteJob -Plink $plink -HostKey $hostKey -Port 12223 -JobName "integrations-up" -Script $upScript -TimeoutMin 15 -PollSec 10
    if (-not $upOk) {
        throw "integrations compose up or gateway health failed"
    }
    return "integrations-gateway-ok"
}

$taskName = if ($MocksOnly) { "integrations-sync-mocks" } else { "integrations-sync" }
$script:out = $null

try {
    Invoke-KorusGuestTaskLocked -RunDir $runDir -Guest integrations -TaskName $taskName -ForceLock:$ForceLock -Action {
        if (-not $BuildOnly -and -not $MocksOnly) {
            . (Join-Path $root "deploy\qemu\lib\New-KorusRepoSnapshot.ps1")
            Stop-KorusRepoHttp
            New-KorusRepoSnapshot -StopRepoHttp -Force | Out-Null
            Start-KorusRepoHttp | Out-Null
            Write-Host "Syncing repo to integrations guest..." -ForegroundColor Cyan
            Update-KorusGuestRepo -Role integrations -SshPort 12223 -HostKey $hostKey -Plink $plink | Out-Null
        }

        if ($MocksOnly) {
            $composeCmd = @'
set -e
cd /mnt/korus/integrations
sudo docker compose -f docker-compose.integrations.yml up -d mock-apis 2>&1
sudo docker compose -f docker-compose.integrations.yml restart mock-apis 2>&1
curl -sf http://127.0.0.1:8080/health && echo mock-apis-ok
curl -sf http://127.0.0.1:8090/health && echo integrations-gateway-ok
'@
            Write-Host "Restarting mock-apis on guest..." -ForegroundColor Cyan
            $script:out = Invoke-PlinkShell -Plink $plink -HostKey $hostKey -Port 12223 -Script $composeCmd
            Write-Host $script:out
        } else {
            $script:out = Invoke-IntegrationsGuestBuild -BridgeList $buildServices -Sequential:$OneAtATime
        }
    }
} catch {
    Write-Host "[FAIL] $_" -ForegroundColor Red
    exit 1
}

if ($out -notmatch 'integrations-gateway-ok') {
    Write-Host "[WARN] gateway health not confirmed; run .\scripts\smoke-integrations-gate.ps1" -ForegroundColor Yellow
    exit 1
}
Write-Host "[OK] integrations guest synced" -ForegroundColor Green
