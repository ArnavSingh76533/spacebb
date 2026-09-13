# Space Browser

A small, native Android browser with an original orbital identity, a bottom address bar and a quieter browsing experience. Built for Android 10+ using the device's Android System WebView; it does not bundle another Chromium engine.

## Download the APK

Open [Releases](https://github.com/ArnavSingh76533/spacebb/releases) and download **Space-Browser.apk** from the newest preview. Allow installation from the app you use to open the APK if Android asks.

Alternatively, open [Actions → Build Space Browser APK](https://github.com/ArnavSingh76533/spacebb/actions/workflows/android.yml), choose a successful run and download **Space-Browser-APK** under Artifacts. Unzip it to get the APK and its SHA-256 checksum. GitHub requires you to be signed in for Actions artifact downloads.

Every push to `main` runs unit tests, Android lint, release shrinking and Android 10 and Android 15 emulator smoke tests. A preview release is published only after those jobs pass. You can also use **Run workflow** in Actions.

## Features

- Original dark and light themes, vector planet artwork, large touch targets and a bottom address/search bar.
- Up to 30 tabs; at most four WebViews are kept live. Other tabs sleep and reload when selected. Regular tab URLs survive app restarts.
- Local domain-based ad/tracker/malware filtering from a bundled StevenBlack unified-hosts snapshot, with per-site exceptions and real blocked-request counts.
- Private browsing in a separate Android process and separate WebView data directory; no saved history, bookmarks or restored tabs. Screenshots and recents previews are protected.
- Bookmarks, local browsing history (latest 500), page search, article reading view, mobile/desktop user agent, share and long-press link actions.
- DuckDuckGo, Brave Search and Google search choices.
- System downloads, file uploads and fullscreen video. Camera/microphone access requires a site prompt and Android runtime permission. Location access is denied.
- Default-browser registration, clear browsing data, JavaScript toggle and optional USB inspection.
- On-device developer tools: console messages, observed request URLs including blocked requests, live DOM/source, page metadata, and a JavaScript console with an explicit run confirmation.

## Lightweight by design

No runtime UI framework, image downloads, analytics SDK, account system, ads in the browser UI, paid subscription, or background filter-update service. The home artwork and icons are drawn as vectors. APK and memory use are different: websites still use the device's WebView renderer, and a heavy website can consume substantial RAM. The APK size is measured in each Actions build summary.

## Privacy and security boundaries

Third-party cookies are disabled. Certificate errors are cancelled, mixed content is blocked, Android WebView Safe Browsing is enabled, and page access to `file://` and `content://` is disabled. No JavaScript-to-Java bridge is exposed. Normal HTTP websites and local development servers remain accessible; HTTP is not encrypted.

Private browsing uses an isolated data directory. Its directory is cleared before each new private process starts and when the session closes. Close private browsing from its menu to end the session; leaving the app temporarily keeps the session open. Files you download and links you copy remain outside private browsing. Private mode is not a VPN or anonymity service. Website operators and your network can still observe traffic.

Normal history, bookmarks and session URLs are stored in app-private preferences; Android backups are disabled. Developer logs are bounded in memory and are not persisted by Space. Pages may still use their own storage. Clear browsing data closes regular tabs and clears cookies, cache, web storage, HTTP-auth credentials and history. Bookmarks and downloaded files are retained. An open private session must be closed separately.

## Honest limits

This is a WebView-based browser, not a Brave/Chromium fork. Domain filters cannot block all first-party or video ads (including many YouTube ads), and this version does not implement full EasyList cosmetic filtering, extension support, sync, a password manager, VPN or a custom engine-level anti-fingerprinting system. Background media behavior depends on WebView, the website and Android; no background-play guarantee is made.

The request inspector is an observation log, not a full network debugger: it cannot show complete redirects, response bodies, status codes, WebSocket traffic or every service-worker request. Console logging starts when the tab is created. DOM output is capped at 120,000 characters. JavaScript execution runs with the current page's authority; do not paste untrusted code.

Reading view uses the page's `article` or `main` content and works best on article sites. Cookie-only authenticated downloads, blob/data downloads, automatic camera capture in the file chooser, geolocation and arbitrary intent links are not supported. Sleeping tabs preserve their URL, not unsent forms or complete in-page state.

## Build locally

Install JDK 17 and an Android SDK containing platform 35 and build-tools 35.0.0. Set `ANDROID_HOME` or create an untracked `local.properties` with `sdk.dir=/your/android/sdk`.

```sh
python3 scripts/prepare-filters.py
./gradlew testDebugUnitTest lintRelease assembleRelease
```

Output: `app/build/outputs/apk/release/app-release.apk`.

The project uses Android Gradle Plugin 8.9.2 and Gradle 8.11.1. Native application code is Java 17; no Kotlin or Compose runtime is bundled.

## Preview signing

The release build is minified and non-debuggable but signed with a **development identity** so it can be installed immediately without asking you for signing secrets. Actions caches the debug keystore to keep preview updates compatible while that cache exists. A cache reset can change the signing identity, requiring an uninstall before installing a newer preview. Uninstalling removes local browsing data. Do not use this identity for a production app-store release; configure a private, backed-up release keystore before public production distribution.

USB WebView inspection starts disabled, even in debug builds. Enable it explicitly in Settings for a session, and turn it off afterwards.

## Tests and filter attribution

Pure JVM tests exercise URL handling and domain-boundary matching. The device smoke test loads an offline HTTP fixture in the actual Android WebView, checks secure settings, exercises native panels, tests private-cookie isolation and captures screenshots. Reports and screenshots are attached to Actions runs.

`app/src/main/assets/filter-source.txt` identifies the exact filter snapshot. The unmodified hosts file retains its attribution header; root and constituent-source licenses are included in `app/src/main/assets/hosts-license.txt` and `third_party/hosts/`. Review upstream changes before replacing this snapshot. Filters do not silently update at runtime.

Space Browser code is MIT licensed. Gradle wrapper files retain their Apache 2.0 notices. Filter data retains its original licenses.
