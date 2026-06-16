# Design: Bot-Plugin Platform (Integration Runtime)

**Date:** 2026-06-15  
**Status:** `draft` — brainstorming approved  
**Parent:** Bot API §17 TZ, spec 009/010 (Bot MVP/L2), hot-plug ADR

---

## 1. Purpose

Платформа **встроенных ботов-плагинов** с настройкой через админку и **полиглотным** исполнением:

- простые **справочники L0** (кнопки → текст/ссылки);
- **читатели L1** (данные из внешних систем);
- **интеграторы L2** (двусторонний обмен, push в чаты/группы);
- **умные L3** (AI + OCR, on-prem/cloud LLM по policy).

**Ценность для заказчика:** не разгонять команду на «устаревшем» стеке — перенаправить на доработки интеграций через единый HTTP-контракт (PHP Bitrix, Java 8, VB.NET, PowerShell и т.д.).

---

## 2. Locked decisions (brainstorming)

| # | Decision |
|---|----------|
| D1 | Runtime **B+C+D**: universal connector + dedicated bridges + customer sidecar |
| D2 | UX **смешанный**: кнопки/карточки + slash + @бот |
| D3 | Admin **3 уровня**: platform preset → org policy → bot instance |
| D4 | **Bidirectional v1**: chat→system и system→chat с первого релиза |
| D5 | Классы **L0–L3** + capability **OCR** (не отдельный класс) |
| D6 | **OCR always on-prem**; **LLM** on-prem или cloud по org-policy |
| D7 | Wire protocol **language-agnostic** (HTTPS + JSON OpenAPI) |
| D8 | **Bitrix24** — reference integration на **PHP** (родной стек экосистемы) |
| D9 | **Duplicate demo bots** на modern + legacy стеках (один сценарий — N языков) |
| D10 | Demo backends: **mock default (CI)** + **vitrine-heavy** (Bitrix box + Jira) for presales |
| D11 | **Full demo catalog** ~43 artifacts — [`demo-plugin-catalog.md`](demo-plugin-catalog.md) |

---

## 3. Execution architecture

```
┌──────────────────────────────────────────────────────────────┐
│ core-api + Admin UI (plugin registry, 3-level config, audit)  │
│ integration-router: PluginEvent → runtime → PluginResponse    │
│ outbound: integration.outbound → sendMessage (bot identity)   │
└────────────┬─────────────────────────────────────────────────┘
             │ HTTPS JSON (Plugin Runtime API v1)
   ┌─────────┼──────────┬────────────┬──────────────┐
   ▼         ▼          ▼            ▼              ▼
connector-  exchange-  1c-bridge   bitrix24-     ocr-worker
runtime     bridge     naumen-     bridge (PHP)   ai-bridge
(C)         storage-   bridge                       (L3)
            bridge
             │
             ▼
      customer sidecar (D) — any language / legacy stack
```

**§17 TZ:** plugin code **не** в Tomcat core-api — только router + config.

**Hot-plug:** bridges = отдельные workers (`/health`, `/ready`, NATS optional для async jobs), см. ADR hot-plug.

---

## 4. Plugin classes (L0–L3)

| Class | User sees | Execution | Config |
|-------|-----------|-----------|--------|
| **L0** | Button tree → text, link, file | Config-only (JSON menu) in connector-runtime | Admin UI wizard, no deploy |
| **L1** | Query → read-only data | Universal connector REST mapping **or** thin bridge | Instance: endpoint, mapping |
| **L2** | Create/update, auto-notifications | Dedicated bridge **B** | Subscriptions, target chats, webhooks |
| **L3** | Dialog, extract intent, OCR→action | ai-bridge + ocr-worker + tool calls to L1/L2 | Model policy, prompts, RAG index ref |

**OCR capability:** attachment in chat → ocr-worker (on-prem) → structured fields → user confirms → L2 write (1C, Jira, Naumen).

---

## 5. Three-level administration

| Level | Owner | Examples |
|-------|-------|----------|
| **Platform preset** | Product | JSON Schema, plugin_type, capabilities, rate limits, SDK contract version |
| **Org policy** | Org-admin / ИБ | Allowlist plugins, vault refs, egress hosts, LLM mode, OCR mandate on-prem, sidecar allowlist |
| **Bot instance** | Integrator / bot owner | @name, chats, Jira project, 1C base, Bitrix portal URL, menu texts (L0) |

**Chat admin:** restrict commands per group (e.g. L0 only, or no auto-create ticket without @mention).

**Secrets:** `secret_ref` only; rotation audited.

---

## 6. Plugin Runtime API (polyglot contract)

**Transport:** HTTPS POST `/v1/plugin/handle` (+ optional `/v1/plugin/health`).

**Auth:** mTLS or HMAC `X-Plugin-Signature` + short-lived service token from org vault.

**Request (`PluginEvent`):**

```json
{
  "event_id": "uuid",
  "bot_instance_id": "uuid",
  "class": "L2",
  "type": "mention|slash|button|schedule|external_webhook",
  "user_id": "uuid",
  "chat_id": "uuid",
  "text": "/jira create",
  "payload": {},
  "config_snapshot": { "jira_project": "OPS" }
}
```

**Response (`PluginResponse`):**

```json
{
  "messages": [{ "text": "...", "format": "markdown" }],
  "cards": [{ "title": "...", "buttons": [{ "id": "approve", "label": "Согласовать" }] }],
  "defer": { "job_id": "..." },
  "external_actions": [{ "system": "jira", "op": "createIssue", "params": {} }]
}
```

