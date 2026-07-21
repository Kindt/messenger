# SonarQube Community lab (QEMU + Docker)

Reusable single-VM lab for any repo that has `sonar-project.properties`.  
Windows host has **no Docker** — guest runs SonarQube + scanner.

| Piece | Detail |
|-------|--------|
| Host | QEMU (WHPX/TCG), PowerShell scripts |
| Guest | Ubuntu 24.04 cloud image + Docker Engine |
| App | `sonarqube:community` + Postgres 16 |
| Host URL | http://127.0.0.1:19000 |
| SSH | `ssh sonar@127.0.0.1 -p 12224` (password from env/`config.local.ps1`) |
| VM name | `sonar-lab` |
| Scan | **guest by default** (pinned `sonar-scanner-cli` image) |

## Use from this or another project

1. Copy/symlink `deploy/sonar-qemu/` (or keep a shared clone of the lab).
2. Put `sonar-project.properties` at the **project root** (modules, sources, binaries).
3. From the project that owns the properties:

```powershell
.\deploy\sonar-qemu\qemu-up.ps1 -KeepDisk     # or omit -KeepDisk for clean disk
.\deploy\sonar-qemu\sonar-scan.ps1            # reads properties from repo root
.\deploy\sonar-qemu\fetch-issues.ps1
.\deploy\sonar-qemu\quality-status.ps1
.\deploy\sonar-qemu\qemu-down.ps1
```

Point at another tree without moving the lab:

```powershell
.\deploy\sonar-qemu\sonar-scan.ps1 -RepoRoot D:\work\other-project
.\deploy\sonar-qemu\fetch-issues.ps1 -RepoRoot D:\work\other-project
```

## Commands

```powershell
.\deploy\sonar-qemu\install-qemu.ps1          # once
.\deploy\sonar-qemu\qemu-up.ps1               # wipe disk + boot (first time long)
.\deploy\sonar-qemu\sonar-scan.ps1            # host compile + scanner in guest
.\deploy\sonar-qemu\sonar-scan.ps1 -OnHost    # Windows scanner (CryptoPro risk)
.\deploy\sonar-qemu\sonar-scan.ps1 -SkipCompile
.\deploy\sonar-qemu\sonar-scan.ps1 -CompileCommand "mvn -q test-compile -DskipTests"

.\deploy\sonar-qemu\setup-lab-quality-gate.ps1
.\deploy\sonar-qemu\qemu-up.ps1 -KeepDisk
.\deploy\sonar-qemu\qemu-down.ps1
```

Default scan path:

1. Compile on host (`scripts/mvn-jdk25.ps1` if present, else `mvn`, else `-CompileCommand`)
2. Pack paths from `sonar-project.properties` (modules → sources/tests/binaries)
3. SSH upload → `docker run --network host sonarsource/sonar-scanner-cli`

First boot: cloud-init installs Docker, pulls Sonar — often **15–40 minutes**.  
First guest scan also pulls `sonar-scanner-cli` once.

Login after bootstrap: admin password from `SONAR_QEMU_ADMIN_PASSWORD` / `config.local.ps1` (see `config.local.ps1.example`).
Do not commit lab passwords; defaults in `config.ps1` are for local disposable VMs only.

## Secrets

Passwords for SSH / Sonar admin / Postgres lab defaults live in `config.ps1` as disposable fall-backs.
**Never commit real secrets.** Policy: `docs/plans/2026-07-15-r-secrets-policy.md`.
Prefer:

1. Environment: `SONAR_QEMU_GUEST_PASSWORD`, `SONAR_QEMU_ADMIN_PASSWORD`, `SONAR_QEMU_DB_PASSWORD`
2. Or copy `config.local.ps1.example` → `config.local.ps1` (gitignored)

Guest cloud-init still seeds the first-boot SSH password (`plain_text_passwd` in `cloud-init/user-data`).
Env/`config.local.ps1` do **not** rewrite an existing disk — changing cloud-init requires a clean disk (`qemu-up.ps1` without `-KeepDisk`).
Scanner image is pinned in `config.ps1` (see supply-chain lite).

## Layout

```
deploy/sonar-qemu/
  qemu-up.ps1 / qemu-down.ps1 / sonar-scan.ps1 / install-qemu.ps1
  fetch-issues.ps1 / quality-status.ps1 / setup-lab-quality-gate.ps1
  lib/ProjectProps.ps1  # properties + compile
  lib/GuestSsh.ps1 / tools/guest_ssh.py
  docker-compose.yml / cloud-init/
  images/  run/          # gitignored
```

Does **not** touch other QEMU labs (unique `-name sonar-lab` and ports).

## CryptoPro on Windows

Host `-OnHost` scanner can pop expired CryptoPro CSP (2×6 MSI).  
Default **guest** scan avoids that. Prefer guest; fix CSP license only if you need host scan.
