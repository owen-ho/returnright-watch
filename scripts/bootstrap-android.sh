#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
TOOLS_DIR="$PROJECT_DIR/.tools"
SDK_DIR="$TOOLS_DIR/android-sdk"
SDK_ARCHIVE="$TOOLS_DIR/android-commandline-tools.zip"

if [[ "${1:-}" != "--accept-sdk-license" ]]; then
    echo "This installer downloads Google's Android SDK and accepts its SDK license."
    echo "Review https://developer.android.com/studio#terms and rerun with --accept-sdk-license."
    exit 1
fi
if [[ "$(uname -s)" != Darwin || "$(uname -m)" != arm64 ]]; then
    echo "This bootstrap is for Apple Silicon macOS. Use Android Studio's SDK Manager instead."
    exit 1
fi
export JAVA_HOME="${JAVA_HOME:-$TOOLS_DIR/jdk-17.0.20.1+1/Contents/Home}"
if [[ ! -x "$JAVA_HOME/bin/java" ]]; then
    echo "Set JAVA_HOME to a Java 17 installation first."
    exit 1
fi
mkdir -p "$SDK_DIR/cmdline-tools"
if [[ ! -x "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" ]]; then
    curl --fail --location --retry 2 \
        https://dl.google.com/android/repository/commandlinetools-mac_arm64-15859902_latest.zip \
        --output "$SDK_ARCHIVE"
    EXPECTED_SHA=835b62a26162b229b441d1f6d4680383815a270809eb33522c0d480fa5002c4e
    ACTUAL_SHA="$(shasum -a 256 "$SDK_ARCHIVE" | awk '{print $1}')"
    [[ "$ACTUAL_SHA" == "$EXPECTED_SHA" ]] || { echo "SDK archive checksum mismatch"; exit 1; }
    unzip -q "$SDK_ARCHIVE" -d "$SDK_DIR/cmdline-tools"
    mv "$SDK_DIR/cmdline-tools/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
fi
# sdkmanager prompts for the license of the specific packages being installed.
printf 'y\ny\ny\ny\ny\n' | "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" \
    --sdk_root="$SDK_DIR" "platforms;android-35" "build-tools;35.0.0"
echo "SDK installed at $SDK_DIR. Set ANDROID_HOME to this path to build."
