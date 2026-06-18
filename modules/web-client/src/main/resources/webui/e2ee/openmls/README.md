# OpenMLS WASM scaffold (spec 020 Phase 0)

Placeholder for future OpenMLS browser binding. Phase 0 ships a dev stub only.

## Files

| File | Role |
|------|------|
| `korus-openmls-dev.js` | Dev factory behind `e2ee_openmls_dev=1`; delegates to hybrid `korus-mls-wasm.js` until WASM lands |
| `openmls-bundle.js` | (Phase 1+) built OpenMLS WASM artifact — not committed yet |

## Build

From `modules/web-client/webui-build`:

```bash
npm run build:openmls
```

Copies `e2ee/openmls/*.js` into `src/main/resources/webui/e2ee/openmls/` (idempotent).

## Dev flag

- URL: `?e2ee_openmls_dev=1`
- Or `localStorage.setItem("e2ee_openmls_dev", "1")`

`app.js` selects `KorusOpenMlsDevFactory` when the flag is set.

## Vectors

Interop fixtures: `specs/020-openmls-interop/contracts/openmls-vectors.json`.
