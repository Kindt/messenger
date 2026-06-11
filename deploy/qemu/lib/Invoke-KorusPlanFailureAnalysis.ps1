# Analyze smoke / Playwright / gate-report failures; recommend targeted remediation (no blind retry).

function Get-KorusPlanFailureAnalysisPath {
    param([Parameter(Mandatory)][string]$RunDir)
    Join-Path $RunDir "plan-failure-analysis.json"
}

function Test-KorusPlanPlaywrightPreflight {
    param(
        [Parameter(Mandatory)][string]$Root,
        [Parameter(Mandatory)][string]$RunDir
    )
    $issues = @()
    $webUrl = if ($env:KORUS_WEB_URL) { $env:KORUS_WEB_URL } else { "http://127.0.0.1:19088" }
    $apiUrl = if ($env:KORUS_API_URL) { $env:KORUS_API_URL } else { "http://127.0.0.1:18080" }
    $pwBase = if ($env:PLAYWRIGHT_BASE_URL) { $env:PLAYWRIGHT_BASE_URL } else { $webUrl }

    if ($pwBase -notmatch ':19088') {
        $issues += "PLAYWRIGHT_BASE_URL=$pwBase (expected host port 19088)"
    }
    if ($apiUrl -notmatch ':18080') {
        $issues += "KORUS_API_URL=$apiUrl (expected host port 18080)"
    }
    try {
        $null = Invoke-RestMethod -Uri "$apiUrl/api/v1/health" -TimeoutSec 8
    } catch {
        $issues += "API health down at $apiUrl"
    }
    try {
        $html = (Invoke-WebRequest -Uri $webUrl -UseBasicParsing -TimeoutSec 8).Content
        if ($html -notmatch 'id="u"') { $issues += "web UI missing #u login field" }
    } catch {
        $issues += "web UI down at $webUrl"
    }
    try {
        $body = '{"username":"csadmin","password":"csadmin"}'
        $r = Invoke-WebRequest -Uri "$apiUrl/api/v1/auth/login" -Method POST -Body $body `
            -ContentType "application/json" -UseBasicParsing -TimeoutSec 10
        if ($r.StatusCode -ne 200) { $issues += "csadmin login HTTP $($r.StatusCode)" }
    } catch {
        $issues += "csadmin login failed: $($_.Exception.Message)"
    }
    try {
        $reg = '{"username":"smoke_user_a","password":"smokepass123","display_name":"Smoke User A"}'
        $r = Invoke-WebRequest -Uri "$apiUrl/api/v1/auth/register" -Method POST -Body $reg `
            -ContentType "application/json" -UseBasicParsing -TimeoutSec 10
        if ($r.StatusCode -notin @(200, 201, 409)) { $issues += "smoke_user_a register HTTP $($r.StatusCode)" }
    } catch {
        $issues += "smoke_user_a register failed: $($_.Exception.Message)"
    }

    return @{
        Ok     = ($issues.Count -eq 0)
        Issues = $issues
        WebUrl = $webUrl
        ApiUrl = $apiUrl
        PwBase = $pwBase
    }
}

