# Shared KMP Module — Scoping

What actually moves into a shared Kotlin Multiplatform module, what needs
to change on the way in, and what stays platform-specific. Written by
reading every file in `sync/`, `data/plex/`, `data/settings/`, and
`data/pairing/` and checking their real imports — not assumed from the
package names. See `docs/design-tokens.md` for the UI side of the
cross-platform plan; this is the logic side.

Module shape follows the spike in the interop verification
(`kotlin("multiplatform")`, targets `androidTarget()` +
`tvosArm64()`/`tvosSimulatorArm64()`, `commonMain`/`androidMain`/
`tvosMain` source sets, `XCFramework` output for the Apple side).

## Moves as-is — pure Kotlin, zero platform dependencies

No changes needed beyond a package move; these already have no import
outside `kotlin`/`kotlinx.coroutines`/`kotlinx.serialization`, which are
genuinely multiplatform:

- `sync/ClockSync.kt`
- `sync/PlaybackState.kt`
- `sync/RelayProtocol.kt` (the `RelayEvent` schema itself — this is the
  interop contract from the earlier "will Apple TV/Roku talk to Android"
  discussion; it moving unchanged is exactly what keeps that guarantee)
- `playback/PlaybackDecision.kt` (subtitle-option/burn-required decision
  logic) — portable once its `PlexPart`/`PlexStream`/`PlexMovieDetail`
  dependencies move too (see below)
- `data/plex/PlexImageUrl.kt`
- The data-class portion of `data/plex/PlexServerApi.kt` — lines 16-190
  (`PlexSection`, `PlexLibraryItem`, `PlexSeason`, `PlexEpisode`,
  `PlexStream`, `PlexPart`, `PlexMedia`, `PlexMovieDetail`,
  `PlexOnDeckItem`, `PlexHub`, etc.) are plain data classes with no
  networking code — **this file needs to be split**, not moved whole; the
  `PlexServerApi` class starting at line 193 is the networking half (see
  next section).

## Moves, but the networking needs a dependency swap

OkHttp is JVM/Android-only — it doesn't run on Kotlin/Native, so it can't
ship in `commonMain`. Every file below currently uses `okhttp3.*` for
plain HTTP or WebSockets and needs that swapped for **Ktor's
`HttpClient`**, which is genuinely multiplatform (Darwin engine on
Apple platforms via `NSURLSession`, OkHttp engine on Android/JVM — same
underlying HTTP stack on Android as today, so no behavior change there):

- `sync/RelayClient.kt` — WebSocket client (OkHttp `WebSocket` →
  Ktor's `HttpClient` WebSocket plugin). This is the biggest single
  migration item; it's also the file the whole watch-together sync
  protocol runs through, so it's worth doing carefully and testing
  against the real relay server, not just compiling.
- `data/plex/PlexAuthApi.kt`, `PlexResourcesApi.kt`, the `PlexServerApi`
  class half of `PlexServerApi.kt` — plain request/response HTTP, more
  mechanical swap than `RelayClient`.

**Open question, not yet verified:** whether Ktor's Darwin engine
supports WebSockets on tvOS specifically the same way it does on iOS —
reasonable to expect yes (same `NSURLSession` under the hood, same OS
family), but this project's own recent history is "reasoned-about ≠
verified" (see the KMP-tvOS interop spike), so confirm with a small
Ktor-WebSocket-against-the-real-relay test before relying on it, the same
way the base interop was confirmed rather than assumed.

## Moves, but needs a new platform-agnostic player interface

`sync/HostPlaybackCoordinator.kt`, `sync/GuestPlaybackReconciler.kt`, and
`sync/SyncViewModel.kt` all import `androidx.media3.exoplayer.ExoPlayer`
directly — this is the real architectural gap, bigger than a dependency
swap. This is the sync *policy* logic (clock reconciliation, host
heartbeat, guest drift correction) — genuinely the most valuable code to
share, since it's the trickiest part of the whole app and Apple TV will
use `AVPlayer`, not ExoPlayer.

**Needed:** a small common interface capturing only what this logic
actually touches on the player (roughly: read `currentPosition`/
`duration`/`isPlaying`, call `play()`/`pause()`/`seekTo(ms)`, observe
playback-state/buffering changes) — call it `SyncedPlayer` or similar —
with an `ExoPlayerAdapter` implementation in `androidMain` wrapping the
existing `ExoPlayer` calls one-for-one, and later an `AVPlayerAdapter` in
`tvosMain`. This is a real design task, not a mechanical move: it means
reading exactly which `ExoPlayer`/`Player` methods and properties these
three files actually call (not the whole ExoPlayer surface) and shaping
the interface to that, so the Android adapter is a thin pass-through and
the sync logic itself never sees ExoPlayer or AVPlayer types.

## Needs expect/actual — simple key-value settings

`data/settings/AppSettings.kt` and `data/settings/RelayIdentityStore.kt`
are both `Context` + Jetpack DataStore, storing a handful of flat
key-value pairs (strings/ints/booleans) with `Flow`-based observation —
DataStore itself doesn't exist outside Android, but the actual shape of
what's needed (get/set a handful of typed values, observe changes as a
`Flow`) is a well-covered multiplatform problem. Recommend the
**`multiplatform-settings`** library (Android: DataStore/SharedPreferences
-backed, iOS/tvOS: `NSUserDefaults`-backed, exposes the same
`Flow`/suspend-based `FlowSettings` shape these files already use) over
hand-rolling `expect`/`actual` storage — this is exactly the problem it
exists for, no reason to reinvent it.

