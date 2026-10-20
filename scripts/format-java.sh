#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
mode="${1:---check}"
case "$mode" in
  --check) options=(--dry-run --set-exit-if-changed) ;;
  --write) options=(--replace) ;;
  *) echo "Usage: bash scripts/format-java.sh [--check|--write]" >&2; exit 2 ;;
esac
formatter="$(mktemp)"
trap 'rm -f -- "$formatter"' EXIT
curl --fail --silent --show-error --location --max-time 60 \
  https://repo.maven.apache.org/maven2/com/google/googlejavaformat/google-java-format/1.24.0/google-java-format-1.24.0-all-deps.jar \
  --output "$formatter"
mapfile -t sources < <(rg --files src/main/java src/test/java -g '*.java' | sort)
java -jar "$formatter" --aosp --skip-reflowing-long-strings "${options[@]}" "${sources[@]}"
