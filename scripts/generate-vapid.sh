#!/usr/bin/env bash
# Generate VAPID keys for Web Push (web-client + push-worker).
set -euo pipefail
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"
ENV_SNIPPET="${1:-}"

echo "Generating VAPID keys (web-push)..."
out="$(npx --yes web-push generate-vapid-keys 2>&1)" || {
  echo "npx web-push failed. Install Node.js." >&2
  exit 1
}
echo "$out"

public="$(echo "$out" | sed -n 's/^Public Key:[[:space:]]*//p' | head -1)"
private="$(echo "$out" | sed -n 's/^Private Key:[[:space:]]*//p' | head -1)"
if [[ -z "$public" || -z "$private" ]]; then
  echo "Warning: could not parse keys; copy manually from output above." >&2
  exit 0
fi

snippet="# Web Push VAPID (same public key on web-client and push-worker)
WEB_CLIENT_VAPID_PUBLIC_KEY=${public}
PUSH_VAPID_PUBLIC_KEY=${public}
PUSH_VAPID_PRIVATE_KEY=${private}
PUSH_VAPID_SUBJECT=mailto:notify@localhost"

echo ""
echo "Suggested env:"
echo "$snippet"

if [[ -n "$ENV_SNIPPET" ]]; then
  printf '%s\n' "$snippet" >"$ENV_SNIPPET"
  echo "Wrote $ENV_SNIPPET"
fi
