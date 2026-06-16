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

$mockBase = getenv('BITRIX_MOCK_BASE') ?: 'http://mock-apis:8080';
$raw = file_get_contents('php://input') ?: '{}';
/** @var array<string, mixed> $event */
$event = json_decode($raw, true) ?: [];
$type = (string)($event['type'] ?? '');
$text = trim((string)($event['text'] ?? ''));

if ($type === 'slash' && str_starts_with(strtolower($text), '/deal ')) {
    $dealId = trim(substr($text, 6));
    if ($dealId === '') {
        $dealId = '42';
    }
    $fixture = fetchDeal($mockBase, $dealId);
    $title = (string)($fixture['TITLE'] ?? 'Unknown deal');
    $stage = (string)($fixture['STAGE_ID'] ?? '?');
    $amount = (string)($fixture['OPPORTUNITY'] ?? '?');
    echo json_encode([
        'messages' => [[
            'text' => "**Сделка $dealId**: $title\nСтадия: **$stage** | Сумма: $amount RUB",
            'format' => 'markdown',
        ]],
    ]);
    exit;
}

if (strtolower($text) === 'ping' || strtolower($text) === '/bitrix ping') {
    echo json_encode(['messages' => [['text' => 'pong (bitrix24-crm-bot PHP)', 'format' => 'markdown']]]);
    exit;
}

echo json_encode([
    'messages' => [[
        'text' => 'Bitrix24 CRM sidecar (PHP). Команды: `ping`, `/deal <id>`',
        'format' => 'markdown',
    ]],
    'cards' => [[
        'title' => 'CRM',
        'buttons' => [
            ['id' => 'deal42', 'label' => 'Сделка 42'],
        ],
    ]],
]);

/** @return array<string, mixed> */
function fetchDeal(string $mockBase, string $dealId): array
{
    $url = rtrim($mockBase, '/') . '/bitrix/rest/crm.deal.get.json?id=' . rawurlencode($dealId);
    $ctx = stream_context_create(['http' => ['timeout' => 5]]);
    $body = @file_get_contents($url, false, $ctx);
    if ($body === false) {
        return ['TITLE' => 'Mock offline', 'STAGE_ID' => 'n/a', 'OPPORTUNITY' => '0'];
    }
    /** @var array<string, mixed> $json */
    $json = json_decode($body, true) ?: [];
    return $json;
}
