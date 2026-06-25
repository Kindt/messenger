#!/usr/bin/env python3
"""Insert JdbcQuerySupport.applyDefaultTimeout(stmt) after prepareStatement blocks (FR-069)."""
from __future__ import annotations

import re
from pathlib import Path

PERSIST = Path("modules/core-api/src/main/java/com/avandocmsg/messenger/core/adapter/persistence")
TIMEOUT_IMPORT = "import com.avandocmsg.messenger.common.jdbc.JdbcQuerySupport;"
CONN_IMPORT = "import com.avandocmsg.messenger.common.jdbc.JdbcConnectionSupport;"
TIMEOUT_CALL = "JdbcQuerySupport.applyDefaultTimeout(stmt)"
TIMEOUT_PATTERNS = (
    "applyDefaultTimeout",
    "applyTimeout(",
    "applyQueryTimeout(",
)


def needs_timeout_after(lines: list[str], idx: int) -> bool:
    line = lines[idx]
    if "prepareStatement" not in line:
        return False
    # Look ahead a few lines for existing timeout call before execute/setObject
    for j in range(idx + 1, min(idx + 6, len(lines))):
        peek = lines[j]
        if any(p in peek for p in TIMEOUT_PATTERNS):
            return False
        if re.search(r"\.execute(Query|Update|\()", peek):
            return True
        if "stmt.set" in peek or "ps.set" in peek:
            return True
    return "{" in line


def indent_of(line: str) -> str:
    return line[: len(line) - len(line.lstrip())]


def ensure_import(content: str, import_line: str) -> str:
    if import_line in content:
        return content
    m = re.search(r"(package [^\n]+\n\n)", content)
    if m:
        return content[: m.end()] + import_line + "\n" + content[m.end() :]
    return import_line + "\n" + content


def patch_file(path: Path) -> bool:
    text = path.read_text(encoding="utf-8")
    if "prepareStatement" not in text:
        return False
    lines = text.splitlines(keepends=True)
    out: list[str] = []
    changed = False
    i = 0
    while i < len(lines):
        line = lines[i]
        out.append(line)
        if needs_timeout_after(lines, i):
            ind = indent_of(line) + "    "
            out.append(f"{ind}{TIMEOUT_CALL};\n")
            changed = True
        i += 1
    if not changed:
        return False
    new_text = "".join(out)
    new_text = ensure_import(new_text, TIMEOUT_IMPORT)
    path.write_text(new_text, encoding="utf-8")
    return True


def main() -> None:
    patched = []
    for path in sorted(PERSIST.glob("Jdbc*.java")):
        if path.name in ("JdbcDialect.java", "JdbcListLimits.java"):
            continue
        if patch_file(path):
            patched.append(path.name)
    print(f"Patched {len(patched)} files:")
    for name in patched:
        print(f"  {name}")


if __name__ == "__main__":
    main()
