#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Mise contributors
# SPDX-License-Identifier: AGPL-3.0-or-later

set -euo pipefail

root_dir="$(cd "$(dirname "$0")/.." && pwd)"
scenario_file="${1:-$root_dir/visual-scenarios.json}"
output_dir="${2:-$root_dir/android/visual-baselines/webview}"

if ! command -v adb >/dev/null 2>&1; then
  echo "adb is required to capture a disposable WebView reference" >&2
  exit 2
fi
if [[ "${MISE_DISPOSABLE_EMULATOR:-}" != "1" ]]; then
  echo "Refusing reference capture without MISE_DISPOSABLE_EMULATOR=1" >&2
  exit 3
fi
if [[ ! -f "$scenario_file" ]]; then
  echo "Scenario file not found: $scenario_file" >&2
  exit 4
fi

mkdir -p "$output_dir"
echo "Reference capture harness ready for $(basename "$scenario_file")."
echo "Launch the separately authorized WebView oracle, drive the listed scenarios, and save PNGs below $output_dir."
