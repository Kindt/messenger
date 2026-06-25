# Icon set policy (Korus messenger shell)

**Scope:** `iconBtn(emoji|iconId, L("title"), { testId })` via `KorusIconButtons.iconButton` in `ui-icon-buttons.js`.

Designer **MUST** cite this in ux-spec **Icons** table. Engineer **MUST NOT** invent new glyphs without designer row.

---

## Rules

1. **Second arg always `L("…")`** — tooltip + accessibility (axis D/E).
2. **`data-testid`** on every new interactive (axis D).
3. **Primary on mobile:** visible text label **or** tab label nearby — not icon alone (ICON-RED-01).
4. **Destructive:** icon + row label or confirm; prefer delete/revoke ids with verb in `L()` title.
5. **Brand names** (Jitsi, LiveKit, Mesh): only in call/conference via `ui.call.*` / `conference.*` keys — not hardcoded EN in `iconBtn`.
6. **No decorative emoji** in headings unless existing shell pattern (`settings-subtitle` = text only).

---

## Rendering (SVG sprite, 2026-06-25)

Standard actions map **emoji → SVG symbol** in `ui-icon-buttons.js` (`EMOJI_TO_ICON`). Unmapped icons still render as emoji.

Engineer may pass explicit id:

```javascript
iconBtn(null, L("ui.common.close"), { iconId: "close", testId: "..." });
```

Sprite symbols: `#korus-icon-{id}` (close, save, delete, refresh, mesh, video, …).

CSS: `.btn-icon-svg .ui-icon` in `webui-build/src/styles.css`.

---

## Standard map (reuse before inventing)

| Action | Emoji / iconId | Title key pattern |
|--------|----------------|-------------------|
| Close | ✕ / `close` | `ui.common.close` |
| Save | 💾 / `save` | `ui.common.save` |
| Delete | 🗑 / `delete` | `ui.actions.delete` |
| Refresh | ↻ / `refresh` | `ui.common.refresh` / surface-specific |
| Download | ⬇ / `download` | `ui.common.download` |
| Revoke | 🚫 / `revoke` | `ui.common.revoke` |
| Add | ＋ / `add` | `ui.settings.create` / context |
| Open external | ↗ / `external` | `ui.settings.goToMessage` |
| Copy link | 📋 / `copy` | `conference.copyLinkHint` |
| Notifications on/off | 🔔🔕 / `bell-on` `bell-off` | `ui.common.on` / `off` |
| Sound on/off | 🔊🔇 / `sound-on` `sound-off` | `ui.common.on` / `off` |
| Theme | 🌙☀️ / `moon` `sun` | `ui.common.darkTheme` / `lightTheme` |
| Call mic/cam | 🎤🔇📷 / `mic-on` `mic-off` `camera` | `ui.call.micOn` / … |
| Screen share | 🖥 / `screen` | `ui.call.screen` |
| PWA install | 📲 / `pwa` | `ui.settings.pwaInstallBtn` |
| Logout | 🚪 / `logout` | `common.logout` |
| Settings | ⚙ / `settings` | `ui.shell.settings` |
| Mesh / Meeting / Live | 📡🎥☁ / `mesh` `video` `cloud` | `ui.call.modeMesh` / … |

---

## Empty / status blocks

- **No emoji** in empty state title — text via `L()` (`ui.search.emptyTitle`).
- Status pills: prefer CSS + text (`ws-status`, `federation-trust-badge`), emoji only inside existing call/shell patterns.

---

## CI / QA

- `node scripts/webui-label-lint.js` — parity + iconBtn tooltip lint (also `./gradlew checkWebuiLabelLint`).
- Playwright tier `ui-visual-regression` — settings tabs + search empty snapshots.

---

## Evaluator checks (axis D)

- [ ] New icon in ux-spec table matches map or justified
- [ ] No ICON-RED-02 duplicate semantics (delete vs revoke for same action in one screen)
- [ ] grep: no `iconBtn\([^,]+,\s*"[A-Za-z]` in touched files
