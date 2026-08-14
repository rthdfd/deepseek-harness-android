#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
V=0.1.0-rc.6
H="${HOME:-/data/data/com.termux/files/home}"
P="${PREFIX:-/data/data/com.termux/files/usr}"
pkg update -y
pkg upgrade -y
pkg install -y nodejs cmake clang make python binutils pkg-config libandroid-spawn curl
npm install -g node-gyp
N="$(node -p 'process.versions.node')"
node-gyp install --ensure --target "$N" >/dev/null 2>&1 || true
for f in "$H"/.cache/node-gyp/*/include/node/common.gypi; do
  [ -f "$f" ] || continue
  grep -q "'android_ndk_path%': ''" "$f" || sed -i "/^[[:space:]]*variables[[:space:]]*{/a\\    'android_ndk_path%': ''," "$f"
done
case "$(uname -m)" in
  aarch64) T=aarch64-linux-android30 ;;
  armv7l|arm) T=armv7a-linux-androideabi30 ;;
  *) T= ;;
esac
if [ -n "$T" ]; then
  export CFLAGS="-target $T"
  export CXXFLAGS="-target $T"
fi
npm install -g "@deepseek-ai/dsh@$V"
R="$(npm root -g)/@deepseek-ai/dsh"
D="$(mktemp -d)"
trap 'rm -rf "$D"' EXIT
printf '{"private":true}\n' >"$D/package.json"
(cd "$D" && npm install --no-save @img/sharp-wasm32@0.35.3)
mkdir -p "$R/node_modules/@img"
cp -R "$D/node_modules/@img/sharp-wasm32" "$R/node_modules/@img/"
[ ! -d "$D/node_modules/@emnapi" ] || cp -R "$D/node_modules/@emnapi" "$R/node_modules/"
mkdir -p "$H/.dsh"
printf '%s\n' "$V" >"$H/.dsh/version"
printf '%s\n' "DSH installation complete"
