<?php
declare(strict_types=1);

header('Content-Type: application/json; charset=utf-8');

$path = parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH);
if ($path === '/health') {
    http_response_code(200);
    echo 'ok';
    exit;
}

if ($path !== '/v1/plugin/handle' || ($_SERVER['REQUEST_METHOD'] ?? '') !== 'POST') {
    http_response_code(404);
    echo json_encode(['error' => 'not_found']);
    exit;
}

$raw = file_get_contents('php://input') ?: '{}';
/** @var array<string, mixed> $event */
$event = json_decode($raw, true) ?: [];
$type = (string)($event['type'] ?? '');
$text = trim((string)($event['text'] ?? ''));

if ($type === 'button') {
    $payload = is_array($event['payload'] ?? null) ? $event['payload'] : [];
    $buttonId = (string)($payload['button_id'] ?? '');
    if ($buttonId === 'about') {
        echo json_encode(['messages' => [['text' => "Korus echo-php82 v1\nhttps://korus.local", 'format' => 'markdown']]]);
        exit;
    }
}

if ($type === 'slash' && str_starts_with($text, '/echo ')) {
    echo json_encode(['messages' => [['text' => substr($text, 6), 'format' => 'markdown']]]);
    exit;
}

if (strtolower($text) === 'ping') {
    echo json_encode(['messages' => [['text' => 'pong (echo-php82)', 'format' => 'markdown']]]);
    exit;
}

echo json_encode([
    'messages' => [['text' => 'Echo PHP 8.2 sidecar. Try: ping, /echo text', 'format' => 'markdown']],
    'cards' => [[
        'title' => 'Меню',
        'buttons' => [
            ['id' => 'about', 'label' => 'О проекте'],
        ],
    ]],
]);
