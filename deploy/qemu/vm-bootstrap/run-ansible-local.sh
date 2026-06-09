#!/bin/sh
# QEMU guest bootstrap via Ansible (spec 003). Called from cloud-init or qemu-redeploy.
# Usage: run-ansible-local.sh server|web
set -eu

ROLE="${1:?usage: run-ansible-local.sh server|web}"
REPO="${KORUS_REPO_ROOT:-/mnt/korus}"
ANSIBLE_DIR="$REPO/deploy/ansible"
LOG="${KORUS_BOOTSTRAP_LOG:-/var/log/korus-bootstrap.log}"
DEBUG_INGEST="${KORUS_DEBUG_INGEST:-http://10.0.2.2:7900/ingest/8837d83b-b660-47bd-8b4a-b8fceb452d46}"

debug_agent_log() {
  _hyp="$1"
  _msg="$2"
  _ts="$(($(date +%s) * 1000))"
  echo "DEBUG[$_hyp] $_msg"
  curl -fsS -m 2 -X POST "$DEBUG_INGEST" \
    -H "Content-Type: application/json" \
    -H "X-Debug-Session-Id: baea72" \
    -d "{\"sessionId\":\"baea72\",\"hypothesisId\":\"$_hyp\",\"location\":\"run-ansible-local.sh\",\"message\":\"$_msg\",\"timestamp\":$_ts,\"runId\":\"clean-build\",\"data\":{\"role\":\"$ROLE\",\"build\":\"$BUILD\"}}" \
    2>/dev/null || true
}

if ! { : >>"$LOG"; } 2>/dev/null; then
  LOG="${TMPDIR:-/tmp}/korus-bootstrap.log"
fi
HOST_GW="${KORUS_QEMU_HOST_GW:-10.0.2.2}"
BUILD="${KORUS_BUILD:-0}"

exec >>"$LOG" 2>&1
echo "=== run-ansible-local.sh $ROLE $(date -Iseconds) repo=$REPO build=$BUILD ==="
debug_agent_log "ALL" "ansible bootstrap start role=$ROLE build=$BUILD"

wait_repo() {
  local marker="$1"
  local i
  for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20; do
    if [ -f "$REPO/$marker" ]; then
      debug_agent_log "A" "repo marker found: $marker"
      return 0
    fi
    echo "waiting for repo at $REPO ($marker)..."
    sleep 3
  done
  echo "ERROR: repo not ready at $REPO ($marker missing)"
  debug_agent_log "A" "repo not ready marker=$marker"
  exit 1
}

ansible_env() {
  # ansible.cfg is ignored on world-writable /mnt/korus; set paths explicitly.
  export ANSIBLE_ROLES_PATH="$ANSIBLE_DIR/roles"
  export ANSIBLE_STDOUT_CALLBACK=default
  export ANSIBLE_CONFIG="${ANSIBLE_CONFIG:-/tmp/korus-ansible.cfg}"
  cp "$ANSIBLE_DIR/ansible.cfg" "$ANSIBLE_CONFIG" 2>/dev/null || true
  chmod 644 "$ANSIBLE_CONFIG" 2>/dev/null || true
}

ensure_ansible() {
  if command -v ansible-playbook >/dev/null 2>&1; then
    return 0
  fi
  echo "installing ansible via pip..."
  if pip3 install --break-system-packages ansible websocket-client 2>/dev/null; then
    :
  elif pip3 install --user ansible websocket-client; then
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
  local url
  for url in \
    "http://${KORUS_QEMU_SERVER_IP:-192.168.76.10}:8080/api/v1/health" \
    "http://${HOST_GW}:18080/api/v1/health"; do
    local i
    for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 24 30; do
      if curl -fsS --max-time 5 "$url" >/dev/null 2>&1; then
        echo "server API ready: $url"
        return 0
      fi
      echo "waiting for server API at $url..."
      sleep 10
    done
  done
  echo "ERROR: server API not reachable from web guest"
  exit 1
}

fetch_host_lan_ip() {
  curl -fsS --max-time 5 "http://${HOST_GW}:${KORUS_QEMU_REPO_HTTP_PORT:-18890}/host-lan-ip.txt" 2>/dev/null \
    | tr -d '\r\n' || true
}

case "$ROLE" in
  server)
    debug_agent_log "A" "server wait_repo begin"
    wait_repo "docker/docker-compose.full-server.yml"
    debug_agent_log "A" "server repo ready"
    normalize_scripts
    ensure_ansible
    ansible_env
    cd "$ANSIBLE_DIR"
    extra=""
    if [ "$BUILD" = "1" ]; then
      extra="-e korus_build_images=true"
      debug_agent_log "B" "server full build KORUS_BUILD=1 gradle+docker"
    else
      extra="-e korus_build_images=false"
    fi
    debug_agent_log "E" "server ansible-playbook start"
    # shellcheck disable=SC2086
    ansible-playbook -i inventory/qemu/localhost.yml playbooks/qemu-server-local.yml \
      -e "korus_repo_root=$REPO" $extra
    debug_agent_log "E" "server ansible-playbook done exit=$?"
    echo "=== QEMU server ansible deploy done ==="
    ;;
  web)
    debug_agent_log "D" "web wait_repo begin"
    wait_repo "korus-web/docker-compose.yml"
    debug_agent_log "D" "web wait_server_api begin"
    wait_server_api
    debug_agent_log "D" "web server API ready"
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
    debug_agent_log "E" "web ansible-playbook start"
    # shellcheck disable=SC2086
    ansible-playbook -i inventory/qemu/localhost.yml playbooks/qemu-web-local.yml \
      -e "korus_repo_root=$REPO" \
      -e "korus_qemu_host_lan_ip=$LAN_IP" $extra
    debug_agent_log "E" "web ansible-playbook done exit=$?"
    echo "=== QEMU web ansible deploy done ==="
    ;;
  *)
    echo "ERROR: unknown role $ROLE"
    exit 2
    ;;
esac
