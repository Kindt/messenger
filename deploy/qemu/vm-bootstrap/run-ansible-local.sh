#!/bin/sh
# QEMU guest bootstrap via Ansible (spec 003). Called from cloud-init or qemu-redeploy.
# Usage: run-ansible-local.sh server|web
set -eu

ROLE="${1:?usage: run-ansible-local.sh server|web|integrations}"
REPO="${KORUS_REPO_ROOT:-/mnt/korus}"
ANSIBLE_DIR="$REPO/deploy/ansible"
LOG="${KORUS_BOOTSTRAP_LOG:-/var/log/korus-bootstrap.log}"

if ! { : >>"$LOG"; } 2>/dev/null; then
  LOG="${TMPDIR:-/tmp}/korus-bootstrap.log"
fi
HOST_GW="${KORUS_QEMU_HOST_GW:-10.0.2.2}"
BUILD="${KORUS_BUILD:-0}"

if [ "${KORUS_TRUNCATE_BOOTSTRAP_LOG:-0}" = "1" ]; then
  : >"$LOG" 2>/dev/null || true
fi

exec >>"$LOG" 2>&1
echo "=== run-ansible-local.sh $ROLE $(date -Iseconds) repo=$REPO build=$BUILD ==="

if [ -f "$REPO/deploy/qemu/vm-bootstrap/korus-guest-deps.sh" ]; then
  sh "$REPO/deploy/qemu/vm-bootstrap/korus-guest-deps.sh"
fi

if [ -f "$REPO/deploy/qemu/vm-bootstrap/korus-docker-image-load.sh" ]; then
  cache_url="http://${HOST_GW}:${KORUS_QEMU_REPO_HTTP_PORT:-18890}/docker-base-images.tar"
  cache_code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "$cache_url" 2>/dev/null || echo 000)
  if [ "$cache_code" = "404" ] || [ "$cache_code" = "000" ]; then
    echo "korus-docker-image-load: skip (host tar HTTP $cache_code)"
  else
    KORUS_DOCKER_CACHE_ATTEMPTS="${KORUS_DOCKER_CACHE_ATTEMPTS:-24}" \
      sh "$REPO/deploy/qemu/vm-bootstrap/korus-docker-image-load.sh" || true
  fi
fi

wait_repo() {
  local marker="$1"
  local i
  for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20; do
    if [ -f "$REPO/$marker" ]; then
      return 0
    fi
    echo "waiting for repo at $REPO ($marker)..."
    sleep 3
  done
  echo "ERROR: repo not ready at $REPO ($marker missing)"
  exit 1
}

ansible_env() {
  # ansible.cfg is ignored on world-writable /mnt/korus; set paths explicitly.
  export ANSIBLE_ROLES_PATH="$ANSIBLE_DIR/roles"
  export ANSIBLE_STDOUT_CALLBACK=default
  export ANSIBLE_CONFIG="${ANSIBLE_CONFIG:-/tmp/korus-ansible.cfg}"
  cp "$ANSIBLE_DIR/ansible.cfg" "$ANSIBLE_CONFIG" 2>/dev/null || true
  chmod 644 "$ANSIBLE_CONFIG" 2>/dev/null || true
  if [ -f "$REPO/deploy/qemu/vm-bootstrap/korus-plain-build-env.sh" ]; then
    # shellcheck source=/dev/null
    . "$REPO/deploy/qemu/vm-bootstrap/korus-plain-build-env.sh"
  fi
}

ensure_guest_packages() {
  if [ -f "$REPO/deploy/qemu/vm-bootstrap/korus-guest-deps.sh" ]; then
    sh "$REPO/deploy/qemu/vm-bootstrap/korus-guest-deps.sh"
    return 0
  fi
  echo "WARN: korus-guest-deps.sh missing; install python3-pip/docker manually"
}

pip_install() {
  if command -v pip3 >/dev/null 2>&1; then
    pip3 "$@"
  else
    python3 -m pip "$@"
  fi
}

ensure_ansible() {
  if [ -f "$REPO/deploy/qemu/vm-bootstrap/korus-guest-deps.sh" ]; then
    sh "$REPO/deploy/qemu/vm-bootstrap/korus-guest-deps.sh"
  else
    ensure_guest_packages
  fi
  if command -v ansible-playbook >/dev/null 2>&1; then
    return 0
  fi
  echo "installing ansible via pip..."
  # #region agent log
  echo "ensure_ansible: pip3=$(command -v pip3 2>/dev/null || echo missing) python3=$(command -v python3 2>/dev/null || echo missing)"
  # #endregion
  if pip_install install --break-system-packages ansible websocket-client 2>/dev/null; then
    :
  elif pip_install install --user ansible websocket-client; then
    export PATH="$HOME/.local/bin:$PATH"
  else
    echo "ERROR: pip install ansible failed"
    exit 1
  fi
  command -v ansible-playbook >/dev/null 2>&1 || export PATH="$HOME/.local/bin:/usr/local/bin:$PATH"
}

