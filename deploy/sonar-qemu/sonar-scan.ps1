# Wait for SonarQube Community, bootstrap admin, run analysis.
# Default: compile on host, scan inside QEMU guest (avoids Windows CryptoPro CSP nags).
# Project-agnostic: reads sonar-project.properties from -RepoRoot (default: repo containing this lab).
param(
    [string]$RepoRoot,
    [string]$JdkHome = $env:JAVA_HOME,
    [string]$CompileCommand,
    [switch]$SkipCompile,
    [int]$TimeoutMinutes = 45,
    [switch]$SkipScan,
    [switch]$OnHost,
    [switch]$AllowCryptoPro,
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host @"
Usage: .\deploy\sonar-qemu\sonar-scan.ps1 [options]

  -RepoRoot <path>       Project root with sonar-project.properties (default: parent of deploy/)
  -JdkHome <path>        JDK for scripts/mvn-jdk25.ps1 if present
  -CompileCommand <cmd>  Custom host compile (cmd.exe); overrides auto Maven
  -SkipCompile           Do not compile (payload uses existing binaries)
  -TimeoutMinutes 45     Wait for Sonar UP
  -SkipScan              Only wait UP + bootstrap admin/token
  -OnHost                Windows SonarScanner (CryptoPro blocked unless -AllowCryptoPro)
  -AllowCryptoPro         Allow host JVM to use system CryptoPro providers (-OnHost only)

Default: Gradle compile on host + sonar-scanner-cli Docker inside guest (no CryptoPro on host).
"@
    exit 0
}

. (Join-Path $PSScriptRoot "config.ps1")
. (Join-Path $PSScriptRoot "lib\GuestSsh.ps1")
. (Join-Path $PSScriptRoot "lib\ProjectProps.ps1")
. (Join-Path $PSScriptRoot "lib\Set-SonarHostJvm.ps1")

$script:SonarQemuRepoRoot = Resolve-SonarLabRepoRoot -RepoRoot $RepoRoot
$props = Get-SonarPropertiesMap -RepoRoot $SonarQemuRepoRoot
$identity = Get-SonarProjectIdentity -Props $props
$projectKey = $identity.Key
$projectName = $identity.Name
Write-Host "Project: $projectName ($projectKey)" -ForegroundColor Cyan
Write-Host "RepoRoot: $SonarQemuRepoRoot" -ForegroundColor DarkGray

function Get-SonarStatus {
    try {
        $r = Invoke-RestMethod -Uri "$SonarQemuUrl/api/system/status" -TimeoutSec 5
        return $r.status
    } catch {
        return $null
    }
}

function Invoke-SonarApi {
    param(
        [string]$Method = "GET",
        [Parameter(Mandatory)][string]$Path,
        [hashtable]$Form = $null,
        [string]$User = $SonarQemuAdminUser,
        [string]$Password
    )
    $pair = "${User}:${Password}"
    $b64 = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes($pair))
    $headers = @{ Authorization = "Basic $b64" }
    $uri = "$SonarQemuUrl$Path"
    if ($Form) {
        return Invoke-RestMethod -Method $Method -Uri $uri -Headers $headers -Body $Form -ContentType "application/x-www-form-urlencoded"
    }
    return Invoke-RestMethod -Method $Method -Uri $uri -Headers $headers
}

function Resolve-SonarScannerBat {
    $dir = Join-Path $SonarQemuToolsDir "sonar-scanner-*-windows-x64"
    $bat = Get-ChildItem -Path $dir -Filter "sonar-scanner.bat" -Recurse -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($bat) { return $bat.FullName }
    return $null
}

function New-ScanPayloadArchive {
    param([Parameter(Mandatory)][hashtable]$Props)
    $stage = Join-Path $SonarQemuRunDir "scan-payload"
    if (Test-Path $stage) { Remove-Item -Recurse -Force $stage }
    New-Item -ItemType Directory -Force -Path $stage | Out-Null

    $rels = Get-SonarPayloadRelativePaths -RepoRoot $SonarQemuRepoRoot -Props $Props
    foreach ($rel in $rels) {
        $src = Join-Path $SonarQemuRepoRoot $rel
        $dst = Join-Path $stage $rel
        if (-not (Test-Path $src)) {
            Write-Host "  skip missing: $rel" -ForegroundColor DarkYellow
            continue
        }
        if (Test-Path $src -PathType Leaf) {
            $parent = Split-Path $dst -Parent
            if ($parent) {
                New-Item -ItemType Directory -Force -Path $parent | Out-Null
            }
            Copy-Item $src $dst -Force
        } else {
            New-Item -ItemType Directory -Force -Path $dst | Out-Null
            Copy-Item (Join-Path $src "*") $dst -Recurse -Force
        }
    }

    $archive = Join-Path $SonarQemuRunDir "scan-payload.tgz"
    if (Test-Path $archive) { Remove-Item -Force $archive }
    Push-Location $stage
    try {
        tar -czf $archive *
        if ($LASTEXITCODE -ne 0) { throw "tar failed ($LASTEXITCODE)" }
    } finally {
        Pop-Location
    }
    if (-not (Test-Path $archive)) { throw "payload archive missing: $archive" }
    return $archive
}

