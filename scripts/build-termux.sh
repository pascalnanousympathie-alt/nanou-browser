#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/android-sdk}}"
AAPT2_WRAPPER="${AAPT2_WRAPPER:-$HOME/.local/bin/aapt2}"
GRADLE_COMMAND=(./gradlew)
BUILD_LOG="$HOME/nanou-browser-build.log"

if [[ ! -d "$SDK_ROOT/platforms/android-35" ]]; then
  echo "ERROR: Android SDK Platform 35 missing at $SDK_ROOT/platforms/android-35" >&2
  echo "Install it with sdkmanager 'platforms;android-35' and Build Tools 35.0.0." >&2
  exit 2
fi

if [[ ! -d "$SDK_ROOT/build-tools/35.0.0" ]]; then
  echo "ERROR: Android Build Tools 35.0.0 missing at $SDK_ROOT/build-tools/35.0.0" >&2
  echo "Install it with sdkmanager 'build-tools;35.0.0'." >&2
  exit 2
fi

if [[ -n "${PREFIX:-}" && "$PREFIX" == */com.termux/files/usr ]]; then
  if [[ ! -x "$AAPT2_WRAPPER" ]]; then
    echo "ERROR: Termux AAPT2 wrapper not executable: $AAPT2_WRAPPER" >&2
    echo "Set AAPT2_WRAPPER to a compatible wrapper (for example, the existing $HOME/.local/bin/aapt2)." >&2
    exit 2
  fi
  GRADLE_COMMAND+=("-Pandroid.aapt2FromMavenOverride=$AAPT2_WRAPPER")
elif [[ -x "$AAPT2_WRAPPER" ]]; then
  GRADLE_COMMAND+=("-Pandroid.aapt2FromMavenOverride=$AAPT2_WRAPPER")
fi

export ANDROID_HOME="$SDK_ROOT"
export ANDROID_SDK_ROOT="$SDK_ROOT"
if [[ -n "${PREFIX:-}" && -d "$PREFIX/lib/jvm/java-21-openjdk" && -z "${JAVA_HOME:-}" ]]; then
  export JAVA_HOME="$PREFIX/lib/jvm/java-21-openjdk"
  export PATH="$JAVA_HOME/bin:$PATH"
fi

APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
cd "$ROOT"
chmod +x ./gradlew

echo "NANOU_BROWSER_BUILD_ROOT=$ROOT"
echo "ANDROID_SDK_ROOT=$ANDROID_SDK_ROOT"
echo "JAVA_VERSION=$(java -version 2>&1 | head -n 1)"
echo "RUNNING_UNIT_TESTS_AND_DEBUG_APK_BUILD"

if "${GRADLE_COMMAND[@]}" --no-daemon --no-parallel \
  -Dorg.gradle.workers.max=1 testDebugUnitTest assembleDebug \
  --console=plain --stacktrace 2>&1 | tee "$BUILD_LOG"; then
  echo "GRADLE_BUILD_EXIT=0"
else
  rc=$?
  echo "GRADLE_BUILD_EXIT=$rc"
  DOWNLOADS="$HOME/storage/downloads"
  if [[ -d "$DOWNLOADS" && -w "$DOWNLOADS" ]]; then
    cp "$BUILD_LOG" "$DOWNLOADS/NanouBrowser-build.log" || true
  fi
  exit "$rc"
fi

test -s "$APK"
unzip -t "$APK" | tail -n 2

APKSIGNER="$SDK_ROOT/build-tools/35.0.0/apksigner"
if [[ -x "$APKSIGNER" ]]; then
  "$APKSIGNER" verify "$APK"
  echo "APK_SIGNATURE=PASS"
else
  echo "APK_SIGNATURE=NOT_CHECKED (apksigner missing)"
fi

echo "APK_PATH=$APK"
sha256sum "$APK"

DOWNLOADS="$HOME/storage/downloads"
if [[ -d "$DOWNLOADS" && -w "$DOWNLOADS" ]]; then
  cp "$APK" "$DOWNLOADS/NanouBrowser-debug.apk"
  test -s "$DOWNLOADS/NanouBrowser-debug.apk"
  echo "APK_COPIED_TO=$DOWNLOADS/NanouBrowser-debug.apk"
  cp "$BUILD_LOG" "$DOWNLOADS/NanouBrowser-build.log"
  echo "BUILD_LOG_COPIED_TO=$DOWNLOADS/NanouBrowser-build.log"
else
  echo "DOWNLOADS_COPY=SKIPPED (run termux-setup-storage to enable it)"
  echo "BUILD_LOG_PATH=$BUILD_LOG"
fi
