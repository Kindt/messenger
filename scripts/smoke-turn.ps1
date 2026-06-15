# TURN/coturn reachability smoke (Sprint B P1-2). Probes host:3478 and optional web ICE config.
param(
    [string]$TurnHost = "127.0.0.1",
    [int]$TurnPort = 3478,
    [string]$WebBaseUrl = ""
)

$ErrorActionPreference = "Stop"
$tcp = Test-NetConnection -ComputerName $TurnHost -Port $TurnPort -WarningAction SilentlyContinue
if (-not $tcp.TcpTestSucceeded) {
    Write-Error "TURN TCP $TurnHost`:$TurnPort not reachable"
}
Write-Host "[OK] TURN TCP $TurnHost`:$TurnPort"

if ($WebBaseUrl) {
    $envJs = Invoke-WebRequest -Uri "$($WebBaseUrl.TrimEnd('/'))/web-client-env.js" -UseBasicParsing
    if ($envJs.Content -notmatch "turn:") {
        Write-Error "web-client-env.js has no turn: ICE entry"
    }
    Write-Host "[OK] web-client-env.js contains turn ICE servers"
}