function Invoke-GuestSonarScan {
    param(
        [Parameter(Mandatory)][string]$Token,
        [Parameter(Mandatory)][string]$ProjectKey,
        [Parameter(Mandatory)][string]$ProjectName,
        [Parameter(Mandatory)][hashtable]$Props
    )
    Write-Host "Packaging sources+binaries for guest..." -ForegroundColor Cyan
    $archive = New-ScanPayloadArchive -Props $Props
    $remoteArch = "/home/$SonarQemuGuestUser/scan-payload.tgz"
    $remoteDir = "/home/$SonarQemuGuestUser/sonar-scan"

    Write-Host "Uploading payload to guest (SSH $($SonarQemuSshHostPort))..." -ForegroundColor Cyan
    Send-SonarGuestFile -Local $archive -Remote $remoteArch

    $tokenEsc = $Token.Replace("'", "'\''")
    $nameEsc = $ProjectName.Replace("'", "'\''")
    $volumeMount = "${remoteDir}:/usr/src"
    $remoteScript = @"
set -euo pipefail
rm -rf '$remoteDir'
mkdir -p '$remoteDir'
tar -xzf '$remoteArch' -C '$remoteDir'
export DOCKER_CLI_HINTS=false
sudo docker pull $SonarQemuScannerImage >/tmp/scanner-pull.log 2>&1
sudo docker run --rm --network host \
  -e SONAR_HOST_URL=http://127.0.0.1:9000 \
  -e SONAR_TOKEN='$tokenEsc' \
  -v '$volumeMount' \
  $SonarQemuScannerImage \
  -Dsonar.projectKey=$ProjectKey \
  -Dsonar.projectName='$nameEsc' \
  -Dsonar.projectBaseDir=/usr/src
"@
    $scriptPath = Join-Path $SonarQemuRunDir "guest-scan.sh"
    $remoteGuestScript = "/home/$SonarQemuGuestUser/guest-scan.sh"
    try {
        [IO.File]::WriteAllText($scriptPath, ($remoteScript -replace "`r`n", "`n"))
        Send-SonarGuestFile -Local $scriptPath -Remote $remoteGuestScript
        Write-Host "Running SonarScanner inside guest Docker..." -ForegroundColor Cyan
        Invoke-SonarGuestCommand -Remote "bash $remoteGuestScript"
    } finally {
        # Token is embedded in the script — wipe host + guest copies after use.
        Remove-Item -Force -ErrorAction SilentlyContinue $scriptPath
        try { Invoke-SonarGuestCommand -Remote "rm -f $remoteGuestScript" } catch { }
    }
}

function Invoke-HostSonarScan {
    param(
        [Parameter(Mandatory)][string]$Token,
        [Parameter(Mandatory)][string]$ProjectKey,
        [Parameter(Mandatory)][string]$ProjectName,
        [switch]$AllowCryptoPro
    )
    $scanner = Resolve-SonarScannerBat
    if (-not $scanner) {
        throw "SonarScanner CLI not found under deploy\sonar-qemu\tools. Download sonar-scanner-*-windows-x64 first."
    }
    $blockCp = $SonarQemuDisableCryptoPro -and -not $AllowCryptoPro
    if ($blockCp) {
        Write-Host "Running SonarScanner CLI on host (CryptoPro disabled via java.security overlay)..." -ForegroundColor Cyan
    } else {
        Write-Host "Running SonarScanner CLI on host (CryptoPro allowed)..." -ForegroundColor Yellow
    }
    Push-Location $SonarQemuRepoRoot
    try {
        Use-SonarNoCryptoProJvm -AllowCryptoPro:(-not $blockCp)
        $prevEap = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        & $scanner `
            "-Dsonar.host.url=$SonarQemuUrl" `
            "-Dsonar.token=$Token" `
            "-Dsonar.projectKey=$ProjectKey" `
            "-Dsonar.projectName=$ProjectName" 2>&1 | Out-Host
        $scanExit = $LASTEXITCODE
        $ErrorActionPreference = $prevEap
        if ($scanExit -ne 0) { throw "SonarScanner failed ($scanExit)" }
    } finally {
        Restore-SonarNoCryptoProJvm
        Pop-Location
    }
}

