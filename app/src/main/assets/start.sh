#!/data/data/com.termux/files/usr/bin/bash
set -u
D="${DISTRO:-ubuntu}"

if curl -fsS --max-time 2 http://127.0.0.1:3080/ >/dev/null 2>&1; then
  printf '%s\n' "DSH already running"
  exit 0
fi
exec proot-distro login "$D" -- bash -lc 'cd ~ && exec dsh web --host 127.0.0.1 --port 3080'
