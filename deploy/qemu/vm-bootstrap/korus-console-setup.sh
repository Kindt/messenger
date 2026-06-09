#!/bin/sh
# Show bootstrap progress on VGA (GTK window) instead of login prompt on tty1.
set -eu

ROLE="${1:?usage: korus-console-setup.sh server|web}"
LOG=/var/log/korus-bootstrap.log

touch "$LOG"
chmod 644 "$LOG" 2>/dev/null || true

case "$ROLE" in
  server) API_HINT="host :18080 / guest :8080" ;;
  web) API_HINT="host :19088 / guest :9088" ;;
  *) API_HINT="see README" ;;
esac

WRAPPER="/usr/local/bin/korus-console-tail.sh"
cat > "$WRAPPER" << 'WRAPPER_EOF'
#!/bin/sh
LOG=/var/log/korus-bootstrap.log
touch "$LOG"
# Show only the latest bootstrap session (skip stale PLAY RECAP / fatal from prior runs).
while true; do
  marker=$(grep -n '^=== run-ansible-local.sh' "$LOG" 2>/dev/null | tail -1 | cut -d: -f1)
  if [ -z "$marker" ]; then
    marker=$(wc -l <"$LOG" 2>/dev/null || echo 1)
    [ "$marker" -gt 80 ] && marker=$((marker - 79)) || marker=1
  fi
  tail -n +"$marker" -F "$LOG" 2>/dev/null | while IFS= read -r line; do
    printf '%s\n' "$line" > /dev/tty1 2>/dev/null || true
  done
  sleep 2
done
WRAPPER_EOF
chmod 755 "$WRAPPER"

cat > /etc/systemd/system/korus-console-tail.service << EOF
[Unit]
Description=Korus bootstrap monitor on VGA (${ROLE})
Conflicts=getty@tty1.service
Before=getty@tty1.service
After=network-online.target

[Service]
Type=simple
ExecStartPre=-/bin/systemctl stop getty@tty1.service
ExecStartPre=/bin/sh -c 'chvt 1 2>/dev/null || true; printf "\\033[2J\\033[H\\033[1;36m=== Korus ${ROLE} — docker compose / Gradle build ===\\033[0m\\n\\033[90m${API_HINT}\\033[0m\\n\\033[90mLog: /var/log/korus-bootstrap.log\\033[0m\\n\\n" > /dev/tty1'
ExecStart=${WRAPPER}
Restart=always
RestartSec=3
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl unmask getty@tty1.service 2>/dev/null || true
systemctl mask getty@tty1.service
systemctl enable korus-console-tail.service
systemctl stop korus-console-tail.service 2>/dev/null || true
systemctl start --no-block korus-console-tail.service
sleep 1

STATUS=$(systemctl is-active korus-console-tail.service 2>&1 || true)
printf '%s korus-console-setup: korus-console-tail status=%s\n' "$(date -Iseconds)" "$STATUS" >>"$LOG"
if [ "$STATUS" != "active" ]; then
  systemctl status korus-console-tail.service >>"$LOG" 2>&1 || true
  journalctl -u korus-console-tail.service -n 15 --no-pager >>"$LOG" 2>&1 || true
fi

if [ -f /mnt/korus/deploy/qemu/vm-bootstrap/korus-clean-bootstrap-log.sh ]; then
  sh /mnt/korus/deploy/qemu/vm-bootstrap/korus-clean-bootstrap-log.sh 2>/dev/null || true
fi

printf '\n%s korus-console-setup: VGA monitor started for %s\n' "$(date -Iseconds)" "$ROLE" >>"$LOG"
# Immediate visible feedback on VGA
printf '>>> bootstrap monitor active — waiting for ansible/docker output...\n' > /dev/tty1 2>/dev/null || true
