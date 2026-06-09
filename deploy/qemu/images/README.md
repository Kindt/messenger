# QEMU OS images (not in git)

Cloud images and overlay disks are **local artifacts** — they are not committed to the repository.

| File | Source | How to obtain |
|------|--------|---------------|
| `ubuntu-24.04-minimal-cloudimg-amd64.img` | [Ubuntu minimal noble](https://cloud-images.ubuntu.com/minimal/releases/noble/release/) | Automatic on first `qemu-up` via `Get-KorusCloudImage`, or manually: `.\scripts\ensure-qemu-images.ps1` |
| `server.qcow2`, `web.qcow2` | overlay on base `.img` | Created automatically by `New-KorusVmOverlayDisk` when VMs start |

## Manual download

From repo root:

```powershell
.\scripts\ensure-qemu-images.ps1
```

Or start the stack (download runs if the base image is missing):

```powershell
.\scripts\qemu-up.ps1
```

URL and path are defined in [`deploy/qemu/config.ps1`](../config.ps1) (`KorusQemuCloudImageUrl`, `KorusQemuCloudImage`).
