# Validates korus-web/.env for two-host (LAN) deployment.
function Test-KorusWebHostEnv {
    param(
        [Parameter(Mandatory)][string]$EnvFilePath
    )
    if (-not (Test-Path $EnvFilePath)) {
        Write-Warning "korus-web/.env not found. Copy deploy/two-host/web.env.example -> korus-web/.env"
        return
    }
    $vars = @{}
    foreach ($line in Get-Content $EnvFilePath) {
        if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$') {
            $vars[$Matches[1]] = $Matches[2].Trim().Trim('"').Trim("'")
        }
    }
    $serverHost = $vars['KORUS_SERVER_HOST']
    if (-not $serverHost) {
        Write-Host "Tip: set KORUS_SERVER_HOST in korus-web/.env for two-host checks (deploy/two-host/web.env.example)." -ForegroundColor DarkGray
        return
    }
    $bad = @('localhost', '127.0.0.1', 'host.docker.internal')
    foreach ($key in @('WEB_CLIENT_API_UPSTREAM', 'WEB_CLIENT_WS_PUBLIC_URL', 'KORUS_WS_GATEWAY_HOST')) {
        $val = $vars[$key]
        if (-not $val) { continue }
        foreach ($b in $bad) {
            if ($val -like "*$b*") {
                Write-Warning "Two-host: $key contains '$b' but KORUS_SERVER_HOST=$serverHost. Use LAN IP of server/web machine."
            }
        }
    }
    $wsUrl = $vars['WEB_CLIENT_WS_PUBLIC_URL']
    if ($wsUrl -and $wsUrl -match 'ws://' -and $wsUrl -like "*${serverHost}*") {
        Write-Warning "WEB_CLIENT_WS_PUBLIC_URL should use WEB machine IP and lb port (9088), not server IP ($serverHost). For hot-swap without lb use ws://${serverHost}:8082/ws."
    }
}
