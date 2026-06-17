# Parallel file upload load (PS-4.1) — streaming upload path on core-api.
param(
    [string]$BaseUrl = "http://127.0.0.1:18080",
    [string]$User = "csadmin",
    [string]$Pass = "csadmin",
    [int]$Parallel = 5,
    [int]$UploadsPerWorker = 4,
    [int]$FileSizeKb = 512
)
$ErrorActionPreference = "Stop"

function Fail([string]$Message) {
    Write-Host "[FAIL] $Message" -ForegroundColor Red
    exit 1
}

$login = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" -Method Post `
    -Body (@{ username = $User; password = $Pass } | ConvertTo-Json) `
    -ContentType "application/json; charset=utf-8"
$token = $login.access_token
if (-not $token) { $token = $login.accessToken }
if (-not $token) { Fail "No access token" }

$bytes = New-Object byte[] ($FileSizeKb * 1024)
(New-Object System.Random).NextBytes($bytes)
$filename = "load-upload.bin"

Write-Host "Upload load: parallel=$Parallel x $UploadsPerWorker size=${FileSizeKb}KB url=$BaseUrl" -ForegroundColor Cyan
$ok = 0
$fail = 0
$sw = [System.Diagnostics.Stopwatch]::StartNew()

$jobs = 1..$Parallel | ForEach-Object {
    $worker = $_
    Start-Job -ScriptBlock {
        param($BaseUrl, $Token, $Bytes, $Name, $Count)
        $localOk = 0
        $localFail = 0
        for ($i = 0; $i -lt $Count; $i++) {
            try {
                $uri = "$BaseUrl/api/v1/files/upload"
                $req = [System.Net.HttpWebRequest]::Create($uri)
                $req.Method = "POST"
                $req.Headers.Add("Authorization", "Bearer $Token")
                $req.Headers.Add("X-Filename", "$Name-$i")
                $req.ContentType = "application/octet-stream"
                $req.ContentLength = $Bytes.Length
                $stream = $req.GetRequestStream()
                $stream.Write($Bytes, 0, $Bytes.Length)
                $stream.Close()
                $resp = $req.GetResponse()
                $resp.Close()
                $localOk++
            } catch {
                $localFail++
            }
        }
        return @{ ok = $localOk; fail = $localFail }
    } -ArgumentList $BaseUrl, $token, $bytes, $filename, $UploadsPerWorker
}

foreach ($job in $jobs) {
    $result = Receive-Job -Job $job -Wait
    Remove-Job -Job $job
    $ok += $result.ok
    $fail += $result.fail
}
$sw.Stop()

$total = $ok + $fail
$elapsed = [Math]::Max(1, $sw.Elapsed.TotalSeconds)
$mb = ($ok * $FileSizeKb) / 1024.0
Write-Host "[OK] uploads ok=$ok fail=$fail total=${total} elapsed=$([Math]::Round($elapsed,1))s (~$([Math]::Round($mb / $elapsed, 2)) MB/s)" -ForegroundColor Green
Write-Host "Live gate: core-api heap stable under parallel uploads - check container stats on guest" -ForegroundColor DarkGray
if ($fail -gt 0) {
    Write-Host "[WARN] $fail uploads failed" -ForegroundColor Yellow
    exit 1
}
