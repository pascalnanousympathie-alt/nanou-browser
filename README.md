# Nanou Browser

Nanou Browser is an independent Android browser implementation created outside Replit so the source, build, and tests can be verified directly.

## V1 functions
- Android WebView browser with multiple tabs (up to 12), close-tab, back/forward/reload, and home.
- Address-bar navigation and DuckDuckGo search.
- Locally stored bookmarks and browsing history.
- Page sharing and file downloads through Android DownloadManager.
- Certificate errors are cancelled, mixed content is blocked, third-party cookies are disabled, and file/content URL access is disabled.
- Safe Browsing is enabled where supported.
- No analytics or account service; bookmarks and history remain in local app storage.

## Build and tests
Requirements: JDK 17+, Android SDK Platform 35, Build Tools 35.0.0.

Run:

    ./gradlew testDebugUnitTest assembleDebug

The debug APK is produced at:

    app/build/outputs/apk/debug/app-debug.apk

### Build directly on Termux

This is the independent build path when GitHub-hosted Actions cannot start. On the Android phone, install Java 17+ and Android SDK Platform 35 / Build Tools 35.0.0.

For an **existing clone**, get the current Nanou Browser fix branch before building:

```bash
cd ~/nanou-browser
git fetch origin fix/navigation-and-low-memory-build
git checkout fix/navigation-and-low-memory-build
git pull --ff-only origin fix/navigation-and-low-memory-build
bash scripts/build-termux.sh
```

For a fresh checkout, clone the repository's `fix/navigation-and-low-memory-build` branch first, then run `bash scripts/build-termux.sh`.

On Termux, the script uses `$HOME/android-sdk` by default and the compatible AAPT2 wrapper at `$HOME/.local/bin/aapt2` (override with `AAPT2_WRAPPER` if needed). It runs unit tests, builds the debug APK with one Gradle worker, validates the APK archive and signature, prints its SHA-256, and copies it to `$HOME/storage/downloads/NanouBrowser-debug.apk` when Termux storage is available. It does not alter other projects.

## Device support
Minimum Android: 8.0 (API 26). Target SDK is 28 to keep compatibility with older Android devices, including Android 8.1.

## Important
This branch is an independent Android alternative. The Replit workspace did not expose a verifiable source snapshot in this session, so this is not presented as a byte-for-byte export of the Replit implementation.
