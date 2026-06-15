# Smoke: TURN relay readiness (spec 010 CALL-6)
param(
    [string]$TurnHost = "127.0.0.1",
    [int]$TurnPort = 3478,
    [string]$WebBaseUrl = "http://127.0.0.1:19088"
)

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
& "$scriptDir\smoke-turn.ps1" -TurnHost $TurnHost -TurnPort $TurnPort -WebBaseUrl $WebBaseUrl

if ($WebBaseUrl) {
    $envJs = (Invoke-WebRequest -Uri "$($WebBaseUrl.TrimEnd('/'))/web-client-env.js" -UseBasicParsing).Content
    if ($envJs -notmatch 'credential') {
        Write-Error "web-client-env.js ICE servers missing TURN credential"
    }
    Write-Host "[OK] TURN ICE credentials present in web-client-env.js"
}

Write-Host "[OK] TURN relay smoke (reachability + ICE config)"
