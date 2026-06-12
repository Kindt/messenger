function Test-KorusHostApiHealth {
    try {
        $code = curl.exe -sS -m 8 -o NUL -w "%{http_code}" "http://127.0.0.1:18080/api/v1/health" 2>$null
        return ($code -match '^2')
    } catch { return $false }
}

function Test-KorusHostApiReady {
    if (-not (Test-KorusHostApiHealth)) { return $false }
    try {
        $code = curl.exe -sS -m 8 -o NUL -w "%{http_code}" "http://127.0.0.1:18080/api/v1/health/ready" 2>$null
        return ($code -match '^2')
    } catch { return $false }
}

function Test-KorusHostUiReady {
    try {
        $code = curl.exe -sS -m 8 -o NUL -w "%{http_code}" "http://127.0.0.1:19088/" 2>$null
        return ($code -match '^2')
    } catch { return $false }
}

function Get-KorusHostHealthSummary {
    @{
        ApiHealth = (Test-KorusHostApiHealth)
        ApiReady  = (Test-KorusHostApiReady)
        Web       = (Test-KorusHostUiReady)
    }
}
