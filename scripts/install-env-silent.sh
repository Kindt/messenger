#!/usr/bin/env bash
# Тихая установка окружения (Debian/Ubuntu): JDK 25 (Adoptium), Git, Docker (опционально).
# Требуется sudo. Из корня: ./scripts/install-env-silent.sh
# Без Docker: INSTALL_DOCKER=0 ./scripts/install-env-silent.sh
# Минимум вывода: QUIET=1 ./scripts/install-env-silent.sh   или   ./scripts/install-env-silent.sh --quiet
set -euo pipefail

for arg in "$@"; do
  if [[ "$arg" == "-h" || "$arg" == "--help" ]]; then
    echo "Usage: $0 [--quiet|-q]"
    echo "  Env: INSTALL_DOCKER=0 to skip Docker, QUIET=1 for minimal logs."
    echo "  Windows: scripts\\install-env-silent.ps1 -Help"
    exit 0
  fi
done

INSTALL_DOCKER="${INSTALL_DOCKER:-1}"
QUIET="${QUIET:-0}"
for arg in "$@"; do
  if [[ "$arg" == "--quiet" ]] || [[ "$arg" == "-q" ]]; then
    QUIET=1
  fi
done

say() {
  if [[ "$QUIET" != "1" ]]; then
    echo "$@"
  fi
}

apt_update() {
  if [[ "$QUIET" == "1" ]]; then
    sudo apt-get update -qq >/dev/null 2>&1
  else
    sudo apt-get update -qq
  fi
}

say "=== Тихая установка окружения (Linux, apt) ==="

if ! command -v sudo >/dev/null 2>&1; then
  echo "Нужен sudo." >&2
  exit 1
fi

if ! command -v apt-get >/dev/null 2>&1; then
  echo "Автоустановка рассчитана на Debian/Ubuntu (apt-get)." >&2
  echo "Windows: scripts/install-env-silent.ps1  |  macOS: brew install --cask temurin@25 docker" >&2
  exit 1
fi

export DEBIAN_FRONTEND=noninteractive

java_major() {
  if ! command -v java >/dev/null 2>&1; then
    echo 0
    return
  fi
  local line
  line="$(java -version 2>&1 | head -1)"
  if [[ "$line" =~ version\ \"1\.([0-9]+) ]]; then
    echo "${BASH_REMATCH[1]}"
    return
  fi
  if [[ "$line" =~ version\ \"([0-9]+) ]]; then
    echo "${BASH_REMATCH[1]}"
    return
  fi
  echo 0
}

apt_update

if ! command -v git >/dev/null 2>&1; then
  say "Установка git..."
  sudo apt-get install -y -qq git
else
  say "git уже есть."
fi

M="$(java_major)"
if [[ "$M" -lt 17 ]]; then
  say "Установка JDK (Temurin через репозиторий Adoptium)..."
  sudo apt-get install -y -qq wget apt-transport-https gnupg ca-certificates
  TMP_KEY="$(mktemp)"
  wget -qO "$TMP_KEY" https://packages.adoptium.net/artifactory/api/gpg/key/public
  sudo gpg --dearmor -o /etc/apt/trusted.gpg.d/adoptium.gpg <"$TMP_KEY"
  rm -f "$TMP_KEY"
  . /etc/os-release
  CODENAME="${VERSION_CODENAME:-jammy}"
  echo "deb https://packages.adoptium.net/artifactory/deb ${CODENAME} main" | sudo tee /etc/apt/sources.list.d/adoptium.list >/dev/null
  apt_update
  if sudo apt-get install -y -qq temurin-25-jdk 2>/dev/null; then
    say "Установлен temurin-25-jdk."
  elif sudo apt-get install -y -qq temurin-21-jdk 2>/dev/null; then
    say "Установлен temurin-21-jdk (fallback)."
  else
    sudo apt-get install -y -qq openjdk-21-jdk-headless
  fi
else
  say "Java уже подходит (мажорная версия $M)."
fi

if [[ "$INSTALL_DOCKER" == "1" ]]; then
  if ! command -v docker >/dev/null 2>&1; then
    say "Установка Docker (get.docker.com)..."
    curl -fsSL https://get.docker.com | sudo sh
    sudo systemctl enable --now docker 2>/dev/null || true
  else
    say "docker уже есть."
  fi
else
  say "Пропуск Docker (INSTALL_DOCKER=0)."
fi

if [[ "$QUIET" == "1" ]]; then
  echo "[OK] install-env-silent (quiet). Then: ./scripts/install-environment.sh --quiet"
else
  echo "=== Готово. Проверка: ./scripts/install-environment.sh ==="
fi
