#!/usr/bin/env bash
# Проверка окружения (Unix). На Windows: scripts\install-environment.ps1
# Тихая установка (Debian/Ubuntu): ./scripts/install-env-silent.sh
set -euo pipefail

SILENT_INSTALL=0
QUIET=0
for arg in "$@"; do
  if [[ "$arg" == "--silent-install" ]] || [[ "$arg" == "-s" ]]; then
    SILENT_INSTALL=1
  fi
  if [[ "$arg" == "--quiet" ]] || [[ "$arg" == "-q" ]]; then
    QUIET=1
  fi
done

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ -f "$ROOT/scripts/lib/korus-env.sh" ]]; then
  # shellcheck source=lib/korus-env.sh
  source "$ROOT/scripts/lib/korus-env.sh"
  korus_set_path_env "$ROOT"
fi

if [[ "$SILENT_INSTALL" == "1" ]]; then
  if [[ "$QUIET" == "1" ]]; then
    "$ROOT/scripts/install-env-silent.sh" --quiet
  else
    "$ROOT/scripts/install-env-silent.sh"
  fi
  hash -r 2>/dev/null || true
fi

if [[ "$QUIET" != "1" ]]; then
  echo "=== Korus Messenger / AvandocMsg — проверка окружения ==="
fi

command -v docker >/dev/null 2>&1 || {
  echo "Docker не найден. Установите: $ROOT/scripts/install-env-silent.sh или см. https://docs.docker.com/get-docker/" >&2
  exit 1
}
command -v java >/dev/null 2>&1 || {
  echo "Java не найдена. Установите: $ROOT/scripts/install-env-silent.sh" >&2
  exit 1
}

LINE="$(java -version 2>&1 | head -1)"
JAVA_VER=0
if [[ "$LINE" =~ version\ \"1\.([0-9]+) ]]; then
  JAVA_VER="${BASH_REMATCH[1]}"
elif [[ "$LINE" =~ version\ \"([0-9]+) ]]; then
  JAVA_VER="${BASH_REMATCH[1]}"
fi
if [[ "$JAVA_VER" -lt 17 ]]; then
  echo "Требуется Java 17+, обнаружено: $LINE" >&2
  exit 1
fi

if [[ "$QUIET" != "1" ]]; then
  echo "Docker: OK"
  echo "Java: OK ($LINE)"
fi

if [[ ! -f "$ROOT/gradlew" ]]; then
  echo "Предупреждение: нет $ROOT/gradlew — клонируйте репозиторий с Gradle wrapper или выполните: gradle wrapper" >&2
else
  if [[ "$QUIET" != "1" ]]; then
    echo "Gradle wrapper: OK"
  fi
fi

if [[ "$QUIET" == "1" ]]; then
  echo "[OK] environment check (docker, java, gradle wrapper)"
else
  echo "=== Проверка завершена ==="
fi
