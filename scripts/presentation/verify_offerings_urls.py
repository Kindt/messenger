#!/usr/bin/env python3
"""Spot-check HTTPS source_url rows in competitor_offerings (METRIC_POLICY)."""

from __future__ import annotations

import sys
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from scripts.presentation.data_loader import load_offerings

# Docs/bot-wall URLs: format OK, live GET may 403 outside browser — skip network probe.
SKIP_NETWORK_PREFIXES = (
    "https://github.com/",
    "https://biz.mail.ru/docs/",
    "https://mattermost.com/",
    "https://www.rocket.chat/",
)

STALE_PATH_FRAGMENTS = (
    "express.ms/pricing",
    "pachca.ru/pricing",
    "getcompass.ru/pricing",
)


def stale_path_errors() -> list[str]:
    errors: list[str] = []
    for o in load_offerings():
        url = o["source_url"]
        for frag in STALE_PATH_FRAGMENTS:
            if frag in url:
                errors.append(f"{o['id']}: stale source_url {url!r}")
    return errors


def network_errors(timeout: float = 20.0) -> list[str]:
    errors: list[str] = []
    seen: set[str] = set()
    for o in load_offerings():
        url = o["source_url"]
        if url in seen:
            continue
        seen.add(url)
        if any(url.startswith(p) for p in SKIP_NETWORK_PREFIXES):
            continue
        req = Request(url, method="GET", headers={"User-Agent": "KorusDeckVerify/1.0"})
        try:
            with urlopen(req, timeout=timeout) as resp:
                if resp.status >= 400:
                    errors.append(f"{url} -> HTTP {resp.status}")
        except HTTPError as exc:
            if exc.code >= 400:
                errors.append(f"{url} -> HTTP {exc.code}")
        except URLError as exc:
            errors.append(f"{url} -> {exc.reason}")
    return errors


def main() -> int:
    stale = stale_path_errors()
    if stale:
        for line in stale:
            print(line, file=sys.stderr)
        return 1

    net = network_errors()
    if net:
        for line in net:
            print(line, file=sys.stderr)
        return 1

    print("verify_offerings_urls: OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
