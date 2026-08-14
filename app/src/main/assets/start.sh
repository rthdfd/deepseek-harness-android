#!/data/data/com.termux/files/usr/bin/bash
set -u
H="${HOME:-/data/data/com.termux/files/home}"
R="$(npm root -g 2>/dev/null)/@deepseek-ai/dsh"
F="$H/.dsh/pid"
[ -f "$R/lib/bin.js" ] || { echo "DSH is not installed" >&2; exit 3; }
mkdir -p "$H/.dsh"
[ -f "$F" ] && kill -0 "$(cat "$F")" 2>/dev/null && { echo "DSH already running"; exit 0; }
nohup node --expose-internals "$R/lib/bin.js" web --host 127.0.0.1 --port 3080 >"$H/.dsh/web.log" 2>&1 &
echo $! >"$F"
echo "DSH starting on http://127.0.0.1:3080"
