# Code Review Report — 2026-05-23

Scope: technical-debt scan for `modules/` with focus on dead/duplicate logic, exception handling, dependency drift, and config hygiene.

## Findings

| Severity | Category | Location | Finding | Recommendation |
|---|---|---|---|---|
| high | error-handling | `modules/core-api/src/main/java/com/avandocmsg/messenger/api/admin/AdminExportFacade.java` | `catch (Exception ignored)` silently drops audit-write failures in multiple paths, reducing observability for admin actions. | Replace ignored catches with structured warn-level logs including action/job context. |
| high | error-handling | `modules/core-api/src/main/java/com/avandocmsg/messenger/api/health/HealthResource.java` | `ready()` swallows DB exceptions (`catch (Exception ignored)`), making root-cause diagnosis harder during outage. | Keep `503` behavior but log exception class/message at debug or warn level. |
| medium | error-handling | `modules/core-api/src/main/java/com/avandocmsg/messenger/api/admin/ui/AdminServerStatsService.java` | stale-count query errors are swallowed (`catch (Exception ignored)`), metrics degrade silently to zero. | Emit warning with query context and fallback value marker. |
| medium | error-handling | `modules/core-api/src/main/java/com/avandocmsg/messenger/api/repository/ChatRepository.java` | high density of broad `catch (Exception e)` blocks reduces error typing and complicates retry/alerting strategy. | Narrow to SQL/validation exception classes where possible; standardize error wrappers. |
| medium | duplicate-logic | `modules/workers/deep-archiver/src/main/java/com/avandocmsg/messenger/worker/deeparchive/DeepArchiverWorker.java` + `modules/workers/retention/src/main/java/com/avandocmsg/messenger/worker/retention/RetentionHotBodyJanitor.java` | chunk split + manifest write flow is implemented in two places, risking drift. | Extract shared chunk writer into `modules/common` and reuse from both workers. |
| medium | config-consistency | `modules/core-api/src/main/java/com/avandocmsg/messenger/api/config/AppConfig.java` | `exportProcessingStaleMinutes()` reads env directly (`System.getenv`) instead of using centralized `overrideFromEnv()` mapping. | Move env mapping to `overrideFromEnv()` for one-path config precedence. |
| medium | config-hygiene | `modules/core-api/src/main/java/com/avandocmsg/messenger/api/config/AppConfig.java` | several sensitive defaults (DB credentials, Keycloak admin creds) are hardcoded for local convenience and can leak to misconfigured environments. | Keep local defaults only in dev profile; fail-fast in non-dev when secrets are unset. |
| low | performance | `modules/core-api/src/main/java/com/avandocmsg/messenger/api/health/HealthResource.java` | Redis client is created per health probe call (`RedisClient.create` each request). | Reuse shared Redis client/pool in health checks to reduce probe overhead. |
| low | dependency-hygiene | `modules/workers/retention/build.gradle.kts`, `modules/workers/deep-archiver/build.gradle.kts` | MinIO SDK version drift existed (8.5.10 vs 8.5.17). | Consolidated to `8.5.17` across worker modules. |
| low | docs-drift | `modules/workers/retention/README.md` | module README referenced old MinIO version. | Updated docs to match effective dependency version. |

## Coverage Notes

- Dead code scan performed via targeted repository search and compile/test checks.
- Exception-handling scan covered core-api and worker hot paths with emphasis on swallowed exceptions.
- AppConfig review focused on env override consistency and operational defaults.
- Dependency scan focused on MinIO drift highlighted in plan task T041.

## Recommended Next Fix Set

1. Refactor duplicate chunk writer into `modules/common` (highest leverage for correctness).
2. Replace all `catch (Exception ignored)` with logged fallback paths.
3. Normalize AppConfig env precedence (single override mechanism).
4. Add lightweight lint rule/check for broad exception catches in repository/service layers.
