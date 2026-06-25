#!/usr/bin/env python3
"""Add JdbcConnectionSupport.prepareRead/prepareWrite after getConnection() (FR-179)."""
from __future__ import annotations

import re
from pathlib import Path

PERSIST = Path("modules/core-api/src/main/java/com/avandocmsg/messenger/core/adapter/persistence")
CONN_IMPORT = "import com.avandocmsg.messenger.common.jdbc.JdbcConnectionSupport;"
READ_CALL = "JdbcConnectionSupport.prepareRead(conn)"
WRITE_CALL = "JdbcConnectionSupport.prepareWrite(conn)"
WRITE_KEYWORDS = re.compile(
    r"\b(INSERT|UPDATE|DELETE|UPSERT|ON CONFLICT)\b", re.IGNORECASE
)


def ensure_import(content: str) -> str:
    if CONN_IMPORT in content:
        return content
    m = re.search(r"(package [^\n]+\n\n)", content)
    if m:
        return content[: m.end()] + CONN_IMPORT + "\n" + content[m.end() :]
    return CONN_IMPORT + "\n" + content


def is_write_sql(sql_lines: list[str]) -> bool:
    blob = " ".join(sql_lines)
    return bool(WRITE_KEYWORDS.search(blob))


def patch_file(path: Path) -> bool:
    text = path.read_text(encoding="utf-8")
    if "getConnection()" not in text or "JdbcConnectionSupport" in text:
        # still patch files missing hygiene even if import exists
        pass
    lines = text.splitlines(keepends=True)
    out: list[str] = []
    changed = False
    i = 0
    while i < len(lines):
        line = lines[i]
        out.append(line)
        if re.search(r"getConnection\(\)\)", line) and "try (" in line:
            # try (var conn = dataSource.getConnection()) {
            block = [line]
            j = i + 1
            while j < len(lines) and not lines[j].strip().startswith("try ("):
                block.append(lines[j])
                if lines[j].strip() == "}":
                    break
                j += 1
            block_text = "".join(block)
            if READ_CALL in block_text or WRITE_CALL in block_text or "beginTransaction" in block_text:
                i += 1
                continue
            # peek ahead for SQL
            sql_lines: list[str] = []
            for k in range(i + 1, min(i + 15, len(lines))):
                if '"""' in lines[k] or "SELECT" in lines[k] or "INSERT" in lines[k]:
                    sql_lines.append(lines[k])
                if "prepareStatement" in lines[k]:
                    break
            write = is_write_sql(sql_lines) or any(
                kw in block_text.upper() for kw in ("INSERT", "UPDATE", "DELETE")
            )
            ind = line[: len(line) - len(line.lstrip())] + "    "
            call = WRITE_CALL if write else READ_CALL
            out.append(f"{ind}{call};\n")
            changed = True
        i += 1
    if not changed:
        return False
    new_text = ensure_import("".join(out))
    path.write_text(new_text, encoding="utf-8")
    return True


def main() -> None:
    patched = []
    for path in sorted(PERSIST.glob("Jdbc*.java")):
        if path.name in ("JdbcDialect.java", "JdbcListLimits.java"):
            continue
        if patch_file(path):
            patched.append(path.name)
    print(f"Connection hygiene patched {len(patched)} files")


if __name__ == "__main__":
    main()
