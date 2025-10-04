#!/usr/bin/env bash
set -euo pipefail

# Runs Android instrumentation tests on a local emulator.
# - Installs emulator + system image (API 34) via sdkmanager
# - Creates an AVD if missing
# - Boots emulator headlessly and waits for boot completion
# - Runs connectedDebugAndroidTest
#
# Requirements:
# - Java (JDK 17 recommended for AGP 8.2)
# - Android SDK cmdline-tools available (this project sets up .android-sdk)

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SDK_DIR="${ANDROID_SDK_ROOT:-$ROOT_DIR/.android-sdk}"
CMDLINE_BIN="$SDK_DIR/cmdline-tools/latest/bin"
PLATFORM_TOOLS="$SDK_DIR/platform-tools"
EMULATOR_BIN="$SDK_DIR/emulator/emulator"

export ANDROID_SDK_ROOT="$SDK_DIR"
export PATH="$CMDLINE_BIN:$PLATFORM_TOOLS:$PATH"

if [ ! -x "$CMDLINE_BIN/sdkmanager" ]; then
  echo "cmdline-tools not found at $CMDLINE_BIN. Run the SDK setup step first." >&2
  exit 1
fi

yes | sdkmanager --licenses >/dev/null || true

# Choose system image ABI based on host arch
ARCH="$(uname -m)"
if [ "$ARCH" = "arm64" ] || [ "$ARCH" = "aarch64" ]; then
  ABI="arm64-v8a"
else
  ABI="x86_64"
fi

SYS_IMG="system-images;android-34;google_apis;${ABI}"

echo "Installing emulator + system image ($SYS_IMG) if missing..."
yes | sdkmanager "emulator" "$SYS_IMG" >/dev/null

AVD_NAME="pixel_api_34_${ABI}"
AVD_DIR="$HOME/.android/avd/${AVD_NAME}.avd"

if [ ! -d "$AVD_DIR" ]; then
  echo "Creating AVD $AVD_NAME..."
  echo "no" | avdmanager create avd -n "$AVD_NAME" -k "$SYS_IMG" -d pixel >/dev/null
fi

echo "Starting emulator $AVD_NAME..."
"$EMULATOR_BIN" -avd "$AVD_NAME" \
  -no-window -no-snapshot -no-boot-anim -gpu swiftshader_indirect \
  -netdelay none -netspeed full >/dev/null 2>&1 &
EMU_PID=$!

cleanup() {
  echo "Stopping emulator..."
  "$PLATFORM_TOOLS/adb" -s emulator-5554 emu kill >/dev/null 2>&1 || true
  if ps -p $EMU_PID >/dev/null 2>&1; then kill -9 $EMU_PID >/dev/null 2>&1 || true; fi
}
trap cleanup EXIT

echo "Waiting for device..."
"$PLATFORM_TOOLS/adb" wait-for-device

echo "Waiting for boot completion..."
BOOT_COMPLETED="0"
for i in {1..120}; do
  BOOT_COMPLETED="$($PLATFORM_TOOLS/adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
  if [ "$BOOT_COMPLETED" = "1" ]; then break; fi
  sleep 2
done
if [ "$BOOT_COMPLETED" != "1" ]; then
  echo "Emulator failed to boot in time." >&2
  exit 1
fi

echo "Running connectedDebugAndroidTest..."
(cd "$ROOT_DIR" && ./gradlew :app:connectedDebugAndroidTest --no-daemon)

echo "Instrumentation tests completed."

