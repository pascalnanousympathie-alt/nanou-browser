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


## Cloud browser-agent service

The cloud browser agent is maintained separately from the Android WebView app. Read [the service operations guide](agent-service/README.md) before deployment, and follow [AGENTS.md](AGENTS.md) for automated-workflow, security, CI troubleshooting, and Opera/noVNC instructions.

- [Agent Service CI](.github/workflows/agent-service-ci.yml)
- [Android CI](.github/workflows/android-ci.yml)
- [CircleCI backup configuration](.circleci/config.yml)

CircleCI is configured as a backup, but it still requires a one-time repository connection at [CircleCI](https://app.circleci.com/). Do not consider the backup active until an actual pipeline run is visible. The Render Blueprint at [render.yaml](render.yaml) also requires the repository owner to connect/approve it in the Render Dashboard before any public service exists.
