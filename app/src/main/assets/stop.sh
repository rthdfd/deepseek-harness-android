#!/data/data/com.termux/files/usr/bin/bash
set -u
D="${DISTRO:-ubuntu}"
proot-distro login "$D" -- bash -lc 'pkill -f "[b]in\\.js" 2>/dev/null || true'
printf '%s\n' "DSH stop requested"
