# Build chat summary + structured issues from minute-status inputs (ASCII-safe for PS 5.1).
function Get-KorusQemuMinuteReport {
    param(
        [int]$Tick = 0,
        [array]$QemuLines = @(),
        [hashtable]$Health = @{ Core = $false; Web = $false; Ready = $false },
        [bool]$Ssh21 = $false,
        [bool]$Ssh22 = $false,
        [string]$Activity = "",
        [string[]]$BootstrapStates = @(),
        [string]$ServerBootstrapText = "",
        [string]$RemediateSummary = "",
        [hashtable]$LoadingState = @{ Loading = $false; Kind = 'none'; Detail = '' }
    )

    $isLoading = [bool]$LoadingState.Loading
    $loadingKind = [string]$LoadingState.Kind

    $issueList = [System.Collections.ArrayList]::new()
    $stackReady = $Health.Core -and $Health.Web -and $Health.Ready

    function Add-Issue {
        param([string]$Id, [string]$Severity, [string]$Message, [string]$Action)
        [void]$issueList.Add(@{
            id       = $Id
            severity = $Severity
            message  = $Message
            action   = $Action
        })
    }

    if ($stackReady) {
        return @{
            tick       = $Tick
            at         = (Get-Date).ToString('o')
            stackReady = $true
            health     = $Health
            activity   = $Activity
            summaryRu  = 'OK: stack ready (API, UI, database_ok).'
            issues     = @()
            remediate  = $RemediateSummary
            agentHint  = 'Stack ready. Stop chat-watch if monitoring only.'
        }
    }

    $qemuText = ($QemuLines -join "`n")
    if ($QemuLines.Count -eq 0 -or $qemuText -match 'QEMU: \(none running\)|QEMU: \(no korus VMs running\)') {
        Add-Issue 'vms_down' 'high' 'Korus QEMU not running.' 'auto-remediate: qemu-up -KeepDisks'
    }

    if ($isLoading) {
        $lid = 'loading_' + ($loadingKind -replace '-', '_')
        Add-Issue $lid 'low' "Loading active: $loadingKind ($($LoadingState.Detail))." 'wait for download/build to finish'
    }

    if (-not $Health.Core -and $Health.Web -and -not $isLoading) {
        Add-Issue 'core_down_web_up' 'high' 'API 18080 down, UI 19088 up (server VM).' 'guest redeploy server'
    }
    elseif (-not $Health.Core -and $Health.Web -and $isLoading) {
        Add-Issue 'core_down_loading' 'low' 'API down while loading on server.' 'wait'
    }
    elseif (-not $Health.Core -and -not $Health.Web) {
        Add-Issue 'stack_down' 'medium' 'API and UI down.' 'wait bootstrap / auto-remediate'
    }

    if ($Health.Core -and -not $Health.Ready) {
        Add-Issue 'not_ready' 'medium' 'API up, database_ok=false.' 'wait-stack-ready on server'
    }

    if (-not $isLoading -and ($Activity -match 'bootstrap errors' -or ($BootstrapStates -contains 'error'))) {
        Add-Issue 'bootstrap_error' 'high' 'Bootstrap errors on guest.' 'redeploy server'
    }

    if (-not $isLoading -and $ServerBootstrapText -match 'keycloak.*409|error:\s*409|returned error:\s*409') {
        Add-Issue 'keycloak_409' 'medium' 'Keycloak HTTP 409 in bootstrap.' 'redeploy server with fresh snapshot'
    }
    if ($ServerBootstrapText -match 'FAILED!|fatal:\s*\[localhost\]') {
        Add-Issue 'ansible_failed' 'high' 'Ansible FAILED on server.' 'fix repo then redeploy server'
    }
    if ($ServerBootstrapText -match 'no space|OOM|out of memory') {
        Add-Issue 'disk_or_memory' 'high' 'Disk or memory pressure on server.' 'fresh disks may be needed'
    }

    if (-not $isLoading -and ($Activity -eq 'docker pull' -or ($BootstrapStates -contains 'docker-pull'))) {
        Add-Issue 'pull_stuck' 'medium' 'Docker pull slow or stuck.' 'auto-remediate pull path'
    }
    if (-not $isLoading -and ($Activity -eq 'gradle / docker build' -or ($BootstrapStates -contains 'gradle'))) {
        Add-Issue 'build_running' 'low' 'Gradle/docker build running.' 'wait'
    }
    if ($Activity -match 'guest busy|SSH slow') {
        Add-Issue 'ssh_busy' 'low' 'SSH slow on guest.' 'wait'
    }

    $healthBits = @(
        'core=' + $Health.Core
        'web=' + $Health.Web
        'ready=' + $Health.Ready
    ) -join ', '
    $sshBits = ':12221=' + $(if ($Ssh21) { 'up' } else { 'down' }) + ', :12222=' + $(if ($Ssh22) { 'up' } else { 'down' })

    $summary = 'tick=' + $Tick + ' | ' + $healthBits + ' | SSH ' + $sshBits + ' | ' + $Activity
    if ($RemediateSummary) { $summary += ' | remediate: ' + $RemediateSummary }
    $issuesArray = @($issueList.ToArray())
    if ($issuesArray.Count -gt 0) {
        $top = @($issuesArray | Where-Object { $_.severity -eq 'high' } | Select-Object -First 1)
        if ($top.Count -eq 0) { $top = @($issuesArray | Select-Object -First 1) }
        $summary += ' | ! ' + $top[0].message
    }

    $agentHint = 'QEMU minute report. Post summaryRu and issues in Russian for the user. High severity without remediate: analyze logs, fix repo, qemu-redeploy -ServerOnly. No qemu-down. Do not kill non-Korus qemu.'

    return @{
        tick       = $Tick
        at         = (Get-Date).ToString('o')
        stackReady = $false
        health     = $Health
        ssh        = @{ server = $Ssh21; web = $Ssh22 }
        activity   = $Activity
        summaryRu  = $summary
        issues     = $issuesArray
        remediate  = $RemediateSummary
        loading    = $LoadingState
        agentHint  = $agentHint
    }
}