`data/plex/PlexIdentity.kt` is the same DataStore pattern (storing one
client-identifier UUID) — same fix.

## Needs expect/actual — secure token storage

`data/plex/TokenStore.kt` is a bigger case: it's not just DataStore, it
does AES-GCM envelope encryption via the **Android Keystore**
(`android.security.keystore.*`, `java.security.KeyStore`,
`javax.crypto.*`) before writing to DataStore. This has no
`multiplatform-settings`-style off-the-shelf equivalent — Android Keystore
and the Apple **Keychain** are genuinely different APIs with different
security models, both need their own implementation.

**Needed:** `expect class SecureTokenStore` (or similar) exposing just
`get()`/`set()`/`clear()` for the Plex auth token, with:
- `actual` on Android: today's existing Keystore-backed implementation,
  moved essentially unchanged.
- `actual` on tvOS: a new implementation calling Keychain APIs
  (`SecItemAdd`/`SecItemCopyMatching`/`SecItemDelete`) via Kotlin/Native's
  `platform.Security` cinterop bindings. This is real, scoped work — a
  known and done-before pattern in the KMP community, but budget for it
  as its own task, not folded into the "mechanical move" pile.

## Stays platform-specific, not part of the shared module

`data/pairing/PairingServer.kt` — a raw `java.net.ServerSocket`/`Socket`
HTTP server used for the QR-code pairing/onboarding flow. `java.net.*`
doesn't exist on Kotlin/Native, and unlike the WebSocket/HTTP-client
cases above, there's no reason to force this through Ktor either — it's a
small, self-contained, one-time-use flow (not core sync logic that both
platforms need to agree on a wire format for), so the pragmatic call is
to leave it Android-only and let a tvOS pairing flow get built natively
against whatever's most natural there, rather than spending shared-module
effort on it.

## Suggested extraction order

Lowest-risk / highest-confidence first, so early phases build confidence
(and a working, testable module) before the harder architectural pieces:

1. **Pure data + protocol types** — `PlaybackState`, `RelayProtocol`,
   the `PlexServerApi.kt` data-class half, `PlexImageUrl`,
   `PlaybackDecision` (+ split `PlexServerApi.kt`). Zero behavior change
   possible here; if it doesn't compile identically, something's wrong
   with the module setup itself, not the logic.
2. **`ClockSync`** — pure logic, no dependencies, but exercises real
   business logic (not just data shapes) through the module boundary.
3. **Settings/identity storage** — pull in `multiplatform-settings`,
   migrate `AppSettings`, `RelayIdentityStore`, `PlexIdentity`. Contained,
   testable in isolation, no dependency on the harder networking work.
4. **HTTP client swap** — `PlexAuthApi`, `PlexResourcesApi`, the
   `PlexServerApi` networking half, onto Ktor. Verify against the real
   Plex server, not just that it compiles.
5. **`RelayClient`** onto Ktor WebSockets — verify the Ktor-WebSocket-on-
   tvOS open question here specifically, against the real relay server.
6. **`SecureTokenStore`** expect/actual — Android side is close to a
   pure move; budget real time for the Keychain `actual`.
7. **`SyncedPlayer` interface + `HostPlaybackCoordinator`/
   `GuestPlaybackReconciler`/`SyncViewModel`** last — the biggest design
   task, and everything it depends on (protocol types, `ClockSync`,
   `RelayClient`) should already be proven in the module by this point.

`PairingServer.kt` never moves — excluded from this order entirely.
