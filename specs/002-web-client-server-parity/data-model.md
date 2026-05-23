# Data Model: Web Client Server Parity

## 1. ParityCapability

Represents one user-facing capability mapped from server contract to web-client flow.

### Fields

- `id` (string): stable capability id, e.g. `chat.message.reaction`.
- `domain` (enum): `chat`, `message`, `file`, `export`, `realtime`, `rtc`, `pwa`, `settings`, `servlet-boundary`.
- `serverEndpoints` (string[]): endpoint patterns, e.g. `/v1/chats/{chatId}/messages/{msgId}/reactions`.
- `uiEntryPoints` (string[]): function/module anchors in web-client.
- `status` (enum): `covered`, `partial`, `missing`.
- `priority` (enum): `P1`, `P2`, `P3`.
- `validationGateIds` (string[]): links to required gates.

### Constraints

- `serverEndpoints` must be non-empty.
- `status=covered` requires at least one validation gate.

## 2. ParityGap

Represents a concrete mismatch between server capability and web-client behavior.

### Fields

- `capabilityId` (string): foreign key to `ParityCapability.id`.
- `gapType` (enum): `missing-ui`, `partial-flow`, `error-handling`, `state-convergence`, `contract-risk`.
- `severity` (enum): `critical`, `high`, `medium`, `low`.
- `symptom` (string): user-visible or operator-visible issue.
- `plannedPhase` (enum): `phase-1`..`phase-6`.
- `owner` (string): module/area owner.
- `resolved` (boolean): closure flag.

### Constraints

- `plannedPhase` required when `resolved=false`.

## 3. ClientModuleBoundary

Tracks responsibility and limits for each extracted utility module.

### Fields

- `moduleName` (string): e.g. `ui-transport-utils.js`.
- `owns` (string[]): responsibilities fully owned by module.
- `nonGoals` (string[]): explicit exclusions.
- `dependsOn` (string[]): runtime dependencies (e.g. `window.__WEB_CLIENT__`, `fetch`).
- `fallbackInApp` (boolean): whether compatibility fallback exists in `app.js`.

### Constraints

- No cyclic ownership between boundaries.

## 4. ScenarioGate

Represents a required verification checkpoint.

### Fields

- `id` (string): gate id, e.g. `gate-webclient-tests`.
- `type` (enum): `unit`, `integrity`, `runtime-smoke`, `manual-scenario`.
- `commandOrScenario` (string): command or manual scenario id.
- `mandatory` (boolean): must pass for phase completion.
- `phase` (enum): `phase-1`..`phase-6`.

### Constraints

- Mandatory gates cannot be skipped when closing a phase.

## Relationships

- `ParityCapability` 1..N `ParityGap`
- `ParityCapability` N..N `ScenarioGate`
- `ClientModuleBoundary` links to many `ParityCapability` through `uiEntryPoints`
