# Movies Shumtimes

A custom Android TV Plex client with real synchronized "watch together"
playback — built for two people on separate Plex accounts/servers/houses to
watch the same movie in sync. Not on the Play Store; installed by sideloading
the APK. See `NOTES.md` for technical/architecture details.

## Installing the app (for whoever's joining you)

### What you need

- An Android TV device (e.g. an Nvidia Shield) on the same Wi-Fi as whatever
  you're using to get the APK onto it.
- A Plex.tv account that the host has shared their library with (Plex
  Friends) — accept that invite first if you haven't.
- The relay URL (and token, if any) from the host — looks like
  `wss://shumtimes-relay.onrender.com?token=...`. You'll need this for the
  Settings screen once the app's installed.

### Option A — Downloader app (easiest, no computer needed)

1. On the Android TV, install **Downloader** from the app store.
2. Get a link to the APK file from the host (e.g. a Google Drive link set to
   "anyone with the link can view").
3. Open Downloader, paste the link, let it download, then install. You'll be
   prompted to allow installs from Downloader the first time — accept that.

### Option B — adb sideload (if you're comfortable with a terminal, or the
host can walk you through it / remote in)

1. On the TV: Settings → Device Preferences → About → click **Build** 7
   times to unlock Developer Options.
2. Developer Options → **Network debugging** → on. Note the IP address shown
   on screen.
3. From a computer with `adb` and the APK file:
   ```
   adb connect <tv-ip-address>:5555
   adb install movies-shumtimes.apk
   ```
4. Accept the connection prompt that appears on the TV.

### First launch

1. Open **Movies Shumtimes** from the apps list.
2. It shows a short code and `plex.tv/link`. On your phone or any browser,
   go to that address and enter the code.
3. Once logged in, you should see the host's shared library.

### Settings — relay URL

1. From the library screen, open **Settings**.
2. Paste the relay URL the host gave you into the **Relay URL** field
   (include `?token=...` on the end if they gave you one).
3. Save.

### Watching together

1. Both of you open the **same title** from your library.
2. Press Play at roughly the same time. The app finds the other person's
   session over the relay automatically and keeps playback in sync —
   pause/play/seek on either end propagates to the other.
3. The player shows "Sync: connecting…" briefly in the top-right, then it
   disappears once connected. If it says "reconnecting…" for a while, double
   check the relay URL/token in Settings.

## Deploying the relay (for the host)

The relay (`relay/`) needs to be reachable from both devices over the
internet — deployed to Render.com's free tier.

1. Push this repo to GitHub (already done, if you're reading this from it).
2. On [render.com](https://render.com), **New → Blueprint**, point it at
   this repo. It picks up `relay/render.yaml` automatically — a free-tier
   Node web service with a random `RELAY_TOKEN` generated for you.
3. Render gives you a URL like `https://shumtimes-relay.onrender.com` — the
   app needs the WebSocket form: `wss://shumtimes-relay.onrender.com`.
4. Grab the generated `RELAY_TOKEN` from the service's **Environment** tab
   and share both the URL and token with whoever's joining you (they append
   `?token=<token>` to the relay URL in their own Settings screen).
5. Free-tier Render services spin down after inactivity and take a few
   seconds to wake up on the first connection of a session — expect the
   first "Sync: connecting…" to take a little longer than usual.
