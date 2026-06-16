# Spec 010: Presentation Gaps Closure



Закрытие §4 продуктовой презентации: **Звонки, E2EE, Push/PWA, Bot API, Prod HTTPS, Live-streaming**.



| Artifact | Description |

|----------|-------------|

| [`spec.md`](spec.md) | Speckit entry point — 6 user stories, FR index, success criteria |

| [`design/multi-stakeholder-spec.md`](design/multi-stakeholder-spec.md) | **Полная проработка** 6 блоков × 7 ролей (аналитик…маркетолог) |

| [`plan.md`](plan.md) | Phases A–D |

| [`tasks.md`](tasks.md) | Actionable checklist T101–T407 |

| [`contracts/presentation-gaps-acceptance-contract.md`](contracts/presentation-gaps-acceptance-contract.md) | Per-US acceptance rows |

| [`quickstart.md`](quickstart.md) | QEMU smoke commands |



**Status:** **engineering closed** (2026-06-16) — Phase A/C/D L0–L1 done; **Phase B ops blocked until Sep 2026** (no stage host).  

**Related:** spec 007 (ops), spec 009 (Bot MVP closed), spec **012** (competitor presentation spider), spec **013** (live-streaming L1)



**Presentation source:** [`docs/PRODUCT_PRESENTATION.md`](../../docs/PRODUCT_PRESENTATION.md) §4



### Engineering exit criteria (met)



| Phase | Scope | Gate |

|-------|-------|------|

| A | TURN/Push/Bot QEMU smokes + Playwright `ui-bot` | ✅ guest smokes; **T110 outer 34/34** |

| C | Bot API L2 (long-poll, moderation, rotate, rate limit) | ✅ code + smokes |

| D L0–L1 | ADR + spec 013 | ✅ |

| B | T601–T607, E2EE sign-off, presentation §4 → Реализовано | ⏸ Sep 2026+ |

