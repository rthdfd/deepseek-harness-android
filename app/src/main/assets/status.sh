#!/data/data/com.termux/files/usr/bin/bash
set -u
if curl -fsS --max-time 2 http://127.0.0.1:3080/ >/dev/null 2>&1; then
  printf '%s\n' "READY"
  exit 0
fi
printf '%s\n' "STOPPED"