**SDKs (thin):** `sdk/java`, `sdk/python`, `sdk/go`, `sdk/csharp`, `sdk/typescript`, **`sdk/php`** (Bitrix).

**Without SDK:** any stack with HTTP client ≥ curl era.

---

## 7. Integration map: universal vs dedicated

| System | Min class | Full | Runtime |
|--------|-----------|------|---------|
| **Bitrix24** | L1 CRM lookup | L2 tasks/deals sync, chat notify | **bitrix24-bridge (PHP)** + REST |
| **MS Exchange** | L1 free/busy | L2 meetings, reminders | exchange-bridge |
| **Disk** (SMB/WebDAV/S3/SharePoint) | L1 search+link | L2 share-to-chat ACL | storage-bridge |
| **1С** (ERP/DOC/…) | L1 status | L2 approval, OCR invoice | 1c-bridge family |
| **Jira** | L1 status/search | L2 create, channel digest | connector-runtime (C) |
| **Confluence** | L1 search | L2 publish summary | connector-runtime (C) |
| **Naumen** | L1 ticket status | L2 create, SLA alerts | naumen-bridge |
| **OCR** | capability | invoice/act → systems | ocr-worker (on-prem) |
| **AI** | L3 triage, RAG | thread→ticket | ai-bridge |
| **Custom ERP** | — | L2 | sidecar **D** |

---

## 8. Bitrix24 (PHP reference)

**Why PHP:** Bitrix24 local apps, REST, коробочная версия — естественный стек для команд заказчика.

**Deliverables:**

- `integrations/demos/bitrix24-crm-bot/` — PHP 8.x sidecar + Bitrix REST
- `integrations/demos/bitrix24-crm-bot-legacy/` — PHP 7.4 style (legacy hosting) — **same Plugin SPI**
- Scenarios: лид из чата → CRM; смена стадии сделки → сообщение в канал; @бот «мои задачи»

**Bridge option:** if org runs Bitrix on same LAN — `bitrix24-bridge` hot-plug can batch webhooks; PHP demo shows **sidecar D** path.

---

## 9. Legacy-friendly demo matrix (“Echo / FAQ clone”)

**Message to customer:** один сценарий «Echo + 2 кнопки» реализован на разных стеках — команда выбирает свой.

| Demo | Stack | Era narrative |
|------|-------|---------------|
| `echo-bot-java8` | Java 8, no records | «Существующая Java-команда» |
| `echo-bot-php74` | PHP 7.4 procedural | «Bitrix/legacy hosting» |
| `echo-bot-vbnet` | VB.NET Framework 4.8 | «Win-сервер, .NET без Core» |
| `echo-bot-powershell` | PowerShell 5.1 | «Admins / ops one-off» |
| `echo-bot-go` | Go 1.22 | modern minimal |
| `echo-bot-python` | Python 3.12 | AI/OCR team |
| `echo-bot-delphi` | optional FPC/Delphi | «Промышленность, старый код» |

**CI:** each demo — Docker or documented host run; one smoke: register sidecar URL → @bot ping → pong.

**Docs:** «Migration path» — не переписывать ERP, обернуть HTTP адаптером 200–300 строк.

---

## 10. End-user corporate scenarios (summary)

- **L0:** HR/IT FAQ, телефонный справочник, ссылки на порталы
- **L1:** Confluence/Jira search, 1C order status, disk file lookup
- **L2:** Naumen/SD tickets, Jira create, Exchange meetings, Bitrix deal updates, group digests
- **OCR:** счёт/акт из вложения → поля → согласование в 1C
- **L3:** triage to queue, thread summary, RAG answers (on-prem LLM)

**UX:** L0 buttons only; L1 buttons + @; L2/L3 + slash + inline cards.

---

## 11. AI & OCR policy (decision C)

| Component | Rule |
|-----------|------|
| **OCR** | Always on-prem worker; files never leave contour for cloud OCR |
| **LLM** | Org policy: `on_prem_only` \| `cloud_allowed` \| `hybrid` |
| **Cloud LLM** | PII masking hook required; audit log of prompts (redacted) |
| **Tools** | L3 may call L1/L2 bridges; deny arbitrary egress in org policy |

---

## 12. Phasing (recommended)

| Phase | Scope |
|-------|--------|
| **P0** | Router API, admin 3-level schema, L0 wizard, connector-runtime, Plugin SPI OpenAPI, echo demos (3 languages incl. **PHP**) |
| **P1** | L2 bridges: Jira (C), Naumen, storage L1; bidirectional outbound; **bitrix24 PHP** L1/L2 |
| **P2** | exchange, 1c family, ocr-worker, legacy echo matrix |
| **P3** | ai-bridge, L3 tools, RAG; full demo catalog |

---

## 13. Testing & acceptance

- Unit: router mapping, signature verify, config validation
- Integration: mock PluginResponse per class
- Smoke: `smoke-plugin-echo-php.ps1`, `smoke-bitrix24-bot.ps1` on QEMU
- Security: secret never in logs; sidecar URL HTTPS only; org allowlist

---

## 14. Related artifacts

- spec **014** — speckit entry
- **Demo catalog:** [`design/demo-plugin-catalog.md`](demo-plugin-catalog.md) — vitrine + integrations + polyglot (~43 artifacts)
- Bot API L2 (spec 010): long-poll, moderation — complementary, not duplicate
- [`docs/adr/ADR-hotplug-deployment-split.md`](../../../docs/adr/ADR-hotplug-deployment-split.md)

---

*Brainstorming session 2026-06-15 — decisions D1–D11.*