function Invoke-KorusPlanFailureAnalysis {
    param(
        [Parameter(Mandatory)]
        [ValidateSet("smoke", "playwright", "gate_report")]
        [string]$Kind,
        [Parameter(Mandatory)][string]$RunDir,
        [Parameter(Mandatory)][string]$Root,
        [string]$LastError = ""
    )

    $at = (Get-Date).ToString("o")
    $categories = @{}
    $samples = @()
    $failed = 0
    $passed = 0
    $recommended = "none"
    $codeFix = $false
    $summaryRu = ""
    $preflight = $null

    if ($Kind -eq "playwright") {
        $pwLog = Join-Path $RunDir "playwright-orchestrator.log"
        $text = ""
        if (Test-Path $pwLog) { $text = Get-Content $pwLog -Raw -ErrorAction SilentlyContinue }

        $env:KORUS_WEB_URL = "http://127.0.0.1:19088"
        $env:PLAYWRIGHT_BASE_URL = $env:KORUS_WEB_URL
        $env:KORUS_API_URL = "http://127.0.0.1:18080"
        $preflight = Test-KorusPlanPlaywrightPreflight -Root $Root -RunDir $RunDir

        if ($text -match '(\d+)\s+failed') { $failed = [int]$Matches[1] }
        if ($text -match '(\d+)\s+passed') { $passed = [int]$Matches[1] }

        if ($text -match 'ERR_CONNECTION_REFUSED at http://127\.0\.0\.1:9088') {
            $categories["web_wrong_port"] = 1
            $samples += "Playwright used :9088 instead of :19088"
        }
        if ($text -match 'login failed for csadmin: 401') {
            $categories["api_csadmin_401"] = 1
            $samples += "csadmin API login 401"
        }
        if ($text -match 'register smoke_user_[abc]: 503') {
            $categories["api_register_503"] = 1
            $samples += "smoke user register 503"
        }
        if ($text -match 'strict mode violation') {
            $categories["test_strict_mode"] = 1
            $samples += "getByText strict mode (duplicate DOM nodes)"
        }
        if ($text -match 'data-testid=logout|uiLogin|auth-submit') {
            if ($text -match 'login failed|toBeVisible.*logout|Timeout.*logout') {
                $categories["ui_login_timeout"] = 1
                $samples += "UI login: logout/composer not visible in time"
            }
        }
        if ($text -match 'page\.goto: net::ERR_CONNECTION_REFUSED') {
            $categories["web_unreachable"] = 1
            $samples += "browser cannot reach web base URL"
        }
        if ($text -match 'login failed for smoke_user') {
            $categories["api_smoke_auth"] = 1
            $samples += "smoke_user API login failed"
        }

        if (-not $preflight.Ok) {
            $categories["preflight_fail"] = 1
            foreach ($i in $preflight.Issues) { if ($samples.Count -lt 5) { $samples += $i } }
        }

        if ($categories.ContainsKey("web_wrong_port") -or ($preflight -and $preflight.PwBase -notmatch ':19088')) {
            $recommended = "fix_playwright_env"
            $summaryRu = "Playwright ходит не на :19088. Проверить PLAYWRIGHT_BASE_URL/KORUS_WEB_URL в orchestrator и playwright.config."
        }
        elseif ($categories.ContainsKey("api_csadmin_401")) {
            $recommended = "ensure_keycloak_dev_users"
            $summaryRu = "csadmin login 401. Запустить keycloak-ensure-dev-users на server guest."
        }
        elseif ($categories.ContainsKey("api_register_503") -or $categories.ContainsKey("preflight_fail")) {
            if ($preflight.Issues -match "health down|register") {
                $recommended = "wait_stack"
                $summaryRu = "API/register не готов. Ждать stack ready, не гонять Playwright."
            }
            elseif ($preflight.Issues -match "csadmin") {
                $recommended = "ensure_keycloak_dev_users"
                $summaryRu = "Preflight: csadmin auth. keycloak-ensure-dev-users на server."
            }
            else {
                $recommended = "preflight_retry"
                $summaryRu = "Preflight failed: $($preflight.Issues -join '; ')"
            }
        }
        elseif ($categories.ContainsKey("test_strict_mode")) {
            $recommended = "fix_tests_in_repo"
            $codeFix = $true
            $summaryRu = "Flaky/fail tests: strict mode violation (duplicate text in chat list + message). Правка fixtures/ui.ts или specs."
        }
        elseif ($categories.ContainsKey("ui_login_timeout")) {
            $recommended = "analyze_web_auth_proxy"
            $summaryRu = "UI login timeout. Проверить web->API proxy, Keycloak, wsUrl; смотреть test-results error-context."
        }
        elseif ($failed -gt 0 -and $passed -gt 0) {
            $recommended = "fix_tests_in_repo"
            $codeFix = $true
            $summaryRu = "Playwright: $passed pass / $failed fail. API частично OK, UI/tests -- разбор логов и test-results, не blind retry."
        }
        elseif ($failed -gt 0) {
            $recommended = "analyze_playwright_log"
            $summaryRu = "Playwright: $failed fail / $passed pass. Разбор playwright-orchestrator.log и test-results."
        }
        else {
            $recommended = "preflight_retry"
            $summaryRu = "Playwright exit non-zero без parsed summary; проверить preflight и лог."
        }
    }
    elseif ($Kind -eq "smoke") {
        $smokeLog = Join-Path $RunDir "smoke-last.log"
        $text = $LastError
        if (Test-Path $smokeLog) { $text += "`n" + (Get-Content $smokeLog -Raw -ErrorAction SilentlyContinue) }

        . (Join-Path $Root "deploy\qemu\lib\Get-KorusLanHostIp.ps1")
        Write-KorusQemuLanHostInfo -RunDir $RunDir | Out-Null
        if (Test-KorusWebClientWsHostMismatch -RunDir $RunDir) {
            $categories["ws_url_mismatch"] = 1
            $recommended = "redeploy_web"
            $summaryRu = "smoke: wsUrl не совпадает с host-lan. redeploy web."
        }
        elseif ($text -match '18080|core|api' -and $text -match 'fail|error|down') {
            $categories["api_down"] = 1
            $recommended = "wait_stack_or_redeploy_server"
            $summaryRu = "smoke: API down. Ждать stack или redeploy server."
        }
        else {
            $recommended = "analyze_smoke_output"
            $summaryRu = "smoke failed ($LastError). Смотреть вывод smoke-korus-web.ps1."
        }
    }
    else {
        $recommended = "analyze_gate_report"
        $summaryRu = "runtime-gate-report failed. Проверить write-runtime-gate-report.ps1 output."
    }

    $fpParts = @($Kind)
    foreach ($k in ($categories.Keys | Sort-Object)) { $fpParts += "$k=$($categories[$k])" }
    if ($recommended -ne "none") { $fpParts += "action=$recommended" }
    $fingerprint = ($fpParts -join "|")

    $result = @{
        at                 = $at
        kind               = $Kind
        fingerprint        = $fingerprint
        categories         = $categories
        failedCount        = $failed
        passedCount        = $passed
        sampleErrors       = $samples
        recommendedAction  = $recommended
        codeFixRequired    = $codeFix
        summaryRu          = $summaryRu
        preflight          = $preflight
        lastError          = $LastError
    }

    $path = Get-KorusPlanFailureAnalysisPath -RunDir $RunDir
    $result | ConvertTo-Json -Depth 6 | Set-Content -Path $path -Encoding utf8
    return $result
}

