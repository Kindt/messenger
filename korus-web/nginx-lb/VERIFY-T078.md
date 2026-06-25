# T078 / FR-173 — ручная проверка gzip, brotli и Cache-Control

Автотестов нет. Проверка на lab/QEMU (`http://127.0.0.1:19088`) или локальном korus-web после пересборки lb:

```bash
docker compose -f korus-web/docker-compose.yml -f korus-web/docker-compose.nginx-only.yml up --build -d
```

## 1. Конфиг nginx

```bash
docker compose -f korus-web/docker-compose.yml -f korus-web/docker-compose.nginx-only.yml exec lb nginx -t
```

Ожидание: `syntax is ok` / `test is successful`.

## 2. Gzip

```bash
curl -sI -H "Accept-Encoding: gzip" http://127.0.0.1:19088/ui-i18n.js | grep -iE '^(content-encoding|vary):'
```

Ожидание: `Content-Encoding: gzip`, `Vary: Accept-Encoding`.

## 3. Brotli

```bash
curl -sI -H "Accept-Encoding: br" http://127.0.0.1:19088/ui-i18n.js | grep -iE '^(content-encoding|vary):'
```

Ожидание: `Content-Encoding: br` (если клиент шлёт только `br`; иначе nginx может отдать gzip).

Комбинированный запрос:

```bash
curl -sI -H "Accept-Encoding: gzip, br" http://127.0.0.1:19088/styles.css
```

Ожидание: `br` или `gzip` в `Content-Encoding`.

## 4. Cache-Control

| URL | Ожидаемый заголовок |
|-----|---------------------|
| `/web-client-env.js` | `Cache-Control: no-store, max-age=0` |
| `/sw.js` | `no-store, max-age=0` |
| `/index.html` | `no-store, max-age=0` |
| `/manifest.json` | `no-cache, must-revalidate` |
| `/ui-i18n.js` | `public, max-age=604800` (+ optional `stale-while-revalidate`) |
| `/locales/ru.json` | `public, max-age=3600, must-revalidate` |

Пример:

```bash
curl -sI http://127.0.0.1:19088/web-client-env.js | grep -i cache-control
curl -sI http://127.0.0.1:19088/ui-i18n.js | grep -i cache-control
```

## 5. Hashed assets (после FR-040 bundler)

Для файла вида `/assets/app.a1b2c3d4.js` или `*.a1b2c3d4e5f67890.js`:

```bash
curl -sI http://127.0.0.1:19088/assets/example.a1b2c3d4e5f67890.js | grep -i cache-control
```

Ожидание: `public, max-age=31536000, immutable` (при наличии файла на диске).

## 6. Регрессия smoke

```bash
./scripts/smoke-korus-web.sh --url http://127.0.0.1:19088 --check-api
```
