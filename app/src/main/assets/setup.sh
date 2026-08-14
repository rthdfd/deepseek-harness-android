#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
D="${DISTRO:-ubuntu}"

if ! command -v proot-distro >/dev/null 2>&1; then
  printf '%s\n' "proot-distro not found in Termux" >&2
  exit 2
fi
if proot-distro login "$D" -- bash -lc 'command -v dsh' >/dev/null 2>&1; then
  printf '%s\n' "DSH found in proot $D"
  exit 0
fi
printf '%s\n' "DSH not found in proot $D; run: proot-distro login $D && npm i -g @deepseek-ai/dsh" >&2
exit 4
