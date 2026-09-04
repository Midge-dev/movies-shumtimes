# Rename plan: Movies Shumtimes → Reelay

Status: **documented, decisions made, not yet executed.** Sean wants this
run as 5 independent parts, each done as its own pass whenever he's ready —
not necessarily all in one session. Logo/splash/icon artwork is explicitly
**out of scope** — Sean is remaking those separately.

## Decisions (confirmed 2026-09-02)

1. **New Android package id: `com.reelay.tv`** (replaces
   `com.moviesshumtimes.tv`). Worth a final glance before Part 1 actually
   starts in case a future iOS/tvOS bundle id
   ([[project_cross_platform_interest]]) argues for something slightly
   different, but this is the adopted default.
2. **Design-system prefix: dropped entirely.** `Shum*` components become
   plain generic names (`ShumCard` → `Card`, `ShumButton` → `Button`, etc.)
   rather than a new `Reelay*` prefix — "generic until we've landed on a
   name." Matches how `Text`/`Icon` in `ui/kit` are already named with no
   prefix.
3. **Relay server: rename to something generic/close to "Reelay."** Exact
   string not yet picked — `reelay-relay` (matches the existing
   `<name>-relay` convention) or plain `reelay` are the two natural options,
   pick one at execution time. **Real consequence, not just cosmetic:** this
   changes the deployed Render service's hostname, which breaks every
   already-paired relay entry (Sean's and his cousin's) until each is
   re-paired via QR/URL. Do this deliberately, not as a drive-by find/replace.
4. **GitHub repo: rename to `Midge-dev/reelay`.** GitHub auto-redirects the
   old URL, but hardcoded references below (CI workflow, `.claude/settings
   .local.json`) still need updating regardless.
5. **APK/workflow artifact filename: `reelay.apk`** (replaces
   `movies-shumtimes.apk`).

## Part 1 — Android package id rename

Highest blast radius. `com.moviesshumtimes.tv` → `com.reelay.tv` on
essentially every Kotlin file in `app/` and `shared/` (~70 files) plus all
cross-file imports. Best done with a proper package-rename
refactor/tool to keep imports consistent, not raw text find/replace.

- `app/build.gradle.kts`: `namespace` (line 23), `applicationId` (line 27)
- `shared/build.gradle.kts`: `namespace = "com.moviesshumtimes.tv.shared"`
- Every `package com.moviesshumtimes.tv...` declaration + matching
  directory structure (`app/src/main/java/com/moviesshumtimes/tv/**`,
  `shared/src/commonMain/kotlin/com/moviesshumtimes/tv/**`)
- `.claude/settings.local.json`'s Bash permission allowlist hardcodes
  `com.moviesshumtimes.tv` in several `adb` install/uninstall entries —
  update or Sean gets permission prompts for the renamed equivalents.

Build + install after this part on its own before moving on — easiest part
to silently break the build in, hardest to bisect if bundled with others.

## Part 2 — Design-system identifier rename

All in `app/src/main/java/com/moviesshumtimes/tv/ui/kit/` (+ `ShumArtwork`
in `ui/common/Artwork.kt`), used pervasively across nearly every UI file —
second-widest blast radius:

`ShumArtwork` → `Artwork`, `ShumBorder` → `Border`, `ShumButton` →
`Button`, `ShumCard` → `Card`, `ShumCardContainer` → `CardContainer`,
`ShumColors` → `Colors`, `ShumFilterChip` → `FilterChip`, `ShumGlow` →
`Glow`, `ShumIconButton` → `IconButton`, `ShumListItem` → `ListItem`,
`ShumOutlinedButton` → `OutlinedButton`, `ShumRadioButton` →
`RadioButton`, `ShumSwitch` → `Switch`, `ShumTypography` → `Typography`

Do this as its own commit separate from Part 1 for the same
easy-to-bisect reason. Depends on Part 1 only in that both touch the same
files heavily — fine to sequence either order, but doing them separately
keeps each diff reviewable.

## Part 3 — App/build/CI identity

- `app/src/main/res/values/strings.xml`: `app_name` → `Reelay`
  (`AndroidManifest.xml`'s `android:label` already references
  `@string/app_name` indirectly, no manifest edit needed)
- `app/build.gradle.kts` line 86: `output.outputFileName.set("reelay.apk")`
- `settings.gradle.kts`: `rootProject.name = "reelay"`
- `.github/workflows/build-apk.yml`: update the
  `app/build/outputs/apk/debug/movies-shumtimes.apk` reference in the
  release-publish step to match the new filename
- GitHub repo rename to `Midge-dev/reelay` (external action on github.com,
  not a code change — do this whenever, but before/alongside updating any
  doc that links to the repo by URL)

**Still out of scope:** launcher icon (`@mipmap/ic_launcher`,
`@mipmap/ic_banner`) and splash artwork — file names are already generic,
only the image content changes, later, separately.

## Part 4 — User-facing branding strings + Plex headers

Literal "Movies Shumtimes" text shown to a person or sent to an external
service, all → "Reelay":

- `RelaySetupScreen.kt:90` — onboarding copy: "Movies Shumtimes syncs
  playback with whoever you're watching with..."
- `SplashScreen.kt:133` — `contentDescription = "Movies Shumtimes"`
- `PairingServer.kt` — the local pairing web page's `<title>`/`<h1>`
  (served to a phone's browser during the QR "pair from phone" flow),
  appears twice (form page + success page)
- `shared/.../data/plex/PlexResourcesApi.kt:23` and
  `shared/.../data/plex/PlexAuthApi.kt:35` — `header("X-Plex-Product",
  "Movies Shumtimes")`, sent to Plex's API and shown in Plex's own
  "authorized devices" list under this name
- `relay/server.js`:
  - Chat overlay page `<title>`/`<h1>` — "Movies Shumtimes — Chat" (lines
    ~382, ~423)
  - Health-check endpoint response text: `'shumtimes relay ok\n'` (line 299)
  - `localStorage` key `shumtimes_chat_name` (lines ~441, ~449) — internal
    storage key, not user-visible, but worth normalizing for consistency

Independent of Parts 1-3 — no shared files, safe to do in any order.

## Part 5 — Relay server package identity + documentation

- `relay/package.json`: `"name"` → the chosen relay name (decision #3),
  `"description"` → "WebSocket relay for Reelay watch-together sync"
- `relay/render.yaml`: `name:` → the chosen relay name — **this is the
  actual deploy trigger for decision #3's hostname change; re-pair both
  devices' relay entries after deploying this.**
- `README.md` — title + body mentions
- `NOTES.md` — title (`# Shumtimes — Project Notes`) + app name/package line
- `docs/design-tokens.md` — title + package-path mention
- `ARCHITECTURE.md` — title, plus package-path references scattered
  throughout (do this pass *after* Part 1's package rename is actually
  merged, since most mentions there are literal code paths, not just prose)

## Confirmed clean — no action needed

- `PlexIdentity.kt`'s `clientIdentifier` is a random UUID with no embedded
  branding string.
- No `User-Agent`/`X-Client-Name`-style header carries the app name in the
  relay protocol (`RelayClient.kt`, `RelayProtocol.kt`) — only the two
  Plex-facing `X-Plex-Product` headers in Part 4 do.
- `docs/kmp-module-scope.md` has no Shumtimes references.
