<?php
declare(strict_types=1);

namespace Korus\PluginSdk;

final class PluginClient
{
    public function __construct(private readonly string $baseUrl) {}

    /** @param array<string, mixed> $event */
    public function handle(array $event): array
    {
        $json = json_encode($event, JSON_THROW_ON_ERROR);
        $ctx = stream_context_create([
            'http' => [
                'method' => 'POST',
                'header' => "Content-Type: application/json\r\n",
                'content' => $json,
                'timeout' => 30,
            ],
        ]);
        $url = rtrim($this->baseUrl, '/') . '/v1/plugin/handle';
        $raw = file_get_contents($url, false, $ctx);
        if ($raw === false) {
            throw new \RuntimeException('plugin request failed');
        }
        /** @var array<string, mixed> */
        return json_decode($raw, true, 512, JSON_THROW_ON_ERROR);
    }
}
