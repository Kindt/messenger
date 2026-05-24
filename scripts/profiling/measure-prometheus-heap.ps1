# Sample JVM heap metrics from Prometheus during/after load.
# Example: .\scripts\profiling\measure-prometheus-heap.ps1 -MetricsUrl http://127.0.0.1:18080/api/v1/metrics/prometheus -Label baseline
param(
    [string]$MetricsUrl = "http://localhost:8080/api/v1/metrics/prometheus",
    [string]$Label = "sample",
    [int]$Samples = 5,
    [int]$IntervalSec = 2
)
$ErrorActionPreference = "Stop"

function Get-MetricValue {
    param([string]$Body, [string]$Name)
    $m = [regex]::Match($Body, "(?m)^$([regex]::Escape($Name))\s+([0-9.eE+-]+)")
    if ($m.Success) { return [double]$m.Groups[1].Value }
    return $null
}

$rows = @()
for ($i = 0; $i -lt $Samples; $i++) {
    $body = (Invoke-WebRequest -Uri $MetricsUrl -UseBasicParsing).Content
    $used = Get-MetricValue -Body $body -Name "jvm_memory_bytes_used{area=`"heap`",}"
    if ($null -eq $used) {
        $used = Get-MetricValue -Body $body -Name "jvm_memory_used_bytes{area=`"heap`",}"
    }
    $max = Get-MetricValue -Body $body -Name "jvm_memory_bytes_max{area=`"heap`",}"
    if ($null -eq $max) {
        $max = Get-MetricValue -Body $body -Name "jvm_memory_max_bytes{area=`"heap`",}"
    }
    $rows += [pscustomobject]@{
        ts = (Get-Date).ToString("o")
        heap_used_mb = if ($used) { [math]::Round($used / 1MB, 2) } else { $null }
        heap_max_mb = if ($max) { [math]::Round($max / 1MB, 2) } else { $null }
    }
    if ($i -lt ($Samples - 1)) { Start-Sleep -Seconds $IntervalSec }
}

$avgUsed = ($rows | Where-Object { $null -ne $_.heap_used_mb } | Measure-Object -Property heap_used_mb -Average).Average
Write-Host "[$Label] avg heap used MB: $([math]::Round($avgUsed, 2)) (samples=$Samples)" -ForegroundColor Green
foreach ($row in $rows) {
    Write-Host ("  {0} heap={1}MB max={2}MB" -f $row.ts, $row.heap_used_mb, $row.heap_max_mb) -ForegroundColor DarkGray
}
return [double]$avgUsed
