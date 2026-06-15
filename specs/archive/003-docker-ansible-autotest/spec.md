# Feature Specification: Docker + Ansible Deployment & Autotest Suite

**Feature Branch**: `003-docker-ansible-autotest`

**Created**: 2026-05-27

**Status**: Ready for Implementation

**Input**: Transition to Docker + Ansible deployment with comprehensive server and web-client autotests; post-deploy acceptance with multi-user messaging and group chat scenarios.

## User Scenarios & Testing

### User Story 1 — Ansible deployment for Linux hosts (Priority: P1)

As an operations engineer, I want idempotent Ansible playbooks to bootstrap and deploy the Korus Docker stacks on single-host and two-host topologies, so that Linux/stage/prod deployments are reproducible without ad-hoc shell scripts.

**Independent Test**: `ansible-playbook -i inventory/local playbooks/ci-local.yml` brings up full-server; health endpoints respond.

**Acceptance Scenarios**:

1. **Given** a fresh Ubuntu host with Docker available, **When** `ci-local.yml` runs, **Then** full-server compose is up and `GET /api/v1/health` returns OK.
2. **Given** a two-host inventory, **When** `site.yml` runs, **Then** server and web stacks deploy with correct upstream URLs in `korus-web/.env`.
3. **Given** a re-run of the same playbook, **When** no config changed, **Then** the run is idempotent (no unnecessary restarts).

---

### User Story 2 — Multi-user messaging E2E smoke (Priority: P1)

As a QA engineer, I want an automated smoke that creates multiple users, exchanges direct and group messages, and verifies delivery across REST and WebSocket, so that core messaging works after every deploy.

**Independent Test**: `scripts/smoke-messaging-e2e.sh` exits 0 against a running stack.

**Acceptance Scenarios**:

1. **Given** three test users, **When** user A sends two DM messages to user B, **Then** B sees both via REST.
2. **Given** user A creates a group with B and C, **When** A sends three messages and B replies, **Then** all members see the thread state correctly.
3. **Given** B is connected via WebSocket, **When** A sends a message, **Then** B receives a delivery frame (or REST fallback within timeout).
4. **Given** B marks messages read, **When** A queries read-receipts, **Then** B appears in `read_by`.

---

### User Story 3 — Deploy acceptance orchestrator (Priority: P1)

As a release manager, I want a single acceptance script after Ansible deploy that runs readiness, auth, messaging, and parity smokes, so that "transition complete" is objectively verifiable.

**Independent Test**: `scripts/smoke-deploy-acceptance.sh` passes after `ci-local.yml`.

---

### User Story 4 — CI nightly deploy + smoke (Priority: P2)

As a maintainer, I want a GitHub Actions workflow that deploys via Ansible and runs the acceptance pack on schedule, mirroring export-compliance-smoke patterns.

**Independent Test**: Manual workflow_dispatch completes green on ubuntu-latest.

---

### User Story 5 — Web-client Playwright critical path (Priority: P2)

As a developer, I want browser E2E for login → group → send → see message, so that web-client regressions are caught beyond API smokes.

**Independent Test**: `npx playwright test` in `tests/e2e-web/` passes against korus-web stack.

---

## Requirements

### Functional Requirements

- **FR-001**: Ansible MUST wrap existing Docker Compose files; MUST NOT replace container runtime.
- **FR-002**: Playbooks MUST support `inventory/local` (single node) and `inventory/two-host`.
- **FR-003**: `smoke-messaging-e2e` MUST cover DM (2 msgs), group (3 msgs + reply), WS deliver, cross-user read receipts.
- **FR-004**: `smoke-deploy-acceptance` MUST orchestrate wait-ready, auth, messaging-e2e, web-parity-api, optional korus-web.
- **FR-005**: CI workflow MUST NOT block PR gate (`buildIntegrity` unchanged); full stack smoke is nightly/manual.
- **FR-006**: Windows dev scripts (`full-stack-up.ps1`, `server-host-up.ps1`) MUST remain functional.

### Key Entities

- **DeployTarget**: host group, compose file set, env template vars.
- **SmokeScenario**: named steps with REST/WS assertions.
- **TestUser**: username, password, Keycloak/API identity.

## Assumptions

- Target OS for Ansible: Ubuntu 22.04/24.04 with Docker Engine.
- Test users created via `/api/v1/auth/register` with fallback to Keycloak ensure script.
- WS gateway default port 8082 on full-server; API on 8080.
- Playwright P1 covers critical path only; full parity-matrix deferred.

## Out of Scope

- Bare-metal JAR deployment without Docker
- Prod TLS/Let's Encrypt (phase B)
- Replacing all 48 legacy smoke scripts
