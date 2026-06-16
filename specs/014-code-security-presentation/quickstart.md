# Quickstart: Spec 014 — Security gate

## Local (Windows host)

```powershell
# Full PR gate (no QEMU)
./gradlew buildIntegrity

# With live stack smokes
.\scripts\security-gate.ps1
.\scripts\security-gate.ps1 -SkipBuild    # smokes only
.\scripts\security-gate.ps1 -SkipQemuSmokes  # build only
```

## Fix spotless on touched files

```powershell
./gradlew spotlessApply
```

Ratchet: only files changed vs `origin/main` are checked in CI.

## CI parity

Same as GitHub Actions job `build` in `.github/workflows/ci.yml`.
