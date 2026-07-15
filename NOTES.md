# Shumtimes — Project Notes

Custom Android TV Plex client for the Nvidia Shield Pro (2019), built to enable
real synchronized "watch together" playback with a cousin on a remote Plex
server. See the full architecture/phase plan for context; this file tracks
running setup details, decisions, and gotchas as we build.

## Target device

- Nvidia Shield Pro (2019), model `SHIELD_Android_TV`, codename `mdarcy`
- Runs up to Android 11 — app's `minSdk = 26` comfortably covers this
- On the home WLAN at `192.168.0.81` (found by MAC vendor lookup: Nvidia
  Corporation OUI `3c:6d:66`, since the Shield doesn't have a fixed/known IP
  ahead of time)
- ADB over network: enabled via Settings → Device Preferences → Developer
  options → Network debugging. Connect with:
  ```
  adb connect 192.168.0.81:5555
  ```
  (accept the on-device RSA key prompt the first time)

## Toolchain

- No Android Studio GUI installed — driving everything via CLI (Claude Code),
  so only the command-line SDK tools were installed, not the full IDE.
- Android SDK root: `~/android-sdk` (`ANDROID_HOME`/`ANDROID_SDK_ROOT`, added
  to `~/.bashrc` along with `cmdline-tools/latest/bin` and `platform-tools`
  on `PATH`)
- Java: system OpenJDK 21 (already present, no install needed)
- Gradle: wrapped at **9.4.1** — note AGP 9.2.0 actually requires Gradle
  9.4.1+ despite some docs/search results suggesting 8.11 was enough; trust
  the Gradle error message's stated minimum over secondary sources here.
- AGP: **9.2.0**, using AGP 9's new *built-in Kotlin support* — no
  `org.jetbrains.kotlin.android` plugin applied, just `com.android.application`
  + `org.jetbrains.kotlin.plugin.compose` for the Compose compiler.
- Kotlin: 2.4.10
- compileSdk/targetSdk: **36** (bumped up from an initial 35 because
  `androidx.media3:*:1.10.1` requires compiling against API 36+)
- UI: Jetpack Compose for TV (`androidx.tv:tv-material:1.1.0`,
  `tv-foundation:1.0.0`) — chosen over the older Leanback library as the
  current Google-recommended path and a much gentler on-ramp coming from
  web/React.
- Playback: Media3 ExoPlayer `1.10.1`.

## Repo layout

- `app/` — the Android TV Kotlin/Compose client
- `relay/` — (Phase E) the watch-together WebSocket relay, Node.js
- App name: **Movies Shumtimes**; package/applicationId: `com.moviesshumtimes.tv`

## Decisions made along the way

- **Forking existing open-source clients was considered and ruled out.**
  Plex's own "Watch Together" feature is being sunset, so not worth building
  on top of. Plezy (Flutter, open source) doesn't support transcoding, which
  is a hard requirement here (subtitle burn-in for image-based subs relies on
  it). Building custom, per the original plan.
- Cousin will run this same app on their own Android TV device/account,
  pointed at the same relay server — keeps the watch-together sync protocol
  symmetric (no need to reverse-engineer Plex's own Companion/Remote-Control
  protocol to drive an unmodified official client).
- Relay hosting: Render.com free tier (Fly.io's free tier no longer exists as
  of 2026).

## Gotchas hit so far

- Running `gradle wrapper` from the wrong working directory (e.g. `cd /tmp &&
  ... gradle wrapper` in one chained command) silently generates the wrapper
  in the wrong place with a confusing "directory does not contain a Gradle
  build" error — always pass `-p <project dir>` explicitly or `cd` in its own
  step first.
- `android:banner` and launcher icon don't require raster PNGs — plain
  `VectorDrawable`s work fine as placeholders (`app/src/main/res/drawable/tv_banner.xml`,
  `ic_launcher_background.xml` / `ic_launcher_foreground.xml`). Swap for real
  artwork later.