normalize_scripts() {
  if [ -d "$REPO/scripts" ]; then
    sed -i 's/\r$//' "$REPO"/scripts/*.sh "$REPO"/scripts/lib/*.sh 2>/dev/null || true
    chmod +x "$REPO"/scripts/*.sh "$REPO"/scripts/lib/*.sh 2>/dev/null || true
  fi
  sed -i 's/\r$//' "$REPO"/deploy/qemu/vm-bootstrap/*.sh 2>/dev/null || true
  chmod +x "$REPO"/deploy/qemu/vm-bootstrap/*.sh 2>/dev/null || true
}

wait_server_api() {
  # First server docker compose build on QEMU can take 30–45 min; old loop (~7 min) caused web to fail early.
  local max_sec="${KORUS_WAIT_SERVER_SEC:-5400}"
  local elapsed=0
  local url
  while [ "$elapsed" -lt "$max_sec" ]; do
    for url in \
      "http://${KORUS_QEMU_SERVER_IP:-192.168.76.10}:8080/api/v1/health" \
      "http://${HOST_GW}:18080/api/v1/health"; do
      if curl -fsS --max-time 5 "$url" >/dev/null 2>&1; then
        echo "server API ready: $url (after ${elapsed}s)"
        return 0
      fi
    done
    echo "waiting for server API (${elapsed}s / ${max_sec}s)..."
    sleep 10
    elapsed=$((elapsed + 10))
  done
  # #region agent log
  echo "wait_server_api: TIMEOUT after ${max_sec}s"
  # #endregion
  echo "ERROR: server API not reachable from web guest (waited ${max_sec}s)"
  exit 1
}

fetch_host_lan_ip() {
  local ip=""
  local i
  for i in 1 2 3 4 5 6; do
    ip="$(curl -fsS --max-time 5 "http://${HOST_GW}:${KORUS_QEMU_REPO_HTTP_PORT:-18890}/host-lan-ip.txt" 2>/dev/null \
      | tr -d '\r\n' || true)"
    if [ -n "$ip" ]; then
      echo "$ip"
      return 0
    fi
    echo "waiting for host-lan-ip.txt from repo HTTP (${i}/6)..."
    sleep 3
  done
  echo ""
}

case "$ROLE" in
  server)
    wait_repo "docker/docker-compose.full-server.yml"
    normalize_scripts
    ensure_ansible
    ansible_env
    cd "$ANSIBLE_DIR"
    extra=""
    if [ "$BUILD" = "1" ]; then
      extra="-e korus_build_images=true"
    else
      extra="-e korus_build_images=false"
    fi
    # shellcheck disable=SC2086
    ansible-playbook -i inventory/qemu/localhost.yml playbooks/qemu-server-local.yml \
      -e "korus_repo_root=$REPO" $extra
    echo "=== QEMU server ansible deploy done ==="
    ;;
  web)
    wait_repo "korus-web/docker-compose.yml"
    wait_server_api
    normalize_scripts
    ensure_ansible
    ansible_env
    LAN_IP="$(fetch_host_lan_ip)"
    if [ -z "$LAN_IP" ]; then
      LAN_IP="127.0.0.1"
    fi
    echo "browser WS host (Windows LAN): $LAN_IP"
    cd "$ANSIBLE_DIR"
    extra=""
    if [ "$BUILD" = "1" ]; then
      extra="-e korus_build_images=true"
    else
      extra="-e korus_build_images=false"
    fi
    # shellcheck disable=SC2086
    ansible-playbook -i inventory/qemu/localhost.yml playbooks/qemu-web-local.yml \
      -e "korus_repo_root=$REPO" \
      -e "korus_qemu_host_lan_ip=$LAN_IP" $extra
    echo "=== QEMU web ansible deploy done ==="
    ;;
  integrations)
    wait_repo "integrations/docker-compose.integrations.yml"
    normalize_scripts
    ensure_ansible
    ansible_env
    cd "$ANSIBLE_DIR"
    extra=""
    if [ "$BUILD" = "1" ]; then
      extra="-e korus_build_images=true"
    else
      extra="-e korus_build_images=false"
    fi
    # shellcheck disable=SC2086
    ansible-playbook -i inventory/qemu/localhost.yml playbooks/qemu-integrations-local.yml \
      -e "korus_repo_root=$REPO" $extra
    echo "=== QEMU integrations ansible deploy done ==="
    ;;
  *)
    echo "ERROR: unknown role $ROLE"
    exit 2
    ;;
esac
