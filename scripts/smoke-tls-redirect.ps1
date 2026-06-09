# TLS redirect and certificate smoke (stage/prod nginx role tls).
# Dev/QEMU HTTP-only: pass -SkipTls to exit 0 without checks.
param(
    [string]$HttpUrl = "http://localhost",
    [string]$HttpsUrl = "https://localhost",
    [string]$ExpectedCertSubject = "",
    [switch]$SkipTls,
    [switch]$Help
)
$ErrorActionPreference = "Stop"

if ($Help) {
    Write-Host @"
Usage: .\scripts\smoke-tls-redirect.ps1 [-HttpUrl <url>] [-HttpsUrl <url>] [-ExpectedCertSubject <CN>] [-SkipTls] [-Help]

  -SkipTls     Skip all TLS checks (dev/QEMU HTTP-only path).
  -HttpUrl     HTTP entry point (default http://localhost). Expect 301/302 to HTTPS.
  -HttpsUrl    HTTPS entry point (default https://localhost). Expect 200 on /health.
  -ExpectedCertSubject  Optional substring in cert Subject (e.g. messenger.stage.example.com).

Stage example:
  .\scripts\smoke-tls-redirect.ps1 `
    -HttpUrl http://messenger.stage.example.com `
    -HttpsUrl https://messenger.stage.example.com `
    -ExpectedCertSubject messenger.stage.example.com
"@
    exit 0
}

function Fail([string]$msg) {
    Write-Host "[FAIL] $msg" -ForegroundColor Red
    exit 1
}

if ($SkipTls) {
    Write-Host "[SKIP] TLS smoke skipped (-SkipTls; HTTP-only dev/QEMU)" -ForegroundColor Yellow
    exit 0
}

Write-Host "HEAD $HttpUrl (expect redirect to HTTPS) ..." -ForegroundColor Cyan
try {
    $redirect = Invoke-WebRequest -Uri $HttpUrl -Method Head -MaximumRedirection 0 -UseBasicParsing
    Fail "Expected redirect from HTTP, got status $($redirect.StatusCode)"
} catch {
    $resp = $_.Exception.Response
    if (-not $resp) { Fail "HTTP redirect check: $_" }
    $code = [int]$resp.StatusCode
    if ($code -notin 301, 302, 307, 308) {
        Fail "Expected 301/302/307/308 from HTTP, got $code"
    }
    $location = $resp.Headers["Location"]
    if (-not $location) { Fail "Redirect missing Location header" }
    if ($location -notmatch '^https://') {
        Fail "Location should be HTTPS, got: $location"
    }
    Write-Host "  redirect -> $location" -ForegroundColor DarkGray
}

Write-Host "GET $HttpsUrl/health (TLS) ..." -ForegroundColor Cyan
try {
    $req = [System.Net.HttpWebRequest]::Create("$HttpsUrl/health")
    $req.Method = "GET"
    $req.AllowAutoRedirect = $true
    $resp = $req.GetResponse()
    try {
        if ([int]$resp.StatusCode -ne 200) {
            Fail "HTTPS health status $([int]$resp.StatusCode)"
        }
        $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
        $body = $reader.ReadToEnd().Trim()
        $reader.Close()
        if ($body -ne "ok") {
            Fail "HTTPS health body expected 'ok', got: $body"
        }
    } finally {
        $resp.Close()
    }
} catch {
    Fail "HTTPS health: $_"
}

if ($ExpectedCertSubject) {
    Write-Host "Checking cert Subject contains '$ExpectedCertSubject' ..." -ForegroundColor Cyan
    try {
        $uri = [Uri]$HttpsUrl
        $hostName = $uri.Host
        $port = if ($uri.Port -gt 0) { $uri.Port } else { 443 }
        $tcp = New-Object System.Net.Sockets.TcpClient($hostName, $port)
        try {
            $ssl = New-Object System.Net.Security.SslStream($tcp.GetStream(), $false, ({ $true }))
            $ssl.AuthenticateAsClient($hostName)
            $cert = New-Object System.Security.Cryptography.X509Certificates.X509Certificate2($ssl.RemoteCertificate)
            $subject = $cert.Subject
            if ($subject -notmatch [regex]::Escape($ExpectedCertSubject)) {
                Fail "Cert Subject '$subject' missing '$ExpectedCertSubject'"
            }
            Write-Host "  Subject: $subject" -ForegroundColor DarkGray
        } finally {
            if ($ssl) { $ssl.Dispose() }
            $tcp.Close()
        }
    } catch {
        Fail "Cert subject check: $_"
    }
}

Write-Host "[OK] TLS redirect and HTTPS health ($HttpsUrl)" -ForegroundColor Green
