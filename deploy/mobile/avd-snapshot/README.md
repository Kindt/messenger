# Optional quickboot snapshot for Maestro on `korus-mobile-build` (TCG guest, no nested KVM).

Place tarball here:

- `korus_api28-boot.tgz` — contents: `boot/` folder from `~/.android/avd/korus_api28.avd/snapshots/`

## Fast path (recommended)

Boot once on **host** Android Studio AVD (WHPX), export, import on guest:

```powershell
.\scripts\export-host-avd-snapshot.ps1
.\scripts\qemu-mobile-avd-snapshot.ps1 import   # SCP full tarball to guest (9p mount truncates large files)
.\scripts\qemu-mobile-emulator-up.ps1
```

Guest TCG with imported snapshot: **~15 s** quickboot vs **15–30+ min** cold boot / hang.

Import:

```powershell
.\scripts\qemu-mobile-avd-snapshot.ps1 import
.\scripts\qemu-mobile-emulator-up.ps1
```

Export after successful cold boot + snapshot on guest:

```powershell
.\scripts\qemu-mobile-avd-snapshot.ps1 export
```

Generate once on a machine with `/dev/kvm` or allow ~60 min cold boot on TCG guest.

**Note:** Host Korus QEMU stays WHPX (`qemu-whpx-required.mdc`). Nested `vmx=on` for mobile-build crashes WHPX on this host class; fast path is **quickboot snapshot**, not nested KVM.
