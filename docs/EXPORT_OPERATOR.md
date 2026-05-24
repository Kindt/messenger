# Export operator guide

## Package contents

- `export.json` — chat metadata, messages, completeness block
- `attachments/manifest.json` — file references
- `attachments/{hash}/{file}` — optional binary bodies when enabled

## Retention

- Artifacts live under `EXPORT_DIR` (default 30 days operational policy; configure via deployment).
- Completeness: `GET /api/v1/chats/{chatId}/export/{jobId}` and `export.json` → `exportCompleteness.complete`.

## Mandatory fields

Configured via `EXPORT_REQUIRED_FIELDS` (CSV). Validated by `ExportCompletenessValidator` in export-replay worker.
Strict mode: `EXPORT_COMPLETENESS_STRICT=true` fails job with `export_failed`.

## Pre-retention export

Run `scripts/pre-retention-export.ps1` before aggressive purge when `EXPORT_REQUIRED_BEFORE_PURGE=true`.

## Smoke

- `scripts/smoke-export-gdpr-fulfillment.ps1`
- `scripts/smoke-export-chat.ps1`
