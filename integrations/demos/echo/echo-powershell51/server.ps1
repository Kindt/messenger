param(
    [string]$BaseUrl = "http://127.0.0.1:8090",
    [string]$ListenPort = "8087"
)

$ErrorActionPreference = "Stop"
$prefix = "/v1/plugin/handle"

$listener = [System.Net.HttpListener]::new()
$listener.Prefixes.Add("http://+:$ListenPort/")
$listener.Start()
Write-Host "echo-powershell listening on :$ListenPort"

while ($listener.IsListening) {
    $ctx = $listener.GetContext()
    $path = $ctx.Request.Url.AbsolutePath
    if ($path -eq "/health") {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes("ok")
        $ctx.Response.ContentType = "text/plain"
        $ctx.Response.OutputStream.Write($bytes, 0, $bytes.Length)
        $ctx.Response.Close()
        continue
    }
    if ($path -ne $prefix -or $ctx.Request.HttpMethod -ne "POST") {
        $ctx.Response.StatusCode = 404
        $ctx.Response.Close()
        continue
    }
    $reader = New-Object System.IO.StreamReader($ctx.Request.InputStream)
    $raw = $reader.ReadToEnd()
    $event = $raw | ConvertFrom-Json
    $text = [string]$event.text
    $type = [string]$event.type
    $msg = "Echo PowerShell sidecar. Try: ping, /echo text"
    if ($text -eq "ping") { $msg = "pong (echo-powershell)" }
    elseif ($type -eq "slash" -and $text.StartsWith("/echo ")) { $msg = $text.Substring(6) }
    $json = @{ messages = @(@{ text = $msg; format = "markdown" }) } | ConvertTo-Json -Compress
    $out = [System.Text.Encoding]::UTF8.GetBytes($json)
    $ctx.Response.ContentType = "application/json"
    $ctx.Response.OutputStream.Write($out, 0, $out.Length)
    $ctx.Response.Close()
}
