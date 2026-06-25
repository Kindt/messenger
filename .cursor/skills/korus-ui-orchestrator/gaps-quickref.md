# Gaps quickref (engineer + QA)

Full analysis: `specs/026-cursor-ui-agent-orchestrator/design/coverage-gaps.md`

---

## Build matrix (after code change)

| You changed | Run (in `modules/web-client/webui-build/`) | Before QEMU sync |
|-------------|---------------------------------------------|------------------|
| `locales/messages/*.json` | `npm run build:locales` | parity audit |
| `tw:*` in JS/HTML | `npm run build:css` | — |
| `webui-build/src/styles.css` | `npm run build:styles` | — |
| Any `ui-*.js`, `app.js` | **`npm run build:js`** | **required** — browser loads `app.bundle.js` |
| New file in bundle | edit `scripts/bundle-script-order.mjs` + `build:js` | — |
| Full release assets | `npm run build:assets` | sync-web |
| `ui-call-mesh.js` / `ui-call-livekit.js` | lazy chunk — verify call flow manually | `ui-call-flows` |

**Hotswap:** `qemu-dev-mode.ps1 -Mode sync-ui` copies webui files — **useless for JS logic if bundle not rebuilt**.

---

## Modifiers (orchestrator)

| Flag | Trigger |
|------|---------|
| `+BUNDLE` | any `ui-*.js` / `app.js` edit |
| `+E2EE` | `ui-e2ee-*`, MLS wasm, openmls paths |
| `+ADDON` | live, bot, e2ee, push UI — check lab addons |
| `+PWA` | `sw.js`, manifest, push install UI |

---

## CSS truth

- **Edit source:** `webui-build/src/styles.css`
- **Load order:** fonts → tailwind → styles → themes (`index.html`)
- **Breakpoints:** 960px, 520px in source CSS

---

## URLs

| Surface | URL |
|---------|-----|
| Messenger shell | `http://127.0.0.1:19088/` |
| API health | `http://127.0.0.1:18080/api/v1/health` |
| Admin browser UI | core-api `/admin/` — tier `ui-admin`, **not** orchestrator messenger path |

---

## Addon lab gaps

If UI hidden because `platformCaps.modules[addon-*]` disabled → QA **SKIP** tier with note, not FAIL.

---

## UX gate (P2/P3)

Before Engineer on new UI behavior:

1. Designer → `ui-ux-spec`
2. **UX Evaluator** → `ui-ux-review` (axes **A** placement, **B** usability, **C** section/IA)
3. Any axis ≤2 → back to Designer (unless user waiver)

Framework: `specs/026-cursor-ui-agent-orchestrator/design/ux-evaluation-framework.md`
