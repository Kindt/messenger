# Smoke: verify deep-archive chunking end-to-end for a large message.
# Flow:
#  1) login and pick/create chat context
#  2) send large message with archive_ttl_seconds
#  3) poll MinIO S3 API for messages/{id}/manifest.json and part-*.json
#  4) validate manifest + chunk sha256 + assembled sha256
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$User = "csadmin",
    [string]$Pass = "csadmin",
    [string]$ChatId = "",
    [string]$MinioEndpoint = "http://localhost:9000",
    [string]$MinioAccessKey = "avandocmsg",
    [string]$MinioSecretKey = "avandocmsg123",
    [string]$MinioBucket = "avandocmsg",
    [switch]$UseSshMinioTunnel,
    [int]$ServerSshPort = 12221,
    [int]$LocalMinioPort = 19000,
    [int]$RemoteMinioPort = 9000,
    [string]$SshHostKey = "",
    [string]$SshPassword = "korus",
    [int]$PayloadBytes = 12288,
    [int]$ArchiveTtlSeconds = 1,
    [int]$WaitSeconds = 180,
    [int]$PollIntervalSec = 3
)
$ErrorActionPreference = "Stop"

function To-HexLower {
    param([byte[]]$Bytes)
    return ([BitConverter]::ToString($Bytes).Replace("-", "").ToLowerInvariant())
}

function Get-Sha256Hex {
    param([byte[]]$Bytes)
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        return To-HexLower -Bytes ($sha.ComputeHash($Bytes))
    } finally {
        $sha.Dispose()
    }
}

function Hmac-Sha256 {
    param(
        [byte[]]$Key,
        [string]$Data
    )
    $h = [System.Security.Cryptography.HMACSHA256]::new($Key)
    try {
        return $h.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($Data))
    } finally {
        $h.Dispose()
    }
}

function UriEncode-Rfc3986 {
    param([string]$Value)
    if ($null -eq $Value) { return "" }
    $e = [Uri]::EscapeDataString($Value)
    $e = $e.Replace("%7E", "~")
    return $e
}

function Get-S3SigningKey {
    param(
        [string]$SecretKey,
        [string]$DateStamp,
        [string]$Region,
        [string]$Service
    )
    $kDate = Hmac-Sha256 -Key ([System.Text.Encoding]::UTF8.GetBytes("AWS4$SecretKey")) -Data $DateStamp
    $kRegion = Hmac-Sha256 -Key $kDate -Data $Region
    $kService = Hmac-Sha256 -Key $kRegion -Data $Service
    return Hmac-Sha256 -Key $kService -Data "aws4_request"
}

function Get-S3AuthHeaders {
    param(
        [string]$Method,
        [string]$CanonicalUri,
        [string]$CanonicalQueryString,
        [string]$HostHeader,
        [string]$AccessKey,
        [string]$SecretKey,
        [string]$Region = "us-east-1",
        [string]$Service = "s3"
    )
    $now = [DateTime]::UtcNow
    $amzDate = $now.ToString("yyyyMMddTHHmmssZ")
    $dateStamp = $now.ToString("yyyyMMdd")
    $payloadHash = Get-Sha256Hex -Bytes ([byte[]]::new(0))
    $canonicalHeaders = "host:$HostHeader`n" + "x-amz-content-sha256:$payloadHash`n" + "x-amz-date:$amzDate`n"
    $signedHeaders = "host;x-amz-content-sha256;x-amz-date"
    $canonicalRequest = @(
        $Method
        $CanonicalUri
        $CanonicalQueryString
        $canonicalHeaders
        $signedHeaders
        $payloadHash
    ) -join "`n"
    $scope = "$dateStamp/$Region/$Service/aws4_request"
    $stringToSign = @(
        "AWS4-HMAC-SHA256"
        $amzDate
        $scope
        (Get-Sha256Hex -Bytes ([System.Text.Encoding]::UTF8.GetBytes($canonicalRequest)))
    ) -join "`n"
    $signingKey = Get-S3SigningKey -SecretKey $SecretKey -DateStamp $dateStamp -Region $Region -Service $Service
    $signature = To-HexLower -Bytes (Hmac-Sha256 -Key $signingKey -Data $stringToSign)
    $auth = "AWS4-HMAC-SHA256 Credential=$AccessKey/$scope, SignedHeaders=$signedHeaders, Signature=$signature"
    return @{
        Authorization = $auth
        "x-amz-date" = $amzDate
        "x-amz-content-sha256" = $payloadHash
    }
}

