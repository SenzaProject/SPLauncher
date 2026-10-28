#!/usr/bin/env bash
# Build SP Launcher -> out/SP-Launcher.apk
unset JAVA_TOOL_OPTIONS
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
TC="${SP_TOOLCHAIN:-$HOME/sp-toolchain}"
[ -f "$TC/android.jar" ] || bash "$ROOT/setup-toolchain.sh"
AJ="$TC/android.jar"; BT="$TC/bundletool.jar"; AAPT2="$TC/aapt2"
VERSION_NAME="$(grep -E '^VERSION_NAME=' "$ROOT/version.properties" | cut -d= -f2)"
VERSION_CODE="$(grep -E '^VERSION_CODE=' "$ROOT/version.properties" | cut -d= -f2)"
B="$ROOT/build"; OUT="$ROOT/out"
rm -rf "$B"; mkdir -p "$B/compiled" "$B/gen" "$B/classes" "$B/dex" "$B/tools" "$OUT"
SRC="$ROOT/app/src/main"

"$AAPT2" compile --dir "$SRC/res" -o "$B/compiled/res.zip"
"$AAPT2" link -o "$B/base.apk" -I "$AJ" --manifest "$SRC/AndroidManifest.xml" \
  --min-sdk-version 34 --target-sdk-version 34 \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
  --java "$B/gen" -R "$B/compiled/res.zip" --auto-add-overlay

javac --release 8 -parameters -Xlint:-options -encoding UTF-8 -cp "$AJ" -d "$B/classes" \
  $(find "$SRC/java" "$B/gen" -name '*.java')

java -cp "$BT" com.android.tools.r8.D8 --release --min-api 33 --lib "$AJ" \
  --output "$B/dex" $(find "$B/classes" -name '*.class')

cp "$B/base.apk" "$B/unsigned.apk"
(cd "$B/dex" && zip -q -u "$B/unsigned.apk" classes.dex)

javac -cp "$BT" -d "$B/tools" "$ROOT/tools/Sign.java"
KS="$ROOT/sp-release.p12"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -storetype PKCS12 -storepass splauncher -keypass splauncher \
    -alias sp -keyalg RSA -keysize 2048 -validity 36500 -dname "CN=SenzaProject, O=SenzaProject, C=ID"
fi
java -cp "$BT:$B/tools" Sign sign "$B/unsigned.apk" "$OUT/SP-Launcher.apk" "$KS" splauncher sp
java -cp "$BT:$B/tools" Sign verify "$OUT/SP-Launcher.apk"
java -cp "$BT:$B/tools" Sign checkalign "$OUT/SP-Launcher.apk"
"$AAPT2" dump badging "$OUT/SP-Launcher.apk" | head -12
ls -la "$OUT/SP-Launcher.apk"
