# Mobile build lab (spec 032)

**Политика lab (автоматизация):** Maestro/conveyor — в QEMU guest `korus-mobile-build` (headless).  
**Визуально на мониторе:** Android Studio emulator на **Windows host** — [`android-studio-host.md`](android-studio-host.md), `.\scripts\mobile-android-studio.ps1`.  
**WSL** для Android SDK/emulator не используем.

## Android — QEMU `korus-mobile-build`

Ubuntu 24.04 guest with JDK 17, Android SDK, Maestro, headless AVD.

| | |
|---|---|
| VM | `korus-mobile-build` |
| Guest IP | `192.168.76.40` |
| SSH | `127.0.0.1:12224` (user `korus` / `korus`) |

**Port :12224** is reserved for this VM. Sonar lab uses `:12229` (`deploy/sonar-qemu/config.ps1`). If `qemu-mobile-build-up` reports a port conflict, stop the other QEMU VM or restart Sonar on the new port.
| adb | `127.0.0.1:15037` |
| RAM | 6 GB default (`KORUS_QEMU_MOBILE_BUILD_MEMORY_MB`; min 4096 on tight hosts) |
| Disk | 48 GB overlay `images/mobile-build-dev.qcow2` |

### Самостоятельный запуск (UI на экране — Android Studio host)

```powershell
.\scripts\mobile-android-studio.ps1
```

Один вход: API + AVD-окно + APK + запуск приложения.  
Док: [`android-studio-host.md`](android-studio-host.md)

### Quick start (headless lab — QEMU guest, без окна)

```powershell
# Server API + mobile-build VM (+ optional warm emulator)
.\scripts\qemu-mobile-lab-up.ps1 -WarmEmulator

# Зависший emulator/Maestro
.\scripts\qemu-mobile-reset.ps1

# Maestro или непрерывный конвейер
.\scripts\qemu-mobile-maestro.ps1
.\scripts\qemu-mobile-conveyor.ps1 -UntilGreen -TargetWave W2
```

### Quick start (пошагово)

```powershell
# Lab API (if not already up)
.\scripts\qemu-up.ps1 -KeepDisks

# Mobile build VM (first boot: SDK ~15-45 min)
.\scripts\qemu-mobile-build-up.ps1

# Build debug APK on guest
.\scripts\qemu-mobile-build-android.ps1
.\scripts\qemu-guest-job.ps1 -Role mobile-build -JobName mobile-android-build -Loop

# Maestro W0 (needs API :18080 + APK)
.\scripts\qemu-mobile-maestro.ps1
.\scripts\qemu-guest-job.ps1 -Role mobile-build -JobName mobile-maestro -Loop -MaxMinutes 45

# Continuous conveyor (no stop between APK / emulator / Maestro / smoke):
.\scripts\qemu-mobile-conveyor.ps1 -UntilGreen -TargetWave W2
# Log: deploy/mobile/run/mobile-conveyor.log

# Or one-shot package (guest build + pull APK)
.\scripts\package-mobile-android.ps1
```

APK on guest: `/mnt/korus/deploy/mobile/run/korus-mobile-debug.apk`

Emulator in guest uses `http://10.0.2.2:18080` (host-forwarded lab API).

### Emulator speed (no extra host installs)

### Emulator speed (TCG guest — no nested KVM)

Nested KVM inside `korus-mobile-build` is **not available** on this host class (WHPX crash if enabled). The Android emulator runs in **software CPU mode** inside the guest — cold boot can take 15–30+ minutes and local snapshots often hang.

**Fast paths (in order):**

1. **Host Maestro (WHPX)** — `.\scripts\mobile-maestro-host.ps1` — Maestro on Windows host AVD (~seconds to boot).
2. **Host snapshot import** — boot once on host, export, import on guest:
   ```powershell
   .\scripts\export-host-avd-snapshot.ps1
   .\scripts\qemu-mobile-avd-snapshot.ps1 import
   ```
3. **Guest cold boot** — only when no imported snapshot; uses `-no-snapshot`, 512 MB RAM, 360×640, `advancedFeatures.ini` Vulkan=off.

Conveyor tries host Maestro first, then guest with imported snapshot.

After changing nested-KVM CPU flags, **restart** `korus-mobile-build` (`qemu-mobile-build-up.ps1 -Force`). Probe: `.\deploy\qemu\lib\Test-KorusMobileBuildNestedKvm.ps1`.

Guest uses software GPU + **AVD quickboot snapshot** (`boot`). Default AVD **API 28** (`korus_api28`, pixel_2, 768 MB). App `targetSdk 35`.

Override: `$env:KORUS_MOBILE_AVD='korus_api28'` `$env:KORUS_MOBILE_EMU_RAM_MB='768'`
Recreate AVD on guest: `$env:KORUS_MOBILE_AVD_RECREATE='1'` then sync repo and `qemu-mobile-emulator-up.ps1 -SaveSnapshot`.

```powershell
# Optional: warm emulator without Maestro (reuse between runs)
.\scripts\qemu-mobile-emulator-up.ps1 -SaveSnapshot   # once, guest job ~5-15 min
.\scripts\qemu-guest-job.ps1 -Role mobile-build -JobName mobile-emulator -Loop -MaxMinutes 25
.\scripts\qemu-mobile-emulator-up.ps1                 # ensure running

.\scripts\qemu-mobile-maestro.ps1                     # reuses warm emulator (~2-5 min)
```

Tune guest RAM: `$env:KORUS_MOBILE_EMU_RAM_MB='2048'` before `qemu-mobile-build-up.ps1`.

## iOS — macOS host (not in QEMU)

iOS/Xcode cannot run inside the Ubuntu mobile-build VM on Windows. See
[`specs/032-mobile-native-client/design/ios-build-host.md`](../../specs/032-mobile-native-client/design/ios-build-host.md).

On Mac: `.\scripts\mobile-ios-build-host-check.ps1` (after `mobile-client-ios` scaffold).
