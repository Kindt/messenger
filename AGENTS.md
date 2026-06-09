<!-- SPECKIT START -->
Current plan: `specs/001-system-review-refactoring/plan.md`
For additional context about technologies to be used, project structure,
shell commands, and other important information, read the current plan
<!-- SPECKIT END -->

## Dev runtime (QEMU only)

On the **Windows host**, do not run Docker, Ansible deploy, or compose stacks locally.
Use the two QEMU guests (`korus-server` / `korus-web`); see `.cursor/rules/qemu-host-isolation.mdc` and `deploy/qemu/README.md`.

Quick start: `.\scripts\qemu-dev-up.ps1` (graphical VMs + monitor) → API http://127.0.0.1:18080, UI http://127.0.0.1:19088
Headless: `.\scripts\qemu-up.ps1` — see `deploy/qemu/README.md`
