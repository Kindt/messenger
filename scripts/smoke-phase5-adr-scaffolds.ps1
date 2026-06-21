# Smoke: spec 022 Phase 5 ADR scaffolds (stickers, kanban, sip, passkeys)
param(
    [string]$ApiBase = $(if ($env:KORUS_API_URL) { $env:KORUS_API_URL } else { "http://127.0.0.1:18080/api" })
)
$ErrorActionPreference = "Stop"

function Get-Token {
    $body = @{ username = "admin"; password = "admin" } | ConvertTo-Json
    $r = Invoke-RestMethod -Uri "$ApiBase/v1/auth/login" -Method Post -Body $body -ContentType "application/json"
    return $r.access_token
}

$token = Get-Token
$h = @{ Authorization = "Bearer $token" }

Write-Host "[1] sticker packs"
Invoke-RestMethod -Uri "$ApiBase/v1/stickers/packs" -Headers $h | Out-Null
$pack = Invoke-RestMethod -Uri "$ApiBase/v1/stickers/packs" -Method Post -Headers $h -Body (@{ name = "Lab pack" } | ConvertTo-Json) -ContentType "application/json"
if (-not $pack.pack_id) { throw "sticker pack create failed" }

Write-Host "[2] gif search"
Invoke-RestMethod -Uri "$ApiBase/v1/stickers/gifs?q=thumb" -Headers $h | Out-Null

Write-Host "[3] sip gateway"
Invoke-RestMethod -Uri "$ApiBase/v1/platform/sip" -Method Put -Headers $h -Body (@{ enabled = $true; gateway_uri = "sip:gw.lab.local"; h323_enabled = $false } | ConvertTo-Json) -ContentType "application/json" | Out-Null

Write-Host "[4] passkeys scaffold"
$cred = "cred-smoke-" + [guid]::NewGuid().ToString("N").Substring(0, 8)
Invoke-RestMethod -Uri "$ApiBase/v1/platform/passkeys" -Method Post -Headers $h -Body (@{ credential_id = $cred; public_key = "pk-scaffold" } | ConvertTo-Json) -ContentType "application/json" | Out-Null

Write-Host "[5] group chat for collaboration ADR"
$me = Invoke-RestMethod -Uri "$ApiBase/v1/users/me" -Headers $h
$meId = $me.id
if (-not $meId) { $meId = $me.user_id }
$chat = Invoke-RestMethod -Uri "$ApiBase/v1/chats" -Method Post -Headers $h -ContentType "application/json" `
  -Body (@{ type = "group"; title = "ADR depth"; member_ids = @($meId) } | ConvertTo-Json)
$chatId = $chat.id
if (-not $chatId) { throw "chat create failed" }

Write-Host "[6] kanban + whiteboard"
Invoke-RestMethod -Uri "$ApiBase/v1/chats/$chatId/kanban/tasks" -Method Post -Headers $h -ContentType "application/json" `
  -Body (@{ column_key = "todo"; title = "Smoke task" } | ConvertTo-Json) | Out-Null
Invoke-RestMethod -Uri "$ApiBase/v1/chats/$chatId/kanban/tasks" -Headers $h | Out-Null
Invoke-RestMethod -Uri "$ApiBase/v1/chats/$chatId/whiteboard" -Method Put -Headers $h -ContentType "application/json" `
  -Body (@{ title = "Lab"; snapshot_json = "{}" } | ConvertTo-Json) | Out-Null

Write-Host "[7] conference ADR guest link + redeem"
$conf = Invoke-RestMethod -Uri "$ApiBase/v1/chats/$chatId/conferences" -Method Post -Headers $h -ContentType "application/json" `
  -Body (@{ title = "ADR conf" } | ConvertTo-Json)
$confId = $conf.id
if (-not $confId) { $confId = $conf.conference_id }
if (-not $confId) { throw "conference create missing id" }
$guest = Invoke-RestMethod -Uri "$ApiBase/v1/chats/$chatId/conferences/$confId/guest-links" -Method Post -Headers $h -ContentType "application/json" `
  -Body (@{ waiting_room = $false } | ConvertTo-Json)
if (-not $guest.guest_token) { throw "guest link missing token" }
Invoke-RestMethod -Uri "$ApiBase/v1/conferences/guest/$($guest.guest_token)" -Method Get | Out-Null

Write-Host "[7b] kanban move"
$tasks = Invoke-RestMethod -Uri "$ApiBase/v1/chats/$chatId/kanban/tasks" -Headers $h
$taskId = $tasks[0].task_id
if (-not $taskId) { throw "kanban task missing id" }
Invoke-RestMethod -Uri "$ApiBase/v1/chats/$chatId/kanban/tasks/$taskId" -Method Patch -Headers $h -ContentType "application/json" `
  -Body (@{ column_key = "doing" } | ConvertTo-Json) | Out-Null

Write-Host "[7c] kanban delete"
Invoke-RestMethod -Uri "$ApiBase/v1/chats/$chatId/kanban/tasks/$taskId" -Method Delete -Headers $h | Out-Null

Write-Host "[8] poll close lifecycle"
$poll = Invoke-RestMethod -Uri "$ApiBase/v1/chats/$chatId/polls" -Method Post -Headers $h -ContentType "application/json" `
  -Body (@{ question = "Smoke?"; options = @("A", "B"); allow_multiple = $false } | ConvertTo-Json)
Invoke-RestMethod -Uri "$ApiBase/v1/chats/$chatId/polls/$($poll.id)/close" -Method Post -Headers $h | Out-Null

Write-Host "[9] scheduled list/cancel + reminder cancel"
$when = (Get-Date).AddHours(2).ToUniversalTime().ToString("o")
$msg = Invoke-RestMethod -Uri "$ApiBase/v1/chats/$chatId/messages" -Method Post -Headers $h -ContentType "application/json" `
  -Body (@{ type = "text"; content = "reminder smoke" } | ConvertTo-Json)
$sched = Invoke-RestMethod -Uri "$ApiBase/v1/chats/$chatId/messages/scheduled" -Method Post -Headers $h -ContentType "application/json" `
  -Body (@{ type = "text"; content = "later"; scheduled_at = $when } | ConvertTo-Json)
Invoke-RestMethod -Uri "$ApiBase/v1/me/scheduled-messages" -Headers $h | Out-Null
Invoke-RestMethod -Uri "$ApiBase/v1/me/scheduled-messages/$($sched.id)" -Method Delete -Headers $h | Out-Null
$rem = Invoke-RestMethod -Uri "$ApiBase/v1/me/reminders" -Method Post -Headers $h -ContentType "application/json" `
  -Body (@{ chat_id = $chatId; message_id = $msg.id; remind_at = $when } | ConvertTo-Json)
Invoke-RestMethod -Uri "$ApiBase/v1/me/reminders/$($rem.id)" -Method Delete -Headers $h | Out-Null

Write-Host "[10] AI assist mock"
Invoke-RestMethod -Uri "$ApiBase/v1/chats/$chatId/ai/assist" -Method Post -Headers $h -ContentType "application/json" `
  -Body (@{ prompt = "summarize" } | ConvertTo-Json) | Out-Null

Write-Host "[OK] smoke-phase5-adr-scaffolds"
