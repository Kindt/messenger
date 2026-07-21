# Resolve Prometheus scrape URLs when VPP runs against QEMU host-forwards (:18080 API).
function Resolve-QemuLabWorkerMetrics {
    param(
        [Parameter(Mandatory)][string]$ApiBaseUrl,
        [string]$WorkerMetricsUrl = "",
        [string]$RetentionMetricsUrl = "",
        [string]$CoreMetricsUrl = ""
    )

    $base = $ApiBaseUrl.TrimEnd('/')
    if ($ApiBaseUrl -notmatch ':18080') {
        return @{
            CoreMetricsUrl = if ($CoreMetricsUrl) { $CoreMetricsUrl } else { "$base/api/v1/metrics/prometheus" }
            WorkerMetricsUrl = if ($WorkerMetricsUrl) { $WorkerMetricsUrl } else { "http://127.0.0.1:9193/metrics" }
            RetentionMetricsUrl = if ($RetentionMetricsUrl) { $RetentionMetricsUrl } else { "http://127.0.0.1:9192/metrics" }
        }
    }

    . (Join-Path $PSScriptRoot "Ensure-GuestWorkerMetricsTunnels.ps1")
    Ensure-GuestWorkerMetricsTunnels | Out-Null

    $core = if ($CoreMetricsUrl -and $CoreMetricsUrl -notmatch ':8080(/|$)') {
        $CoreMetricsUrl
    } else {
        "$base/api/v1/metrics/prometheus"
    }
    $worker = if ($WorkerMetricsUrl -and $WorkerMetricsUrl -notmatch '(127\.0\.0\.1|localhost):9193') {
        $WorkerMetricsUrl
    } else {
        "http://127.0.0.1:19193/metrics"
    }
    $retention = if ($RetentionMetricsUrl -and $RetentionMetricsUrl -notmatch '(127\.0\.0\.1|localhost):9192') {
        $RetentionMetricsUrl
    } else {
        "http://127.0.0.1:19192/metrics"
    }
    return @{
        CoreMetricsUrl = $core
        WorkerMetricsUrl = $worker
        RetentionMetricsUrl = $retention
    }
}
