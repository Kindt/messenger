# Spec 014: Bot-Plugin Platform

**Feature branch:** `014-bot-plugin-platform`  
**Created:** 2026-06-15  
**Status:** `draft` — **Phase P0** implemented (2026-06-15); QEMU 3rd VM orchestration pending (T01427)  
**Input:** Система ботов-плагинов базового исполнения с админкой; интеграции Exchange, диски, 1С, Jira, Confluence, Naumen, **Bitrix24**; polyglot + legacy-friendly demos.

**Full design:** [`design/bot-plugin-platform-design.md`](design/bot-plugin-platform-design.md)

---

## Goal

Единая платформа интеграционных ботов с классами сложности **L0–L3**, исполнением **B+C+D** (connector + dedicated bridges + sidecar), **полиглотным** Plugin Runtime API и **трёхуровневой** админкой.

**Ключевые принципы:**

- код плагинов **не** в core-api (§17);
- **bidirectional** v1 (чат ↔ системы);
- **OCR on-prem**; LLM по org-policy;
- **Bitrix24 reference на PHP**;
- duplicate demo bots на modern + **legacy** стеках для заказчиков с «устаревшими» командами.

---

## Relationship to other specs

| Spec | Relationship |
|------|--------------|
| **009/010** | Bot API MVP/L2 — transport; 014 — hosted plugin runtime + admin |
| **012** | Sales positioning hosted/on-prem integrations |
| **011** | Cells — deploy bridges per tenant |

---

## User Stories (summary)

| US | Priority | Summary |
|----|----------|---------|
| US1 | P0 | Org-admin: preset + policy + register bot instance |
| US2 | P0 | L0 FAQ bot via admin wizard (no code) |
| US3 | P0 | Polyglot sidecar: PHP echo bot + Plugin SPI |
| US4 | P1 | Universal connector: Jira/Confluence L1/L2 |
| US5 | P1 | Bitrix24 PHP: CRM/task L1/L2 |
| US6 | P1 | Outbound: external event → message in chat |
| US7 | P2 | Dedicated bridges: Exchange, 1C, Naumen, storage |
| US8 | P2 | OCR on-prem: attachment → fields → L2 action |
| US9 | P3 | AI L3: triage, RAG (LLM policy) |
| US10 | P2 | Legacy demo matrix (Java8, VB.NET, PowerShell, PHP7.4…) |

See design doc for acceptance detail.

---

## Functional Requirements (index)

| ID | Requirement |
|----|-------------|
| FR-014-01 | Plugin Runtime API OpenAPI v1 (language-agnostic) |
| FR-014-02 | integration-router in core-api (no plugin bytecode in API JVM) |
| FR-014-03 | 3-level config: preset, org policy, instance |
| FR-014-04 | L0 config-only menu bots |
| FR-014-05 | Classes L0–L3 + OCR capability flag |
| FR-014-06 | Bidirectional event delivery v1 |
| FR-014-07 | OCR worker on-prem only |
| FR-014-08 | LLM provider abstraction + org policy |
| FR-014-09 | bitrix24-bridge / PHP reference demo |
| FR-014-10 | SDK stubs: java, python, go, csharp, typescript, **php** |
| FR-014-11 | Legacy duplicate echo demos + smoke per stack |
| FR-014-12 | Audit: plugin actions, secret_ref only |

---

## Success Criteria

| ID | Criterion |
|----|-----------|
| SC-014-01 | L0 bot created in admin without deploy; works in chat |
| SC-014-02 | PHP echo sidecar registered; @bot roundtrip on QEMU |
| SC-014-03 | Bitrix24 demo: at least one L1 read scenario |
| SC-014-04 | Outbound test: mock webhook → message in channel |
| SC-014-05 | Two legacy + two modern echo demos in CI smokes |
| SC-014-06 | Org policy blocks cloud LLM when `on_prem_only` |

---

## Out of scope (v1)

- Marketplace публичных плагинов
- Customer-upload arbitrary bytecode into platform JVM
- Mobile-native plugin UI beyond web admin