function Invoke-KorusPlanFailureRemediate {
    param(
        [Parameter(Mandatory)][string]$Action,
        [Parameter(Mandatory)][string]$RunDir,
        [Parameter(Mandatory)][string]$Root,
        [string]$Reason = ""
    )

    $log = Join-Path $RunDir "status-remediate.log"
    $line = "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') [plan-remediate] action=$Action reason=$Reason"
    Add-Content -Path $log -Value $line -Encoding utf8

    switch ($Action) {
        "ensure_keycloak_dev_users" {
            $Plink = "${env:ProgramFiles}\PuTTY\plink.exe"
            if (-not (Test-Path $Plink)) {
                return @{ Ok = $false; Summary = "plink missing for keycloak-ensure" }
            }
            $script = Join-Path $Root "scripts\keycloak-ensure-dev-users.sh"
            if (-not (Test-Path $script)) {
                return @{ Ok = $false; Summary = "keycloak script missing" }
            }
            $remote = "bash /mnt/korus/scripts/keycloak-ensure-dev-users.sh 2>&1 | tail -20"
            $hkPath = Join-Path $RunDir "ssh-hostkeys.ps1"
            if (Test-Path $hkPath) {
                . $hkPath
                if ($script:KorusQemuSshHostKeys -and $script:KorusQemuSshHostKeys['server']) {
                    $hk = $script:KorusQemuSshHostKeys['server']
                }
            }
            if (-not $hk) { $hk = "ssh-ed25519 255 SHA256:8Qv8mEo5/yZGhKbNRMtVPzZjIt2vd2rF1lNMHxTCHxY" }
            $out = & $Plink -batch -hostkey $hk -pw korus -P 12221 "korus@127.0.0.1" $remote 2>&1
            return @{ Ok = ($LASTEXITCODE -eq 0); Summary = "keycloak-ensure: $($out -join ' ')" }
        }
        "redeploy_web" {
            . (Join-Path $Root "deploy\qemu\lib\Start-KorusQemuGuestRedeploy.ps1")
            $r = Start-KorusQemuGuestRedeploy -Role web -RunDir $RunDir -Root $Root -Reason $Reason
            return @{ Ok = [bool]$r.Started; Summary = $r.Summary }
        }
        "wait_stack_or_redeploy_server" {
            . (Join-Path $Root "deploy\qemu\lib\Start-KorusQemuGuestRedeploy.ps1")
            $r = Start-KorusQemuGuestRedeploy -Role server -RunDir $RunDir -Root $Root -Reason $Reason
            return @{ Ok = $true; Summary = "wait stack; maybe $($r.Summary)" }
        }
        "fix_playwright_env" {
            $env:KORUS_WEB_URL = "http://127.0.0.1:19088"
            $env:PLAYWRIGHT_BASE_URL = $env:KORUS_WEB_URL
            $env:KORUS_API_URL = "http://127.0.0.1:18080"
            return @{ Ok = $true; Summary = "env set to 19088/18080 for next run" }
        }
        default {
            return @{ Ok = $false; Summary = "no auto-remediate for action=$Action" }
        }
    }
}