function Invoke-S3GetBytes {
    param(
        [Uri]$EndpointUri,
        [string]$CanonicalUri,
        [string]$CanonicalQueryString
    )
    $url = "$($EndpointUri.Scheme)://$($EndpointUri.Authority)$CanonicalUri"
    if ($CanonicalQueryString) {
        $url += "?$CanonicalQueryString"
    }
    $headers = Get-S3AuthHeaders -Method "GET" -CanonicalUri $CanonicalUri -CanonicalQueryString $CanonicalQueryString `
        -HostHeader $EndpointUri.Authority -AccessKey $MinioAccessKey -SecretKey $MinioSecretKey
    $resp = Invoke-WebRequest -Uri $url -Method Get -Headers $headers -UseBasicParsing
    $ms = [System.IO.MemoryStream]::new()
    try {
        $resp.RawContentStream.CopyTo($ms)
        return $ms.ToArray()
    } finally {
        $ms.Dispose()
    }
}

function Get-S3ObjectBytes {
    param(
        [Uri]$EndpointUri,
        [string]$Bucket,
        [string]$ObjectKey
    )
    $segments = @($ObjectKey -split "/") | ForEach-Object { UriEncode-Rfc3986 -Value $_ }
    $encodedKey = ($segments -join "/")
    $canonicalUri = "/" + (UriEncode-Rfc3986 -Value $Bucket) + "/" + $encodedKey
    return Invoke-S3GetBytes -EndpointUri $EndpointUri -CanonicalUri $canonicalUri -CanonicalQueryString ""
}

function Get-S3ListKeysByPrefix {
    param(
        [Uri]$EndpointUri,
        [string]$Bucket,
        [string]$Prefix
    )
    $query = "list-type=2&prefix=$(UriEncode-Rfc3986 -Value $Prefix)"
    $canonicalUri = "/" + (UriEncode-Rfc3986 -Value $Bucket)
    $bytes = Invoke-S3GetBytes -EndpointUri $EndpointUri -CanonicalUri $canonicalUri -CanonicalQueryString $query
    $xmlText = [System.Text.Encoding]::UTF8.GetString($bytes)
    [xml]$doc = $xmlText
    $keys = @()
    foreach ($node in @($doc.ListBucketResult.Contents)) {
        if ($node -and $node.Key) {
            $keys += [string]$node.Key
        }
    }
    return $keys
}

function Get-Token {
    param([string]$LoginBaseUrl, [string]$LoginUser, [string]$LoginPass)
    $login = Invoke-RestMethod -Uri "$LoginBaseUrl/api/v1/auth/login" -Method Post `
        -Body (@{ username = $LoginUser; password = $LoginPass } | ConvertTo-Json) `
        -ContentType "application/json; charset=utf-8"
    $t = $login.access_token
    if (-not $t) { $t = $login.accessToken }
    if (-not $t) { throw "No access token for $LoginUser" }
    return $t
}

function Resolve-ServerHostKey {
    param([string]$ExplicitHostKey)
    if ($ExplicitHostKey) { return $ExplicitHostKey }
    $scriptDir = $PSScriptRoot
    $repoRoot = Split-Path -Parent $scriptDir
    $serverSerial = Join-Path (Join-Path $repoRoot "deploy\qemu\run") "server-serial.log"
    $lib = Join-Path $repoRoot "deploy\qemu\lib\Update-KorusGuestRepo.ps1"
    if (Test-Path $lib) {
        . $lib
        $hk = Get-KorusEd25519HostKey -SerialPath $serverSerial -Role server -SshPort 12221
        if ($hk) { return $hk }
    }
    if (-not (Test-Path $serverSerial)) {
        throw "server serial log not found: $serverSerial (pass -SshHostKey to skip auto-detect)"
    }
    $m = Select-String -Path $serverSerial -Pattern "256 SHA256:([A-Za-z0-9+/=]+)\s+root@.*\(ED25519\)" | Select-Object -Last 1
    if (-not $m) {
        throw "Could not extract server ED25519 host key fingerprint from serial log. Pass -SshHostKey."
    }
    return "ssh-ed25519 255 SHA256:$($m.Matches[0].Groups[1].Value)"
}

function Start-MinioSshTunnel {
    param(
        [int]$SshPort,
        [int]$LocalPort,
        [int]$TargetPort,
        [string]$HostKey,
        [string]$Password
    )
    $plink = Join-Path $env:ProgramFiles "PuTTY\plink.exe"
    if (-not (Test-Path $plink)) {
        throw "plink not found: $plink"
    }
    $argLine = "-batch -N -hostkey `"$HostKey`" -pw $Password -P $SshPort -L ${LocalPort}:127.0.0.1:${TargetPort} korus@127.0.0.1"
    $proc = Start-Process -FilePath $plink -ArgumentList $argLine -PassThru -WindowStyle Hidden
    Start-Sleep -Seconds 2
    if ($proc.HasExited) {
        throw "SSH tunnel process exited early (code=$($proc.ExitCode))"
    }
    return $proc
}

