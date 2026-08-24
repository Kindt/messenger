#Requires -Version 5.1
<#
.SYNOPSIS
  Единый фасад подъёма lab stack (2 VM: server + web) — от холодного старта до hotswap.

.DESCRIPTION
  Профиль dev (server-dev / web-dev) — ежедневная работа.
  Два агента: server (API :18080) и web (UI :19088); общий статус в deploy/qemu/run/server-stack-status.json.

.EXAMPLE
  .\scripts\Start-KorusServerStack.ps1 -Mode status

.EXAMPLE
  .\scripts\Start-KorusServerStack.ps1 -Mode warm

.EXAMPLE
  .\scripts\Start-KorusServerStack.ps1 -Mode sync-api-hotswap

.EXAMPLE
  .\scripts\Start-KorusServerStack.ps1 -EmitPrompt -Agent server
#>
[CmdletBinding()]
param(
    [ValidateSet(
        'status', 'stop',
        'cold', 'fast', 'warm', 'wait',
        'remediate', 'fix-api',
        'sync-api', 'sync-api-core', 'sync-api-hotswap', 'enable-api-hotswap', 'sync-workers',
        'sync-web', 'sync-ui', 'enable-web-hotswap',
        'rebuild-api', 'rebuild-web',
        'full', 'monitored'
    )]
    [string] $Mode = 'status',

    [ValidateSet('server', 'web', 'both')]
    [string] $Agent = 'both',

    [switch] $FreshDisks,
    [switch] $Rebuild,
    [switch] $Force,
    [switch] $Graphical,
    [switch] $RequireReady,
    [switch] $LaunchRebuildIfNeeded,
    [int] $MaxMinutes = 30,
    [switch] $EmitPrompt,
    [switch] $Help
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$RunDir = Join-Path $Root 'deploy\qemu\run'
$StatusPath = Join-Path $RunDir 'server-stack-status.json'
$FeedPath = Join-Path $RunDir 'server-stack-latest-prompt.txt'
$PromptCatalog = Join-Path $Root '.cursor\prompts\qemu-server-stack-pipeline.md'

$ModeMatrix = [ordered]@{
  stop               = @{ tier = 0;  agent = 'both';   script = 'qemu-down'; summary = 'Stop both VMs' }
  cold               = @{ tier = 1;  agent = 'both';   script = 'qemu-up (new disks)'; summary = 'Cold bootstrap (hours, first run)' }
  fast               = @{ tier = 2;  agent = 'both';   script = 'qemu-fast-up'; summary = 'WHPX + KeepDisks restart' }
  warm               = @{ tier = 3;  agent = 'both';   script = 'qemu-fast-up + stack-wait'; summary = 'Daily dev profile start' }
  wait               = @{ tier = 3;  agent = 'both';   script = 'Wait-KorusLabStackReady'; summary = 'Wait API/UI/ready' }
  remediate          = @{ tier = 4;  agent = 'server'; script = 'Invoke-KorusGuestStackRemediate'; summary = 'Guest compose up without build' }
  'fix-api'          = @{ tier = 4;  agent = 'server'; script = 'perf/run-qemu-fix-core-api'; summary = 'Quick core-api recreate' }
  'sync-api'         = @{ tier = 5;  agent = 'server'; script = 'qemu-redeploy -ServerOnly'; summary = 'Ansible sync, no docker build' }
  'sync-api-core'    = @{ tier = 6;  agent = 'server'; script = 'qemu-sync-api-core'; summary = 'Rebuild core-api only (5-15 min)' }
  'enable-api-hotswap' = @{ tier = 7; agent = 'server'; script = 'qemu-api-hotswap -Enable'; summary = 'Bind-mount lib/ + HOT_RELOAD' }
  'sync-api-hotswap' = @{ tier = 8;  agent = 'server'; script = 'qemu-api-hotswap -SyncOnly'; summary = 'Gradle installDist + JVM reload (1-3 min)' }
  'sync-workers'     = @{ tier = 6;  agent = 'server'; script = 'qemu-sync-workers'; summary = 'Workers rebuild' }
  'sync-web'         = @{ tier = 5;  agent = 'web';    script = 'qemu-redeploy -WebOnly'; summary = 'Ansible sync web guest' }
  'sync-ui'          = @{ tier = 9;  agent = 'web';    script = 'qemu-web-sync'; summary = 'JS/CSS/Tailwind (seconds)' }
  'enable-web-hotswap' = @{ tier = 7; agent = 'web';  script = 'qemu-web-hotswap -Enable'; summary = 'lb -> web-dev overlay' }
  'rebuild-api'      = @{ tier = 10; agent = 'server'; script = 'qemu-redeploy -ServerOnly -Rebuild'; summary = 'Full docker build server' }
  'rebuild-web'      = @{ tier = 10; agent = 'web';    script = 'qemu-redeploy -WebOnly -Rebuild'; summary = 'Full docker build web' }
  full               = @{ tier = 1;  agent = 'both';   script = 'qemu-full-stack-up'; summary = 'Full profile disks' }
  monitored          = @{ tier = 99; agent = 'both';   script = 'qemu-redeploy-monitored'; summary = 'Auto redeploy + remediate loop' }
  status             = @{ tier = -1; agent = 'both';   script = 'qemu-dev-mode -Mode status'; summary = 'VM/API/UI snapshot' }
}

function Write-Utf8File {
    param([string] $Path, [string] $Content)
    $dir = Split-Path -Parent $Path
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    $utf8Bom = New-Object System.Text.UTF8Encoding $true
    [System.IO.File]::WriteAllText($Path, $Content, $utf8Bom)
}

function Get-DefaultStatus {
    [ordered]@{
        schema_version = 1
        profile        = 'dev'
        mode           = 'status'
        agent          = 'both'
        api_ready      = $false
        web_ready      = $false
        updated_at     = (Get-Date).ToUniversalTime().ToString('o')
        last_command   = $null
        history        = @()
    }
}

function Read-StackStatus {
    if (-not (Test-Path $StatusPath)) { return $null }
    Get-Content -Raw -Path $StatusPath -Encoding UTF8 | ConvertFrom-Json
}

function Write-StackStatus {
    param(
        [string] $ModeName,
        [string] $AgentName,
        [string] $Command,
        [int] $ExitCode,
        [bool] $ApiReady = $false,
        [bool] $WebReady = $false
    )
    $prev = Read-StackStatus
    $st = Get-DefaultStatus
    if ($prev) {
        foreach ($p in $prev.PSObject.Properties) {
            if ($p.Name -notin @('mode', 'agent', 'api_ready', 'web_ready', 'updated_at', 'last_command', 'history')) {
                $st[$p.Name] = $p.Value
            }
        }
    }
    $st.mode = $ModeName
    $st.agent = $AgentName
    $st.api_ready = $ApiReady
    $st.web_ready = $WebReady
    $st.updated_at = (Get-Date).ToUniversalTime().ToString('o')
    $st.last_command = $Command
    $hist = @()
    if ($prev -and $prev.history) { $hist = @($prev.history) }
    $hist += [ordered]@{
        at       = (Get-Date).ToUniversalTime().ToString('o')
        mode     = $ModeName
        agent    = $AgentName
        command  = $Command
        exit     = $ExitCode
    }
    if ($hist.Count -gt 40) { $hist = $hist[-40..-1] }
    $st.history = $hist
    Write-Utf8File -Path $StatusPath -Content ($st | ConvertTo-Json -Depth 8)
}

function Test-StackHttp {
    $api = curl.exe -sS -m 10 -o NUL -w '%{http_code}' 'http://127.0.0.1:18080/api/v1/health' 2>$null
    $web = curl.exe -sS -m 10 -o NUL -w '%{http_code}' 'http://127.0.0.1:19088/' 2>$null
    $ready = $false
    if ("$api" -match '^2') {
        try {
            $rd = Invoke-RestMethod -Uri 'http://127.0.0.1:18080/api/v1/health/ready' -TimeoutSec 10
            $ready = [bool]$rd.database_ok
        } catch { $ready = $false }
    }
    return @{
        Api   = ("$api".Trim() -match '^2')
        Web   = ("$web".Trim() -match '^2')
        Ready = $ready
        ApiCode = "$api".Trim()
        WebCode = "$web".Trim()
    }
}

function Show-Help {
    Write-Host @"

Korus server stack facade (2 VM: server + web, profile dev).

  .\scripts\Start-KorusServerStack.ps1 -Mode <mode> [-Agent server|web|both] [-Force] [-FreshDisks]

Modes (cold -> hotswap):
  stop, cold, fast, warm, wait
  remediate, fix-api
  sync-api, sync-api-core, sync-workers, rebuild-api
  enable-api-hotswap, sync-api-hotswap
  sync-web, sync-ui, enable-web-hotswap, rebuild-web
  full, monitored, status

2 agents:
  -Agent server  -- API / core-api / workers
  -Agent web     -- UI / web-sync / web-hotswap
  -EmitPrompt    -- prompt for selected agent

Status: deploy\qemu\run\server-stack-status.json
Prompt: .cursor\prompts\qemu-server-stack-pipeline.md

Legacy facade: .\scripts\qemu-dev-mode.ps1 -Mode <mode>

"@
    Write-Host 'Matrix:' -ForegroundColor Cyan
    foreach ($k in $ModeMatrix.Keys) {
        $m = $ModeMatrix[$k]
        Write-Host ("  {0,-20} tier={1} agent={2,-6} {3}" -f $k, $m.tier, $m.agent, $m.summary)
    }
}

function Emit-AgentPrompt {
    param([string] $AgentName)
    $http = Test-StackHttp
    $lines = @(
        "# Korus server stack -- agent: $AgentName",
        "",
        "Status: API=$($http.ApiCode) UI=$($http.WebCode) ready=$($http.Ready)",
        "Profile: dev (server-dev + web-dev), WHPX required, host Docker forbidden.",
        "",
        "## Role"
    )
    if ($AgentName -eq 'server') {
        $lines += @(
            '**Server agent** -- QEMU server VM, API :18080, core-api, workers, remediate.',
            '',
            '### Commands (lightest to heaviest)',
            '```powershell',
            '.\scripts\Start-KorusServerStack.ps1 -Mode status',
            '.\scripts\Start-KorusServerStack.ps1 -Mode fast',
            '.\scripts\Start-KorusServerStack.ps1 -Mode warm',
            '.\scripts\Start-KorusServerStack.ps1 -Mode sync-api',
            '.\scripts\Start-KorusServerStack.ps1 -Mode sync-api-core',
            '.\scripts\qemu-guest-job.ps1 -Loop',
            '.\scripts\Start-KorusServerStack.ps1 -Mode enable-api-hotswap',
            '.\scripts\Start-KorusServerStack.ps1 -Mode sync-api-hotswap',
            '.\scripts\Start-KorusServerStack.ps1 -Mode remediate',
            '.\scripts\Start-KorusServerStack.ps1 -Mode fix-api',
            '.\scripts\Start-KorusServerStack.ps1 -Mode rebuild-api -Force',
            '```',
            '',
            'Success: curl http://127.0.0.1:18080/api/v1/health -> 200, /ready database_ok=true'
        )
    } elseif ($AgentName -eq 'web') {
        $lines += @(
            '**Web agent** -- QEMU web VM, UI :19088, web-client, hotswap overlay.',
            '',
            '### Commands',
            '```powershell',
            '.\scripts\Start-KorusServerStack.ps1 -Mode status',
            '.\scripts\Start-KorusServerStack.ps1 -Mode sync-web',
            '.\scripts\Start-KorusServerStack.ps1 -Mode sync-ui',
            '.\scripts\Start-KorusServerStack.ps1 -Mode enable-web-hotswap',
            '.\scripts\Start-KorusServerStack.ps1 -Mode rebuild-web -Force',
            '```',
            '',
            'Success: curl http://127.0.0.1:19088/ -> 200'
        )
    } else {
        $lines += @(
            '**Orchestrator** -- coordinate server + web agents.',
            '',
            '1. .\scripts\Start-KorusServerStack.ps1 -Mode fast or -Mode warm',
            '2. Server agent: API healthy',
            '3. Web agent: UI healthy',
            '4. Java loop: -Mode sync-api-hotswap; UI: -Mode sync-ui',
            '',
            'Full matrix: .\scripts\Start-KorusServerStack.ps1 -Help'
        )
    }
  $text = $lines -join "`n"
    Write-Utf8File -Path $FeedPath -Content $text
    if (Test-Path $PromptCatalog) {
        $catalog = Get-Content -Raw -Path $PromptCatalog -Encoding UTF8
        $inject = "<!-- AGENT-INJECT:START -->`n$text`n<!-- AGENT-INJECT:END -->"
        if ($catalog -match '(?s)<!-- AGENT-INJECT:START -->.*?<!-- AGENT-INJECT:END -->') {
            $catalog = $catalog -replace '(?s)<!-- AGENT-INJECT:START -->.*?<!-- AGENT-INJECT:END -->', $inject
        } else {
            $catalog += "`n`n$inject"
        }
        Write-Utf8File -Path $PromptCatalog -Content $catalog
    }
    Write-Host $text
}

if ($Help) { Show-Help; exit 0 }
if ($EmitPrompt) { Emit-AgentPrompt -AgentName $Agent; exit 0 }

# Agent filter: skip modes not owned by this agent unless both
if ($Agent -ne 'both' -and $Mode -ne 'status' -and $ModeMatrix.Contains($Mode)) {
    $owner = $ModeMatrix[$Mode].agent
    if ($owner -ne 'both' -and $owner -ne $Agent) {
        Write-Error "Mode '$Mode' is for agent '$owner', not '$Agent'. Use -Agent both or pick another mode."
    }
}

$exitCode = 0
$cmdLabel = "Start-KorusServerStack -Mode $Mode"

try {
    switch ($Mode) {
        'status' {
            & (Join-Path $Root 'scripts\qemu-dev-mode.ps1') -Mode status
            $exitCode = $LASTEXITCODE
        }
        'stop' {
            & (Join-Path $Root 'scripts\qemu-dev-mode.ps1') -Mode stop
            $exitCode = $LASTEXITCODE
        }
        'cold' {
            Remove-Item Env:KORUS_QEMU_FORCE_TCG -ErrorAction SilentlyContinue
            if ($FreshDisks) {
                & (Join-Path $Root 'scripts\qemu-down.ps1')
                if ($LASTEXITCODE -ne 0) { throw "qemu-down failed" }
                . (Join-Path $Root 'deploy\qemu\lib\Reset-KorusVmDisks.ps1')
                Reset-KorusVmDisks -StackProfile dev
                $upArgs = @{ StackProfile = 'dev' }
                if ($Graphical) { $upArgs.Graphical = $true }
                & (Join-Path $Root 'scripts\qemu-up.ps1') @upArgs
            } else {
                $upArgs = @{ StackProfile = 'dev' }
                if ($Graphical) { $upArgs.Graphical = $true }
                & (Join-Path $Root 'scripts\qemu-up.ps1') @upArgs
            }
            $exitCode = $LASTEXITCODE
            if ($exitCode -eq 0) {
                & (Join-Path $Root 'scripts\qemu-stack-wait.ps1') -MaxMinutes $MaxMinutes
                $exitCode = $LASTEXITCODE
            }
        }
        'fast' {
            & (Join-Path $Root 'scripts\qemu-fast-up.ps1')
            $exitCode = $LASTEXITCODE
        }
        'warm' {
            & (Join-Path $Root 'scripts\qemu-fast-up.ps1')
            if ($LASTEXITCODE -ne 0) { $exitCode = $LASTEXITCODE; break }
            & (Join-Path $Root 'scripts\qemu-stack-wait.ps1') -MaxMinutes $MaxMinutes -RedeployWhenSshUp
            $exitCode = $LASTEXITCODE
        }
        'wait' {
            $waitArgs = @{ MaxMinutes = $MaxMinutes; WarmIfDown = $true }
            if ($RequireReady) { $waitArgs.RequireReady = $true }
            if ($LaunchRebuildIfNeeded) { $waitArgs.LaunchRebuildIfNeeded = $true }
            & (Join-Path $Root 'scripts\Wait-KorusLabStackReady.ps1') @waitArgs
            $exitCode = $LASTEXITCODE
        }
        'remediate' {
            . (Join-Path $Root 'deploy\qemu\lib\Invoke-KorusGuestStackRemediate.ps1')
            $ok = Invoke-KorusGuestStackRemediate -Root $Root -RunDir $RunDir -OnLog { param($m) Write-Host $m }
            if (-not $ok) { $exitCode = 1 } else {
                & (Join-Path $Root 'scripts\qemu-stack-wait.ps1') -MaxMinutes 15
                $exitCode = $LASTEXITCODE
            }
        }
        'fix-api' {
            & (Join-Path $Root 'scripts\perf\run-qemu-fix-core-api.ps1')
            $exitCode = $LASTEXITCODE
        }
        'sync-api' {
            $p = @{ ServerOnly = $true }
            if ($Force) { $p.Force = $true }
            & (Join-Path $Root 'scripts\qemu-redeploy.ps1') @p
            $exitCode = $LASTEXITCODE
        }
        'sync-api-core' {
            $p = @{}
            if ($Force) { $p.ForceLock = $true }
            & (Join-Path $Root 'scripts\qemu-sync-api-core.ps1') @p
            $exitCode = $LASTEXITCODE
        }
        'sync-api-hotswap' {
            & (Join-Path $Root 'scripts\qemu-api-hotswap.ps1') -SyncOnly
            $exitCode = $LASTEXITCODE
        }
        'enable-api-hotswap' {
            & (Join-Path $Root 'scripts\qemu-api-hotswap.ps1') -Enable
            $exitCode = $LASTEXITCODE
        }
        'sync-workers' {
            & (Join-Path $Root 'scripts\qemu-sync-workers.ps1')
            $exitCode = $LASTEXITCODE
        }
        'sync-web' {
            $p = @{ WebOnly = $true }
            if ($Force) { $p.Force = $true }
            & (Join-Path $Root 'scripts\qemu-redeploy.ps1') @p
            $exitCode = $LASTEXITCODE
        }
        'sync-ui' {
            & (Join-Path $Root 'scripts\qemu-web-sync.ps1')
            $exitCode = $LASTEXITCODE
        }
        'enable-web-hotswap' {
            & (Join-Path $Root 'scripts\qemu-web-hotswap.ps1') -Enable
            $exitCode = $LASTEXITCODE
        }
        'rebuild-api' {
            $p = @{ ServerOnly = $true; Rebuild = $true }
            if ($Force) { $p.Force = $true }
            & (Join-Path $Root 'scripts\qemu-redeploy.ps1') @p
            $exitCode = $LASTEXITCODE
        }
        'rebuild-web' {
            $p = @{ WebOnly = $true; Rebuild = $true }
            if ($Force) { $p.Force = $true }
            & (Join-Path $Root 'scripts\qemu-redeploy.ps1') @p
            $exitCode = $LASTEXITCODE
        }
        'full' {
            $p = @{}
            if ($FreshDisks) { $p.FreshDisks = $true }
            if ($Rebuild) { $p.Rebuild = $true }
            if ($Force) { $p.Force = $true }
            & (Join-Path $Root 'scripts\qemu-full-stack-up.ps1') @p
            $exitCode = $LASTEXITCODE
        }
        'monitored' {
            $p = @{ Target = 'both'; MaxCycles = 5 }
            if ($Force) { $p.Force = $true }
            if ($Rebuild) { $p.Rebuild = $true }
            & (Join-Path $Root 'scripts\qemu-redeploy-monitored.ps1') @p
            $exitCode = $LASTEXITCODE
        }
    }
} catch {
    Write-Host "[FAIL] $($_.Exception.Message)" -ForegroundColor Red
    $exitCode = 1
}

$http = Test-StackHttp
Write-StackStatus -ModeName $Mode -AgentName $Agent -Command $cmdLabel -ExitCode $exitCode -ApiReady $http.Api -WebReady $http.Web

if ($exitCode -eq 0 -and $http.Api -and $http.Web) {
    Write-Host "[OK] Stack ready API=$($http.ApiCode) UI=$($http.WebCode) ready=$($http.Ready)" -ForegroundColor Green
} elseif ($exitCode -eq 0) {
    Write-Host "[WARN] Command OK but stack incomplete API=$($http.ApiCode) UI=$($http.WebCode)" -ForegroundColor Yellow
}

exit $exitCode
