#!/usr/bin/env python3
"""Generate /web-client-env.js — parity with WebClientEnvServlet (spec 021 Phase 7.4)."""

from __future__ import annotations

import argparse
import os
import sys
from pathlib import Path


def json_quote(value: str) -> str:
    return '"' + value.replace("\\", "\\\\").replace('"', '\\"') + '"'


def env_or_default(getenv, key: str, fallback: str) -> str:
    raw = getenv(key)
    if raw is None:
        return fallback
    return raw.strip()


def env_flag(getenv, key: str) -> bool:
    raw = getenv(key)
    if raw is None:
        return False
    trimmed = raw.strip()
    return trimmed == "1" or trimmed.lower() == "true"


def build_env_script_body(getenv=os.getenv) -> str:
    ws_url = env_or_default(getenv, "WEB_CLIENT_WS_PUBLIC_URL", "ws://127.0.0.1:8081/ws").rstrip("/")
    ice_raw = env_or_default(getenv, "WEB_CLIENT_RTC_ICE_SERVERS", "")
    ice_js = "null" if not ice_raw else json_quote(ice_raw)
    vapid_raw = env_or_default(getenv, "WEB_CLIENT_VAPID_PUBLIC_KEY", "")
    vapid_js = "null" if not vapid_raw else json_quote(vapid_raw)
    disable_sw = env_flag(getenv, "WEB_CLIENT_DISABLE_SW")
    return (
        "window.__WEB_CLIENT__ = { wsUrl: "
        + json_quote(ws_url)
        + ", iceServersJson: "
        + ice_js
        + ", vapidPublicKey: "
        + vapid_js
        + ", disableServiceWorker: "
        + ("true" if disable_sw else "false")
        + " };\n"
    )


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate web-client-env.js")
    parser.add_argument(
        "-o",
        "--output",
        type=Path,
        default=Path("-"),
        help="Output file (default stdout)",
    )
    args = parser.parse_args()
    body = build_env_script_body()
    if str(args.output) == "-":
        sys.stdout.write(body)
    else:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(body, encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
