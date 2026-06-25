#!/usr/bin/env python3
"""Move applyDefaultTimeout out of try-with-resources headers into block bodies."""
from __future__ import annotations

import re
from pathlib import Path

PERSIST = Path("modules/core-api/src/main/java/com/avandocmsg/messenger/core/adapter/persistence")


def fix_invalid_try_headers(text: str) -> str:
    # Pattern: resource; applyDefaultTimeout(...); next resource
    pattern = re.compile(
        r"(try \(.*?var (\w+) = [^\n]+prepareStatement[^\n]*;\s*\n)"
        r"\s*JdbcQuerySupport\.applyDefaultTimeout\(\2\);\s*\n"
        r"(\s*(?:var \w+ = [^\n]+;\s*\n(?:\s*JdbcQuerySupport\.applyDefaultTimeout\(\w+\);\s*\n)?)?)"
        r"(\s*(?:var \w+ = [^\n]+\)\) \{|var \w+ = [^\n]+;\s*\n\s*var \w+ = [^\n]+\)\) \{))",
        re.MULTILINE | re.DOTALL,
    )

    def repl(m: re.Match[str]) -> str:
        first = m.group(1)
        var = m.group(2)
        middle = m.group(3) or ""
        closing = m.group(4)
        # strip embedded timeout lines from middle
        middle_clean = re.sub(r"\s*JdbcQuerySupport\.applyDefaultTimeout\(\w+\);\s*\n", "", middle)
        timeouts = [f"            JdbcQuerySupport.applyDefaultTimeout({var});\n"]
        for mv in re.finditer(r"var (\w+) = .*prepareStatement", middle_clean):
            timeouts.append(f"            JdbcQuerySupport.applyDefaultTimeout({mv.group(1)});\n")
        body = "".join(timeouts)
        return first + middle_clean + closing.replace("{", "{\n" + body, 1)

    prev = None
    while prev != text:
        prev = text
        text = pattern.sub(repl, text)
    return text


def fix_rs_in_header(text: str) -> str:
    """Fix: ps = prepareStatement; timeout; rs = executeQuery in try header."""
    pattern = re.compile(
        r"try \(var conn = dataSource\.getConnection\(\);\s*\n"
        r"\s*var (\w+) = conn\.prepareStatement\(([^)]+(?:\([^)]*\))?)\);\s*\n"
        r"\s*JdbcQuerySupport\.applyDefaultTimeout\(\1\);\s*\n"
        r"\s*var rs = \1\.executeQuery\(\)\) \{",
        re.MULTILINE,
    )

    def repl(m: re.Match[str]) -> str:
        var, sql_arg = m.group(1), m.group(2)
        return (
            f"try (var conn = dataSource.getConnection();\n"
            f"             var {var} = conn.prepareStatement({sql_arg})) {{\n"
            f"            JdbcQuerySupport.applyDefaultTimeout({var});\n"
            f"            try (var rs = {var}.executeQuery()) {{"
        )

    return pattern.sub(repl, text)


def main() -> None:
    for path in sorted(PERSIST.glob("Jdbc*.java")):
        orig = path.read_text(encoding="utf-8")
        text = fix_rs_in_header(orig)
        if text != orig:
            path.write_text(text, encoding="utf-8")
            print(f"fixed rs-header: {path.name}")


if __name__ == "__main__":
    main()
