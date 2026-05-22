#!/usr/bin/env bash
# Dot-source: prometheus_counter METRICS_TEXT NAME

prometheus_gauge() {
  local text="$1" name="$2"
  echo "$text" | awk -v n="$name" '
    $0 ~ "^#|^$" { next }
    $1 == n && $0 !~ /\{/ { print $2; exit }
  '
}

prometheus_counter() {
  local text="$1" name="$2"
  echo "$text" | awk -v n="$name" '
    $0 ~ "^#|^$" { next }
    $1 == n && $0 !~ /\{/ { print $2; exit }
  '
}

prometheus_metric_present() {
  local text="$1" name="$2"
  echo "$text" | grep -qE "^${name}(\{|[[:space:]])" || return 1
}