if ($PayloadBytes -lt 4096) {
    throw "PayloadBytes must be >= 4096 for chunk smoke (got $PayloadBytes)"
}

if ($BaseUrl -match ':18080' -and -not $UseSshMinioTunnel) {
    $UseSshMinioTunnel = $true
    Write-Host "QEMU lab: enabling MinIO SSH tunnel via :$LocalMinioPort" -ForegroundColor DarkGray
}

$tunnelProc = $null
try {
    if ($UseSshMinioTunnel) {
        $resolvedHostKey = Resolve-ServerHostKey -ExplicitHostKey $SshHostKey
        Write-Host "Starting SSH tunnel for MinIO: localhost:$LocalMinioPort -> server:$RemoteMinioPort ..." -ForegroundColor Cyan
        $tunnelProc = Start-MinioSshTunnel -SshPort $ServerSshPort -LocalPort $LocalMinioPort -TargetPort $RemoteMinioPort `
            -HostKey $resolvedHostKey -Password $SshPassword
        $MinioEndpoint = "http://127.0.0.1:$LocalMinioPort"
    }

    $token = Get-Token -LoginBaseUrl $BaseUrl -LoginUser $User -LoginPass $Pass
    $headers = @{ Authorization = "Bearer $token" }

    if (-not $ChatId) {
        $chats = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats" -Headers $headers -Method Get
        if (-not $chats -or @($chats).Count -eq 0) {
            throw "No chats available; pass -ChatId"
        }
        $first = @($chats)[0]
        $ChatId = $first.id
        if (-not $ChatId) { $ChatId = $first.chat_id }
        if (-not $ChatId) { throw "Failed to resolve ChatId from /chats response" }
        Write-Host "Using chat $ChatId" -ForegroundColor DarkGray
    }

    $content = ("x" * $PayloadBytes) + " chunk-smoke " + (Get-Date -Format o)
    $body = @{
        type = "text"
        content = $content
        archive_ttl_seconds = $ArchiveTtlSeconds
    } | ConvertTo-Json

    Write-Host "POST $BaseUrl/api/v1/chats/$ChatId/messages (payload bytes ~ $PayloadBytes) ..." -ForegroundColor Cyan
    $msg = Invoke-RestMethod -Uri "$BaseUrl/api/v1/chats/$ChatId/messages" -Method Post -Headers $headers `
        -ContentType "application/json; charset=utf-8" -Body $body
    $messageId = $msg.id
    if (-not $messageId) { $messageId = $msg.message_id }
    if (-not $messageId) { throw "No message id returned from POST /messages" }
    Write-Host "[OK] message_id=$messageId" -ForegroundColor Green

    $endpointUri = [Uri]$MinioEndpoint
    $prefix = "messages/$messageId/"
    $manifestKey = "${prefix}manifest.json"
    $partRegex = "^messages/$([Regex]::Escape($messageId))/part-\d+\.json$"

    $deadline = (Get-Date).AddSeconds($WaitSeconds)
    $keys = @()
    while ((Get-Date) -lt $deadline) {
        try {
            $keys = @(Get-S3ListKeysByPrefix -EndpointUri $endpointUri -Bucket $MinioBucket -Prefix $prefix)
        } catch {
            Write-Host "[WARN] MinIO list failed: $($_.Exception.Message)" -ForegroundColor Yellow
            $keys = @()
        }
        $parts = @($keys | Where-Object { $_ -match $partRegex })
        if ($keys -contains $manifestKey -and $parts.Count -gt 0) {
            break
        }
        Start-Sleep -Seconds $PollIntervalSec
    }

    $parts = @($keys | Where-Object { $_ -match $partRegex } | Sort-Object)
    if (-not ($keys -contains $manifestKey)) {
        throw "Timed out waiting for $manifestKey in MinIO bucket '$MinioBucket'"
    }
    if ($parts.Count -eq 0) {
        throw "Timed out waiting for chunk parts under prefix '$prefix'"
    }
    Write-Host "[OK] MinIO objects present: manifest + $($parts.Count) part(s)" -ForegroundColor Green

    $manifestBytes = Get-S3ObjectBytes -EndpointUri $endpointUri -Bucket $MinioBucket -ObjectKey $manifestKey
    $manifestText = [System.Text.Encoding]::UTF8.GetString($manifestBytes)
    $manifest = $manifestText | ConvertFrom-Json
    if (-not $manifest -or -not $manifest.chunks) {
        throw "manifest.json has no 'chunks' field"
    }
    if ([string]$manifest.message_id -ne [string]$messageId) {
        throw "manifest.message_id mismatch: expected=$messageId actual=$($manifest.message_id)"
    }
    if ([int]$manifest.chunk_count -ne @($manifest.chunks).Count) {
        throw "manifest.chunk_count mismatch: chunk_count=$($manifest.chunk_count) actual=$(@($manifest.chunks).Count)"
    }

    $assembled = [System.IO.MemoryStream]::new()
    try {
        foreach ($chunk in @($manifest.chunks)) {
            $name = [string]$chunk.part_name
            if (-not $name) { throw "manifest chunk has empty part_name" }
            $objKey = "$prefix$name"
            $chunkBytes = Get-S3ObjectBytes -EndpointUri $endpointUri -Bucket $MinioBucket -ObjectKey $objKey
            $chunkSha = Get-Sha256Hex -Bytes $chunkBytes
            $expectedChunkSha = ([string]$chunk.sha256).ToLowerInvariant()
            if ($expectedChunkSha -and $chunkSha -ne $expectedChunkSha) {
                throw "chunk sha mismatch for $name expected=$expectedChunkSha actual=$chunkSha"
            }
            $assembled.Write($chunkBytes, 0, $chunkBytes.Length)
        }
        $assembledBytes = $assembled.ToArray()
    } finally {
        $assembled.Dispose()
    }

    $assembledSha = Get-Sha256Hex -Bytes $assembledBytes
    $manifestSha = ([string]$manifest.sha256).ToLowerInvariant()
    if (-not $manifestSha) {
        throw "manifest.sha256 is empty"
    }
    if ($assembledSha -ne $manifestSha) {
        throw "manifest sha mismatch expected=$manifestSha actual=$assembledSha"
    }

    Write-Host "[OK] manifest/chunk integrity verified for message $messageId" -ForegroundColor Green
    Write-Host "  chunk_count=$($manifest.chunk_count) total_size_bytes=$($manifest.total_size_bytes)" -ForegroundColor DarkGray
    Write-Host "  sha256=$assembledSha" -ForegroundColor DarkGray
    exit 0
}
finally {
    if ($tunnelProc -and -not $tunnelProc.HasExited) {
        Stop-Process -Id $tunnelProc.Id -Force -ErrorAction SilentlyContinue
    }
}
