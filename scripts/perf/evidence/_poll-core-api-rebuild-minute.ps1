$ErrorActionPreference = "Continue"
$Root = "D:\proj\korus_messenger"
Set-Location $Root
$LogPath = Join-Path $Root "scripts\perf\evidence\2026-06-25_core-api-rebuild-minute.log"
$EvidenceDir = Split-Path $LogPath -Parent
if (-not (Test-Path $EvidenceDir)) { New-Item -ItemType Directory -Path $EvidenceDir -Force | Out-Null }
. (Join-Path $Root "deploy\qemu\lib\Update-KorusGuestRepo.ps1")
. (Join-Path $Root "deploy\qemu\lib\Get-KorusGuestRemoteJobStatus.ps1")
. (Join-Path $Root "scripts\perf\lib\Invoke-QemuServerGuest.ps1")
$RunDir = Join-Path $Root "deploy\qemu\run"
$Plink = "${env:ProgramFiles}\PuTTY\plink.exe"
$JobName = "core-api-rebuild"
$hk = Get-KorusEd25519HostKey -SerialPath (Join-Path $RunDir "server-serial.log") -Role "server" -SshPort 12221
if (-not $hk) { throw "Server SSH host key not ready" }
function Get-HostHealth { try { $r = Invoke-WebRequest -Uri "http://127.0.0.1:18080/api/v1/health" -TimeoutSec 8 -UseBasicParsing; return "$($r.StatusCode)" } catch { if ($_.Exception.Response) { return "$([int]$_.Exception.Response.StatusCode)" }; return "down" } }
function Get-Guest8080 { try { $out = Invoke-QemuServerGuest -Script "curl -sS -m 6 -o /dev/null -w '%{http_code}' http://127.0.0.1:8080/api/v1/health"; $c = ($out -split "`n" | Select-Object -Last 1).Trim(); if (-not $c) { return "empty" }; return $c } catch { return "ssh_err" } }
function Get-ContainerLine { try { $out = Invoke-QemuServerGuest -Script "docker ps --filter name=core-api --format '{{.Status}}' | head -1"; $c = ($out -split "`n" | Where-Object { $_.Trim() } | Select-Object -Last 1).Trim(); if (-not $c) { return "none" }; return $c } catch { return "ssh_err" } }
function Write-MinuteLine { param($st); $ts = Get-Date -Format "yyyy-MM-dd HH:mm:ss"; if ($st.Running) { $jobPart = "job=RUNNING" } else { $jobPart = "job=DONE(exit=$($st.ExitCode))" }; $container = Get-ContainerLine; $h18080 = Get-HostHealth; $g8080 = Get-Guest8080; $tail = ($st.LogTail -replace "[\r\n]+", " | " -replace '\s+', ' ' ).Trim(); if ($tail.Length -gt 500) { $tail = $tail.Substring(0, 497) + "..." }; $line = "$ts | $jobPart | container=$container | host18080=$h18080 | guest8080=$g8080 | log_tail=$tail"; Add-Content -Path $LogPath -Value $line -Encoding UTF8; Write-Host $line; return [PSCustomObject]@{ Running = $st.Running; ExitCode = $st.ExitCode; Host18080 = $h18080; Guest8080 = $g8080 } }
$deadline = (Get-Date).AddMinutes(45); $iter = 0; $st = $null; $lastHost = "unknown"
while ($true) { $iter++; $st = Get-KorusGuestRemoteJobStatus -Plink $Plink -HostKey $hk -Port 12221 -JobName $JobName; $info = Write-MinuteLine -st $st; $lastHost = $info.Host18080; if (-not $st.Running) { break }; if ((Get-Date) -ge $deadline) { Add-Content -Path $LogPath -Value "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') | TIMEOUT_45min | last_host=$lastHost" -Encoding UTF8; break }; Start-Sleep -Seconds 60 }
$stateFile = Join-Path $EvidenceDir "2026-06-25_core-api-rebuild-poll-state.json"
@{ finishedAt = (Get-Date).ToString("o"); jobRunning = $st.Running; exitCode = $st.ExitCode; host18080 = $lastHost; iterations = $iter } | ConvertTo-Json | Set-Content $stateFile -Encoding UTF8
