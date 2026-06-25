# VP-00 static registry (spec 025 T000 / performance-acceptance-contract.md). No live stack required.
param([switch]$Help)
$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "Usage: .\scripts\perf\run-vp00-static.ps1"
    exit 0
}

$Root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $Root

function Step($id, $name, $pass, $evidence) {
    $status = if ($pass) { "PASS" } else { "FAIL" }
    $color = if ($pass) { "Green" } else { "Red" }
    Write-Host "[$status] VP-00-$id $name" -ForegroundColor $color
    if ($evidence) { Write-Host "       $evidence" -ForegroundColor DarkGray }
    return $pass
}

$results = @()
$results += Step "01" "LiveSession N+1 JOIN" (
    Select-String -Path "modules/core-api/src/main/java/com/avandocmsg/messenger/core/adapter/persistence/JdbcLiveSessionJdbcRepository.java" -Pattern "LEFT JOIN" -Quiet
) "JdbcLiveSessionJdbcRepository LEFT JOIN"

$results += Step "02" "Auth rate limit lab compose" (
    Select-String -Path "docker/docker-compose.pilot-overrides.yml" -Pattern "RATE_LIMIT_AUTH_ENABLED" -Quiet
) "docker-compose.pilot-overrides.yml"

$results += Step "03" "WsNatsDeliveryHub dispatcher" (
    Test-Path "modules/ws-gateway/src/main/java/com/avandocmsg/messenger/ws/WsNatsDeliveryHub.java"
) "WsNatsDeliveryHub.java"

$results += Step "04" "Rate limit fail-closed" (
    (Select-String -Path "modules/core-api/src/main/java/com/avandocmsg/messenger/api/config/AppConfig.java" -Pattern "rateLimitAuthFailOpen" -Quiet) -and
    (Test-Path "modules/core-api/src/test/java/com/avandocmsg/messenger/api/auth/AuthRateLimiterTest.java")
) "AppConfig + AuthRateLimiterTest"

$results += Step "05" "Pilot compose profile" (
    Test-Path "docker/docker-compose.pilot.yml"
) "docker-compose.pilot.yml"

$results += Step "06" "Redis infra + cache keys" (
    (Select-String -Path "docker/docker-compose.full-server.yml" -Pattern "redis:" -Quiet) -and
    (Select-String -Path "modules/core-api/src/main/java/com/avandocmsg/messenger/api/config/AppConfig.java" -Pattern "redis.read.cache" -Quiet)
) "compose redis + AppConfig"

$results += Step "07" "validateProductionSecrets test" (
    Test-Path "modules/core-api/src/test/java/com/avandocmsg/messenger/api/config/AppConfigProductionSecretsTest.java"
) "AppConfigProductionSecretsTest"

$results += Step "08" "Virtual message list" (
    (Select-String -Path "modules/web-client/src/main/resources/webui/ui-message-list.js" -Pattern "VIRTUAL_THRESHOLD|renderVirtualMessages" -Quiet)
) "ui-message-list.js"

$fail = @($results | Where-Object { -not $_ }).Count
if ($fail -gt 0) {
    Write-Host "VP-00: $fail FAIL" -ForegroundColor Red
    exit 1
}
Write-Host "VP-00: all PASS (static)" -ForegroundColor Green
exit 0
