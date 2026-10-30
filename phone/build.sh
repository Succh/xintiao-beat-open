#!/usr/bin/env bash
# Build + sign 心跳 Beat Android app with plain SDK command-line tools (no Gradle / Android Studio).
# Usage:  ./build.sh                       (no default server; users type it in the app)
#         BEAT_SERVER=wss://beat.example.com/ws ./build.sh   (bake in the relay address)
set -euo pipefail
cd "$(dirname "$0")"
[ -f ~/android-env.sh ] && source ~/android-env.sh
SDK=${ANDROID_HOME:?ANDROID_HOME not set}
BT=$SDK/build-tools/34.0.0
JAR=$SDK/platforms/android-34/android.jar
KEYS=../keys
OUT=build
VERSION=$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' AndroidManifest.xml)   # bump versionName/versionCode in the manifest
APK=dist/beat-$VERSION.apk
rm -rf "$OUT" && mkdir -p "$OUT"/{res,gen,classes,dex} dist

# ---- resources (inject default server address)
cp -r res "$OUT/res-src"
sed -i "s#@@BEAT_SERVER@@#${BEAT_SERVER:-}#" "$OUT/res-src/values/strings.xml"
"$BT/aapt2" compile --dir "$OUT/res-src" -o "$OUT/res/res.zip"
"$BT/aapt2" link -o "$OUT/unsigned.apk" -I "$JAR" --manifest AndroidManifest.xml \
  --java "$OUT/gen" --min-sdk-version 26 --target-sdk-version 34 "$OUT/res/res.zip"

# ---- java
LIBS="libs/xms-wearable-lib_1.4_release.jar:libs/okhttp-3.12.13.jar:libs/okio-1.17.6.jar"
javac -nowarn -encoding UTF-8 --release 11 -d "$OUT/classes" -classpath "$JAR:$LIBS" \
  $(find src "$OUT/gen" -name '*.java') 2>&1 | grep -v '^Note:' || true
[ -f "$OUT/classes/com/xintiao/beat/MainActivity.class" ] || { echo "javac failed"; exit 1; }

# ---- dex
"$BT/d8" --release --min-api 26 --lib "$JAR" --output "$OUT/dex" \
  $(find "$OUT/classes" -name '*.class') libs/xms-wearable-lib_1.4_release.jar libs/okhttp-3.12.13.jar libs/okio-1.17.6.jar \
  2>&1 | grep -v -i 'warning\|missing class\|^ *at \|^$' || true
python3 - "$OUT" <<'PY'
import sys, zipfile, glob, os
out = sys.argv[1]
with zipfile.ZipFile(os.path.join(out, "unsigned.apk"), "a", zipfile.ZIP_DEFLATED) as z:
    for f in sorted(glob.glob(os.path.join(out, "dex", "classes*.dex"))):
        z.write(f, os.path.basename(f))
PY

# ---- align + sign with the shared key (same certificate as the band .rpk)
"$BT/zipalign" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
prop() { grep "^$1=" "$KEYS/keystore.properties" | cut -d= -f2-; }
export BEAT_KS_PASS="$(prop store.password)"
export BEAT_KEY_PASS="$(prop key.password)"
"$BT/apksigner" sign --ks "$KEYS/beat-release.jks" --ks-key-alias "$(prop key.alias)" \
  --ks-pass env:BEAT_KS_PASS --key-pass env:BEAT_KEY_PASS --out "$APK" "$OUT/aligned.apk"
unset BEAT_KS_PASS BEAT_KEY_PASS
rm -f "$APK.idsig"
mkdir -p ../out && cp "$APK" ../out/
echo "built $APK + ../out/$(basename "$APK") ($(du -h "$APK" | cut -f1))"
