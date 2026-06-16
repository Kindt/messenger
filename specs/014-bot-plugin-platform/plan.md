# Plan: Spec 014 — Bot-Plugin Platform

**Spec:** [`spec.md`](spec.md)  
**Design:** [`design/bot-plugin-platform-design.md`](design/bot-plugin-platform-design.md)  
**Date:** 2026-06-15  
**Status:** `draft`

---

## Phase P0 — Foundation (4–6 weeks)

| Deliverable | Notes |
|-------------|-------|
| OpenAPI `Plugin Runtime API v1` | PluginEvent / PluginResponse |
| integration-router (core-api) | route by plugin_type; no plugin code in API |
| Admin schema 3-level | preset JSON Schema + org policy + instance |
| L0 menu wizard | config-only → connector-runtime |
| connector-runtime worker (C) | hot-plug |
| `sdk/php` + `sdk/python` minimal | |
| Demos: `echo-bot-go`, `echo-bot-php74`, `echo-bot-php82` | polyglot smoke |
| Smokes | `smoke-plugin-echo-php.ps1` |

**Exit:** SC-014-01, SC-014-02

---

## Phase P1 — Integrations core (6–10 weeks)

| Deliverable | Notes |
|-------------|-------|
| Jira + Confluence via connector (L1/L2) | |
| bitrix24 PHP demo + optional bridge | native PHP REST |
| Outbound pipeline | integration.outbound → bot sendMessage |
| Naumen L1 smoke path | |
| Admin: test connection UI | |

**Exit:** SC-014-03, SC-014-04

---

## Phase P2 — Enterprise bridges + OCR (10–16 weeks)

| Deliverable | Notes |
|-------------|-------|
| exchange-bridge, storage-bridge, 1c-bridge | dedicated B |
| ocr-worker on-prem | |
| Legacy echo matrix | Java8, VB.NET, PowerShell 5.1 |

**Exit:** SC-014-05

---

## Phase P3 — AI L3 (16+ weeks)

| Deliverable | Notes |
|-------------|-------|
| ai-bridge | tool calls, policy hooks |
| L3 triage + optional RAG | on-prem LLM default |

**Exit:** SC-014-06

---

## Verification

```powershell
python scripts/test_competitor_products.py  # unrelated; plugin tests TBD
# Future:
# ./gradlew :modules:workers:connector-runtime:test
# .\scripts\smoke-plugin-echo-php.ps1
```