function Ensure-GuestSonarStack {
    # After VM reboot containers stay Exited until compose up -d.
    Write-Host "Ensuring guest Docker stack is up..." -ForegroundColor DarkGray
    $code = Invoke-SonarGuest -Action run -Args @(
        "cd /opt/sonar && sudo DOCKER_CLI_HINTS=false docker compose up -d >/tmp/compose-up.log 2>&1; sudo docker ps --format '{{.Names}} {{.Status}}'"
    )
    if ($code -ne 0) {
        Write-Host "  guest compose up returned $code (SSH/docker may still be starting)" -ForegroundColor DarkYellow
    }
}

Write-Host "Waiting for SonarQube at $SonarQemuUrl (up to $TimeoutMinutes min)..." -ForegroundColor Cyan
$deadline = (Get-Date).AddMinutes($TimeoutMinutes)
$status = $null
$stackNudged = $false
while ((Get-Date) -lt $deadline) {
    $status = Get-SonarStatus
    if ($status -eq "UP") { break }
    if (-not $OnHost -and -not $stackNudged -and -not $status) {
        try {
            Ensure-GuestSonarStack
            $stackNudged = $true
        } catch {
            Write-Host "  guest SSH not ready yet: $($_.Exception.Message)" -ForegroundColor DarkYellow
        }
    }
    Write-Host "  status=$status ..." -ForegroundColor DarkGray
    Start-Sleep -Seconds 15
}
if ($status -ne "UP") {
    throw "SonarQube not UP after $TimeoutMinutes min. Check deploy\sonar-qemu\run\sonar-serial.log"
}
Write-Host "[OK] SonarQube UP" -ForegroundColor Green

$password = $SonarQemuAdminPassword
try {
    Invoke-SonarApi -Method POST -Path "/api/users/change_password" -Form @{
        login            = "admin"
        previousPassword = "admin"
        password         = $password
    } -Password "admin" | Out-Null
    Write-Host "Changed default admin password" -ForegroundColor DarkGray
} catch {
    try {
        Invoke-SonarApi -Path "/api/authentication/validate" -Password $password | Out-Null
    } catch {
        throw "Cannot authenticate as admin. Reset VM disk (qemu-up without -KeepDisk) or set password manually."
    }
    Write-Host "Using existing admin password" -ForegroundColor DarkGray
}

$slug = ($projectKey -replace '[^A-Za-z0-9._-]', '_')
$tokenName = "scan-$slug-$(Get-Date -Format 'yyyyMMddHHmmss')"
$tokenResp = Invoke-SonarApi -Method POST -Path "/api/user_tokens/generate" -Form @{
    name = $tokenName
} -Password $password
$token = $tokenResp.token
if (-not $token) { throw "Failed to generate Sonar token" }

try {
    Invoke-SonarApi -Method POST -Path "/api/projects/create" -Form @{
        name       = $projectName
        project    = $projectKey
        visibility = "private"
    } -Password $password | Out-Null
} catch {
    # already exists
}

Write-Host "UI: $SonarQemuUrl  (login admin / <password from config.ps1>)" -ForegroundColor Green

if ($SkipScan) {
    Write-Host "SkipScan set; token generated"
    exit 0
}

if (-not $SkipCompile) {
    Invoke-SonarLabCompile -RepoRoot $SonarQemuRepoRoot -JdkHome $JdkHome -CompileCommand $CompileCommand
} else {
    Write-Host "SkipCompile: using existing binaries" -ForegroundColor DarkGray
}

if ($OnHost) {
    Invoke-HostSonarScan -Token $token -ProjectKey $projectKey -ProjectName $projectName -AllowCryptoPro:$AllowCryptoPro
} else {
    Invoke-GuestSonarScan -Token $token -ProjectKey $projectKey -ProjectName $projectName -Props $props
}

Write-Host ""
Write-Host "[OK] Scan finished. Open: $SonarQemuUrl/dashboard?id=$([uri]::EscapeDataString($projectKey))" -ForegroundColor Green
if (-not $OnHost) {
    Write-Host "Scanner ran inside guest (host CryptoPro CSP is not used)." -ForegroundColor DarkGray
} elseif ($SonarQemuDisableCryptoPro -and -not $AllowCryptoPro) {
    Write-Host "Host scanner used JDK-only java.security (CryptoPro blocked)." -ForegroundColor DarkGray
}
