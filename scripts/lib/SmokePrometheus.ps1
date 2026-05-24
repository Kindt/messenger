# Dot-source: parse simple Prometheus text exposition counters.
using namespace System.Globalization

function Try-ParsePromValue {
    param([string]$Value)
    $parsed = 0.0
    if ([double]::TryParse($Value, [NumberStyles]::Float, [CultureInfo]::InvariantCulture, [ref]$parsed)) {
        return $parsed
    }
    return $null
}

function Get-PrometheusGauge {
    param(
        [string]$MetricsText,
        [string]$Name
    )
    if (-not $MetricsText) { return $null }
    foreach ($line in $MetricsText -split "`n") {
        if ($line.StartsWith("#") -or [string]::IsNullOrWhiteSpace($line)) { continue }
        if (-not $line.StartsWith($Name)) { continue }
        if ($line -match '\{') { continue }
        $parts = $line.Trim() -split '\s+'
        if ($parts.Length -ge 2) {
            $num = Try-ParsePromValue -Value $parts[-1]
            if ($null -ne $num) { return $num }
        }
    }
    return $null
}

function Get-PrometheusCounter {
    param(
        [string]$MetricsText,
        [string]$Name,
        [hashtable]$Labels = @{}
    )
    if (-not $MetricsText) { return $null }
    foreach ($line in $MetricsText -split "`n") {
        if ($line.StartsWith("#") -or [string]::IsNullOrWhiteSpace($line)) { continue }
        if (-not $line.StartsWith($Name)) { continue }
        if ($Labels.Count -gt 0) {
            $ok = $true
            foreach ($k in $Labels.Keys) {
                if ($line -notmatch "$k=`"$([regex]::Escape($Labels[$k]))`"") {
                    $ok = $false
                    break
                }
            }
            if (-not $ok) { continue }
        } elseif ($line -match '\{') {
            continue
        }
        $parts = $line.Trim() -split '\s+'
        if ($parts.Length -ge 2) {
            $num = Try-ParsePromValue -Value $parts[-1]
            if ($null -ne $num) { return $num }
        }
    }
    return $null
}

function Test-PrometheusMetricsPresent {
    param(
        [string]$MetricsText,
        [string[]]$RequiredNames
    )
    foreach ($n in $RequiredNames) {
        if ($MetricsText -notmatch "(?m)^$([regex]::Escape($n))(\{|\s)") {
            throw "Metric missing in exposition: $n"
        }
    }
}
