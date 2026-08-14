#!/data/data/com.termux/files/usr/bin/bash
set -u
HOME_DIR="${HOME:-/data/data/com.termux/files/home}"
PID_FILE="$HOME_DIR/.dsh/pid"

if [ -f "$PID_FILE" ]; then
  PID="$(cat "$PID_FILE")"
  kill "$PID" 2>/dev/null || true
  rm -f "$PID_FILE"
fi
printf '%s\n' "DSH stopped"
