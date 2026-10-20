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
if command -v rg >/dev/null 2>&1; then
  mapfile -t sources < <(rg --files src/main/java src/test/java -g '*.java' | sort)
else
  mapfile -t sources < <(find src/main/java src/test/java -type f -name '*.java' | sort)
fi
if [ "${#sources[@]}" -eq 0 ]; then echo "No Java sources found" >&2; exit 1; fi
java -jar "$formatter" --aosp --skip-reflowing-long-strings "${options[@]}" "${sources[@]}"