function Convert-KorusQemuReportToRussian {
    param([hashtable]$Report)

    $i18nPath = Join-Path $PSScriptRoot 'minute-report-i18n.json'
    $ru = @{}
    if (Test-Path $i18nPath) {
        $raw = Get-Content $i18nPath -Raw -Encoding UTF8 | ConvertFrom-Json
        $raw.PSObject.Properties | ForEach-Object { $ru[$_.Name] = [string]$_.Value }
    }

    $out = @{
        tick       = $Report.tick
        at         = $Report.at
        stackReady = $Report.stackReady
        health     = $Report.health
        ssh        = $Report.ssh
        activity   = $Report.activity
        summaryRu  = $Report.summaryRu
        issues     = $Report.issues
        remediate  = $Report.remediate
        agentHint  = $Report.agentHint
    }
    if ($Report.stackReady) {
        $out.summaryRu = if ($ru.stack_ready) { $ru.stack_ready } else { 'Stack ready.' }
        return $out
    }

    $issuesRu = @()
    foreach ($iss in @($Report.issues)) {
        if (-not $iss) { continue }
        $id = [string]$iss.id
        if (-not $id) { continue }
        $msg = if ($ru -and $ru.ContainsKey($id)) { $ru[$id] } else { [string]$iss.message }
        $issuesRu += @{
            id       = $iss.id
            severity = $iss.severity
            message  = $msg
            action   = $iss.action
        }
    }
    $out.issues = $issuesRu

    if ($issuesRu.Count -gt 0) {
        $top = @($issuesRu | Where-Object { $_.severity -eq 'high' } | Select-Object -First 1)
        if ($top.Count -eq 0) { $top = @($issuesRu | Select-Object -First 1) }
        $base = [string]$Report.summaryRu
        if ($base -match ' \| ! ') {
            $base = ($base -split ' \| ! ', 2)[0]
        }
        $out.summaryRu = $base + ' | ! ' + $top[0].message
    }

    return $out
}
