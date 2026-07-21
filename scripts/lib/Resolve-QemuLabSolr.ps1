# Resolve Solr base URL when VPP runs against QEMU host-forwards (:18080 API).
function Resolve-QemuLabSolr {
    param(
        [Parameter(Mandatory)][string]$ApiBaseUrl,
        [string]$SolrUrl = "",
        [string]$Collection = "messages_meta"
    )

    if ($SolrUrl -and $SolrUrl -notmatch '(127\.0\.0\.1|localhost):8983') {
        return $SolrUrl.TrimEnd('/')
    }

    if ($ApiBaseUrl -notmatch ':18080') {
        $base = if ($SolrUrl) { $SolrUrl.TrimEnd('/') } else { "http://127.0.0.1:8983" }
        if ($base -notmatch '/solr/') { $base = "$base/solr/$Collection" }
        return $base
    }

    . (Join-Path $PSScriptRoot "Ensure-GuestSolrTunnel.ps1")
    $hostBase = Ensure-GuestSolrTunnel
    return "$hostBase/solr/$Collection"
}
