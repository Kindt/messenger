# Phase 5 messaging smoke: polls, scheduled send, reminders (spec 022).
param(
    [string]$BaseUrl = "http://127.0.0.1:18080"
)

$ErrorActionPreference = "Stop"
$API = "$BaseUrl/api/v1"

function Login-User($user, $pass) {
    $login = Invoke-RestMethod -Method POST -Uri "$API/auth/login" -ContentType "application/json" `
      -Body (@{ username = $user; password = $pass } | ConvertTo-Json -Compress)
    return $login.access_token
}

function Get-MeId($token) {
    $me = Invoke-RestMethod -Method GET -Uri "$API/users/me" `
      -Headers @{ Authorization = "Bearer $token" }
    $id = $me.id
    if (-not $id) { $id = $me.user_id }
    if (-not $id) { throw "users/me missing id" }
    return $id
}

function New-GroupChat($token, $title, $memberIds) {
    $body = @{
      type = "group"
      title = $title
      member_ids = $memberIds
    } | ConvertTo-Json -Compress
    $chat = Invoke-RestMethod -Method POST -Uri "$API/chats" `
      -Headers @{ Authorization = "Bearer $token" } `
      -ContentType "application/json" -Body $body
    if (-not $chat.id) { throw "create group missing id" }
    return $chat.id
}

$token = Login-User "csadmin" "csadmin"
$headers = @{ Authorization = "Bearer $token" }
$meId = Get-MeId $token
$chatId = New-GroupChat $token "Smoke phase5" @($meId)

$pollBody = @{
  question = "Smoke poll?"
  options = @("A", "B")
  allow_multiple = $false
} | ConvertTo-Json -Compress
$poll = Invoke-RestMethod -Method POST -Uri "$API/chats/$chatId/polls" `
  -Headers $headers -ContentType "application/json" -Body $pollBody
if (-not $poll.id) { throw "poll create missing id" }

$pollList = Invoke-RestMethod -Method GET -Uri "$API/chats/$chatId/polls" -Headers $headers
if (-not ($pollList -is [array])) { throw "poll list expected array" }

$scheduledAt = [DateTime]::UtcNow.AddHours(1).ToString("o")
$schedBody = @{
  type = "text"
  content = "smoke scheduled"
  scheduled_at = $scheduledAt
} | ConvertTo-Json -Compress
$sched = Invoke-RestMethod -Method POST -Uri "$API/chats/$chatId/messages/scheduled" `
  -Headers $headers -ContentType "application/json" -Body $schedBody
if ($sched.status -ne "pending") { throw "scheduled status=$($sched.status)" }

$msgBody = @{ type = "text"; content = "smoke reminder target" } | ConvertTo-Json -Compress
$msg = Invoke-RestMethod -Method POST -Uri "$API/chats/$chatId/messages" `
  -Headers $headers -ContentType "application/json" -Body $msgBody
if (-not $msg.id) { throw "send message missing id" }

$remindAt = [DateTime]::UtcNow.AddHours(2).ToString("o")
$remBody = @{
  chat_id = $chatId
  message_id = $msg.id
  remind_at = $remindAt
} | ConvertTo-Json -Compress
$rem = Invoke-RestMethod -Method POST -Uri "$API/me/reminders" `
  -Headers $headers -ContentType "application/json" -Body $remBody
if ($rem.status -ne "pending") { throw "reminder status=$($rem.status)" }

$remList = Invoke-RestMethod -Method GET -Uri "$API/me/reminders" -Headers $headers
if (-not ($remList -is [array])) { throw "reminder list expected array" }

Write-Host "[OK] phase5 smoke chat=$chatId poll=$($poll.id) scheduled=$($sched.id) reminder=$($rem.id)"
