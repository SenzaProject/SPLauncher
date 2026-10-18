#!/usr/bin/env bash
# Menyiapkan toolchain build APK tanpa Android SDK resmi.
# Sumber: GitHub (bundletool memuat D8 + aapt2 + apksig; android.jar dari Sable/android-platforms).
set -euo pipefail
TC="${SP_TOOLCHAIN:-$HOME/sp-toolchain}"
mkdir -p "$TC"
cd "$TC"
if [ ! -f bundletool.jar ]; then
  curl -fsSL -o bundletool.jar https://github.com/google/bundletool/releases/download/1.17.2/bundletool-all-1.17.2.jar
fi
if [ ! -x aapt2 ]; then
  unzip -oq bundletool.jar 'linux/aapt2' -d bt && cp bt/linux/aapt2 aapt2 && chmod +x aapt2
fi
if [ ! -f android.jar ]; then
  rm -rf sable
  git clone --depth 1 --filter=blob:none --sparse https://github.com/Sable/android-platforms sable
  (cd sable && git sparse-checkout set android-34)
  cp sable/android-34/android.jar android.jar
fi
echo "Toolchain siap di $TC"
