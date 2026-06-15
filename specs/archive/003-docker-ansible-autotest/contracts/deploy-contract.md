# Deploy Contract

**Feature**: 003-docker-ansible-autotest

## Compose file sets

| Topology | Compose files |
|----------|-----------------|
| Single-host / CI | `docker/docker-compose.full-server.yml` |
| LAN server | + `docker/docker-compose.lan-publish.yml` |
| Web (two-host) | `korus-web/docker-compose.yml` |

## Health endpoints

| Service | URL | Expected |
|---------|-----|----------|
| core-api | `{api_base}/api/v1/health` | HTTP 200 |
| retention-worker | `http://host:9192/health` | body contains ok |
| export-replay | `http://host:9193/health` | body contains ok |
| korus-web lb | `{web_base}/health` | body `ok` |

## Environment variables (web template)

| Variable | Source |
|----------|--------|
| `KORUS_SERVER_HOST` | `korus_server_lan_ip` |
| `WEB_CLIENT_API_UPSTREAM` | `http://{server_ip}:8080` |
| `WEB_CLIENT_WS_PUBLIC_URL` | `ws://{web_ip}:9088/ws` |
| `KORUS_WS_GATEWAY_HOST` | `korus_server_lan_ip` |
| `KORUS_WS_GATEWAY_PORT` | `8082` |

## Rollback

1. `docker compose -f ... down` on affected host
2. Re-deploy previous image tag via `korus_image_tag` group_var
3. Re-run `smoke-deploy-acceptance.sh`

## Post-deploy steps

1. Wait Keycloak on port 8081 (mapped) or 8080 realm path per stack
2. Run `scripts/keycloak-ensure-dev-users.sh`
3. Optional: `scripts/keycloak-ensure-smoke-users.sh`
