var builder = WebApplication.CreateBuilder(args);
var app = builder.Build();

app.MapGet("/health", () => Results.Text("ok"));

app.MapPost("/v1/plugin/handle", async (HttpRequest request) =>
{
    using var reader = new StreamReader(request.Body);
    var raw = await reader.ReadToEndAsync();
    var text = ExtractText(raw).Trim();
    var lower = text.ToLowerInvariant();
    var msg = "Echo VB/.NET sidecar (Linux demo). Try: ping, /echo text";
    if (lower == "ping")
    {
        msg = "pong (echo-vbnet48-demo)";
    }
    else if (text.StartsWith("/echo ", StringComparison.OrdinalIgnoreCase))
    {
        msg = text[6..].Trim();
    }
    return Results.Json(new
    {
        messages = new[] { new { text = msg, format = "markdown" } }
    });
});

app.Run("http://0.0.0.0:8086");

static string ExtractText(string raw)
{
    const string key = "\"text\"";
    var idx = raw.IndexOf(key, StringComparison.Ordinal);
    if (idx < 0) return string.Empty;
    var colon = raw.IndexOf(':', idx);
    var q1 = raw.IndexOf('"', colon + 1);
    var q2 = raw.IndexOf('"', q1 + 1);
    if (q1 < 0 || q2 < 0) return string.Empty;
    return raw[(q1 + 1)..q2];
}
