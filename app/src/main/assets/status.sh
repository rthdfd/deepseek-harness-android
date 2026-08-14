#!/data/data/com.termux/files/usr/bin/bash
set -u
HOME_DIR="${HOME:-/data/data/com.termux/files/home}"
PID_FILE="$HOME_DIR/.dsh/pid"

if [ -f "$PID_FILE" ] && kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
  if curl -fsS --max-time 2 http://127.0.0.1:3080/ >/dev/null 2>&1; then
    printf '%s\n' "READY"
    exit 0
  fi
  printf '%s\n' "STARTING"
  exit 0
fi
printf '%s\n' "STOPPED"
