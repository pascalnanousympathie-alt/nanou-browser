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

## Device support
Minimum Android: 8.0 (API 26). Target SDK is 28 to keep compatibility with older Android devices, including Android 8.1.

## Important
This branch is an independent Android alternative. The Replit workspace did not expose a verifiable source snapshot in this session, so this is not presented as a byte-for-byte export of the Replit implementation.
