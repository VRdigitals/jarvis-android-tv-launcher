#!/bin/zsh
# Builds build/tvhome.apk with the Android SDK tools directly (no Gradle / Android Studio).
# Needs: JDK 17, Android SDK with platforms;android-34 and build-tools 34+.
set -euo pipefail
cd "${0:A:h}"

# Java: $JAVA_HOME, else ~/jdk-17, else the system's JDK 17
if [[ -z "${JAVA_HOME:-}" ]]; then
  if [[ -x "$HOME/jdk-17/bin/javac" ]]; then JAVA_HOME="$HOME/jdk-17"
  else JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || true); fi
fi
[[ -x "${JAVA_HOME:-}/bin/javac" ]] || { echo "JDK 17 not found. Install it and/or set JAVA_HOME."; exit 1; }
export JAVA_HOME PATH="$JAVA_HOME/bin:$PATH"

# Android SDK: $ANDROID_HOME, else the default Android Studio location
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
JAR="$SDK/platforms/android-34/android.jar"
[[ -f "$JAR" ]] || { echo "Android platform 34 not found in $SDK (sdkmanager \"platforms;android-34\")."; exit 1; }
BT=$(ls -d "$SDK"/build-tools/3[4-9]* 2>/dev/null | sort -V | tail -1)
[[ -n "$BT" ]] || { echo "Android build-tools 34+ not found (sdkmanager \"build-tools;34.0.0\")."; exit 1; }
VERSION_CODE=${VERSION_CODE:-$(date +%y%m%d%H%M | cut -c1-9)}
VERSION_NAME=${VERSION_NAME:-1.0.$VERSION_CODE}

rm -rf build && mkdir -p build/gen build/classes build/dex

echo "1/5 resources"
"$BT/aapt2" compile --dir res -o build/res.zip
# -0 m4a: voice clips stay uncompressed so SoundPool can play them straight from the APK
"$BT/aapt2" link -o build/unsigned.apk -I "$JAR" --manifest AndroidManifest.xml -A assets -0 m4a \
  --java build/gen --min-sdk-version 31 --target-sdk-version 34 \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" build/res.zip

echo "2/5 java"
# --release 11 + android.jar on the classpath: java.* comes from the JDK (d8 desugars lambdas)
javac --release 11 -classpath "$JAR" -encoding UTF-8 \
  -d build/classes $(find src build/gen -name '*.java')

echo "3/5 dex"
"$BT/d8" --min-api 31 --release --lib "$JAR" --output build/dex $(find build/classes -name '*.class')
(cd build/dex && zip -q -u ../unsigned.apk classes.dex)

echo "4/5 align"
"$BT/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk

echo "5/5 sign"
# Your own local signing key, made on first build. Keep keystore/ (it's git-ignored):
# updates must be signed with the same key. Set KEYSTORE_PASS to choose its password.
PASS="${KEYSTORE_PASS:-tvhome-local}"
if [[ ! -f keystore/tvhome.jks ]]; then
  mkdir -p keystore
  keytool -genkeypair -keystore keystore/tvhome.jks -alias tvhome -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass "$PASS" -keypass "$PASS" -dname "CN=TV Home" >/dev/null
fi
"$BT/apksigner" sign --ks keystore/tvhome.jks --ks-pass "pass:$PASS" --key-pass "pass:$PASS" \
  --out build/tvhome.apk build/aligned.apk

echo "Built build/tvhome.apk ($(du -h build/tvhome.apk | cut -f1))"
