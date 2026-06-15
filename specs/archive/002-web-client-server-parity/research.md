# Research: Web Client Server Parity

## Decision 1: Incremental modularization of `app.js` vs framework rewrite

- **Decision**: Keep incremental utility extraction (`ui-*.js`) and avoid framework migration.
- **Rationale**:
  - Existing web-client is production-coupled to server contracts and ws behavior.
  - Framework migration would mix architecture rewrite with parity work and increase risk.
  - Current extraction already proved low-risk in previous phases (`ui-format`, `ui-shell`, `ui-transport`).
- **Alternatives considered**:
  - React/Vue rewrite: rejected for scope and regression risk.
  - Keep monolith `app.js`: rejected due to maintainability bottleneck.

## Decision 2: Parity source-of-truth

- **Decision**: Use non-admin `core-api` resources as parity source-of-truth.
- **Rationale**:
  - API contracts are already explicit and versioned in server code.
  - Prevents ambiguous interpretation of "feature completeness".
- **Alternatives considered**:
  - UI-first inventory: rejected (can miss endpoints).
  - NATS subject inventory only: rejected (does not cover user HTTP surface).

## Decision 3: Validation strategy (tests vs runtime smoke)

- **Decision**: Mandatory `:modules:web-client:test` for each phase, plus runtime smoke/manual scenarios on available stack.
- **Rationale**:
  - Servlet boundaries are already unit-tested.
  - Browser behavior (ws/calls/pwa/push) still needs runtime validation.
- **Alternatives considered**:
  - Add Playwright/Cypress now: deferred; useful but out-of-scope for parity delivery.

## Decision 4: Service worker/push rollout safety

- **Decision**: Keep env-driven guard (`disableServiceWorker`) and explicit update/reset controls in UI settings.
- **Rationale**:
  - Existing clients may carry stale SW state.
  - Need deterministic rollback path for static cache incidents.
- **Alternatives considered**:
  - Always-on SW: rejected due to operational risk.
  - Remove SW: rejected (regresses offline/update experience).

## Decision 5: Realtime consistency model

- **Decision**: Preserve optimistic UI + WS event convergence with deterministic merge/patch helpers.
- **Rationale**:
  - Current UX depends on immediate feedback.
  - Full server-only render cycle would reduce responsiveness.
- **Alternatives considered**:
  - Disable optimistic updates: rejected due to UX regression.

## Decision 6: Servlet boundary hardening approach

- **Decision**: Refactor boundaries by helper extraction only; no route/env contract changes.
- **Rationale**:
  - `WebClientApplication`, `UpstreamProxyServlet`, and `WebClientEnvServlet` are stable deployment boundaries.
  - Keeps compatibility with current scripts and infra assumptions.
- **Alternatives considered**:
  - Introduce new proxy layer/library: rejected as unnecessary complexity.
