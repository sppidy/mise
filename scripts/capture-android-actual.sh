#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Mise contributors
# SPDX-License-Identifier: AGPL-3.0-or-later

set -euo pipefail

root_dir="$(cd "$(dirname "$0")/.." && pwd)"
output_dir="${1:-$root_dir/android/build/visual-regression/actual}"
mkdir -p "$output_dir"
if ! command -v adb >/dev/null 2>&1; then
  echo "adb is required for actual screenshot capture" >&2
  exit 2
fi
if [[ "${MISE_DISPOSABLE_EMULATOR:-}" != "1" ]]; then
  echo "Refusing actual capture without MISE_DISPOSABLE_EMULATOR=1" >&2
  exit 3
fi
device="${ANDROID_SERIAL:-$(adb devices | awk 'NR > 1 && $2 == "device" { print $1; exit }')}"
if [[ -z "$device" ]]; then
  echo "No connected disposable emulator" >&2
  exit 4
fi
adb -s "$device" exec-out screencap -p > "$output_dir/screen.png"
echo "Captured $output_dir/screen.png from disposable emulator $device."
