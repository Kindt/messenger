#!/bin/sh
# Idempotent apt deps for QEMU guests (pip, docker). Root or passwordless sudo.
set -eu

LOG="${KORUS_BOOTSTRAP_LOG:-/var/log/korus-bootstrap.log}"
APT_LOG=/var/log/korus-apt.log
STAMP_DIR=/var/lib/korus
touch "$LOG" 2>/dev/null || true
mkdir -p "$STAMP_DIR" 2>/dev/null || true

log() {
  echo "$1" >>"$LOG" 2>/dev/null || true
}

run_apt() {
  if [ "$(id -u)" -eq 0 ]; then
    "$@"
  else
    sudo "$@"
  fi
}

enable_universe() {
  if grep -rq 'universe' /etc/apt/sources.list /etc/apt/sources.list.d/ 2>/dev/null; then
    return 0
  fi
  log "korus-guest-deps: enabling universe apt component"
  if [ -f /etc/apt/sources.list.d/ubuntu.sources ]; then
    sed -i 's/^Components: main$/Components: main universe restricted multiverse/' \
      /etc/apt/sources.list.d/ubuntu.sources 2>/dev/null || true
  fi
  if command -v add-apt-repository >/dev/null 2>&1; then
    run_apt add-apt-repository -y universe >>"$APT_LOG" 2>&1 || true
  fi
}

bootstrap_pip() {
  if command -v pip3 >/dev/null 2>&1 || python3 -m pip --version >/dev/null 2>&1; then
    return 0
  fi
  if [ -f "$STAMP_DIR/pip-bootstrap-tried" ]; then
    return 1
  fi
  touch "$STAMP_DIR/pip-bootstrap-tried"
  log "korus-guest-deps: bootstrapping pip via get-pip.py"
  if curl -fsSL -o /tmp/get-pip.py https://bootstrap.pypa.io/get-pip.py >>"$APT_LOG" 2>&1 \
    && python3 /tmp/get-pip.py --break-system-packages >>"$APT_LOG" 2>&1; then
    return 0
  fi
  python3 /tmp/get-pip.py --user >>"$APT_LOG" 2>&1 || return 1
  export PATH="$HOME/.local/bin:$PATH"
}

install_pip_apt() {
  if command -v pip3 >/dev/null 2>&1 || python3 -m pip --version >/dev/null 2>&1; then
    return 0
  fi
  if [ -f "$STAMP_DIR/pip-apt-tried" ]; then
    return 1
  fi
  touch "$STAMP_DIR/pip-apt-tried"
  log "korus-guest-deps: installing python3-pip via apt"
  run_apt apt-get install -y python3-pip >>"$APT_LOG" 2>&1
}

install_docker_apt() {
  if command -v docker >/dev/null 2>&1; then
    return 0
  fi
  if [ -f "$STAMP_DIR/docker-apt-tried" ]; then
    log "korus-guest-deps: docker still missing (apt already tried; details in $APT_LOG)"
    return 1
  fi
  touch "$STAMP_DIR/docker-apt-tried"
  log "korus-guest-deps: installing docker.io via apt"
  if run_apt apt-get install -y docker.io docker-compose-v2 >>"$APT_LOG" 2>&1; then
    run_apt systemctl enable --now docker 2>/dev/null || true
    log "korus-guest-deps: docker installed"
    return 0
  fi
  log "korus-guest-deps: ERROR docker apt failed (minimal cloud image lacks deps; use server cloudimg — see $APT_LOG)"
  tail -n 8 "$APT_LOG" >>"$LOG" 2>/dev/null || true
  return 1
}

log "=== korus-guest-deps.sh $(date -Iseconds) ==="
export DEBIAN_FRONTEND=noninteractive
enable_universe
run_apt apt-get update -qq >>"$APT_LOG" 2>&1

if ! install_pip_apt; then
  bootstrap_pip || true
fi
if ! command -v pip3 >/dev/null 2>&1 && ! python3 -m pip --version >/dev/null 2>&1; then
  bootstrap_pip || true
fi

install_docker_apt || true

log "korus-guest-deps: pip3=$(command -v pip3 2>/dev/null || echo missing) docker=$(command -v docker 2>/dev/null || echo missing)"

if ! command -v pip3 >/dev/null 2>&1 && ! python3 -m pip --version >/dev/null 2>&1; then
  log "korus-guest-deps: ERROR pip still unavailable"
  exit 1
fi
