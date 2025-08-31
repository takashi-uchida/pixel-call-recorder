# Pixel Call Recorder

A project for recording and managing call data.

## Repository Structure
- `.github/workflows`: Contains GitHub Actions workflows
  - `openhands-issue-handler.yml`: Main workflow for handling issues

## Development
This project uses GitHub Actions for CI/CD. The main workflow handles:
- Issue tracking and management
- Automated testing (when implemented)

## Testing locally

Run unit tests:

```
./gradlew test
```

Run only permission package unit tests:

```
./gradlew :app:testDebugUnitTest --tests "com.callrecorder.pixel.permission.*"
```

Run Android instrumentation tests on an emulator:

```
# One‑time: ensure local SDK exists (this repo uses .android-sdk)
# Then run:
scripts/run_android_tests.sh
```

The script will:
- Install emulator + API 34 system image (arm64-v8a on Apple Silicon, x86_64 on Intel)
- Create an AVD if missing
- Boot the emulator headlessly and wait for boot completion
- Run `:app:connectedDebugAndroidTest`
