#!/bin/sh
# Remove VGA noise from failed docker-logs follower (not real build errors).
LOG=/var/log/korus-bootstrap.log
[ -f "$LOG" ] || exit 0
tmp="${LOG}.clean.$$"
grep -v 'configured logging driver does not support reading' "$LOG" \
  | grep -v '^--- docker logs ' \
  | grep -v '^--- build status ' \
  | grep -v '^| .* | Error response from daemon' \
  >"$tmp" && cat "$tmp" >"$LOG" && rm -f "$tmp"
