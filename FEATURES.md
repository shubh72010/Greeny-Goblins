# JusPlayer — Complete Feature List

YouTube Music client for Android. Ad-free playback, real audio engineering, deep customization.
This file is generated from what is actually in the source tree (v15.0.0, `versionCode 142`), not from
marketing copy.

- **Package:** `moe.rukamori.archivetune` · **Fork of:** ArchiveTune (GPL-3.0) · **Platform:** Android 8.0+ (`minSdk 26`, `targetSdk 37`)
- **Variants:** `gms` (with Cast) / `foss` (no Google deps) × `mobile` / `tv` × split ABIs
- **Not on Play Store** — distributed as an APK via GitHub Releases

---

## 1. Playback

- Ad-free playback, background playback, and no Premium requirement
- **Dual-engine playback** — InnerTube clients *and* the bundled JusPlayer Engine; resolves both and keeps the better stream
- **Audio quality: Auto / High / Highest / Low**
  - `Highest` resolves both engines concurrently and picks the higher bitrate (ties under 8 kbps broken by codec rank: Opus > AAC > other)
- **Stream client selection:** Android VR, WEB_REMIX, ArchiveTune extractor, JusPlayer Engine, Hi-Res Lossless, iOS, TVHTML5, Android Music, Subsonic
- **Crossfade** with configurable duration, plus **gapless** mode (both share one audio path)
- **EBU R128 loudness normalization**
- **Tempo and pitch control** independently (pitch in semitones *or* multiplier)
- **Skip silence**
- **Audio offload** for low-power hardware decoding
- **Sleep timer** — fixed duration or "end of song"
- **Pause when device volume is 0**, with automatic resume when volume returns
- **Bluetooth auto-start** — resume on connect, with configurable delay and a device allowlist
- **Auto-skip to next track on error**
- **Persistent queue** — restores your last queue on launch
- **Permanent shuffle** — shuffle stays on for every new song
- **Custom seek step**, configurable history window
- **Wake lock** option (keep CPU awake during playback)
- **Low Data Mode** — low-quality streams and playback-only payloads on metered connections
- **Manual PO token generation** (WebView) for Web client playback reliability
- **Playback confirmation fallback** — guides you to confirm/login in YouTube Music when YouTube demands it

## 2. Audio processing

- **In-app equalizer** — multi-band, per-band gain, output gain, presets
- **Bass boost** and **virtualizer** with strength controls
- **Custom EQ profiles** — save, name, import, and export
- Link out to the **system equalizer** / audio session
- **Haptic music visualizer** — vibration synced to bass; continuous or on-beat modes, intensity slider
- External visualizer hooks: **Glyph (Nothing)**, **Flashlight**, **BNMV** (external app, 8888/8889 UDP)
- Beat detection with configurable engine mode and sensitivity

## 3. Library and sources

- YouTube Music account with **multi-account switching** (saved accounts, channels)
- Full library: songs, albums, artists, playlists, liked songs, history, uploaded content
- **Local file playback** (device music) with its own browse surface
- **Downloads / offline playback** with per-item and bulk management
- **Subsonic / OpenSubsonic support** — Navidrome, Airsonic-Advanced, Gonic, Ampache
  - Server search, server library browsing, music-folder filtering
  - **Matched playback** — a queued YouTube track plays the server copy when it exists
  - **"Play only server tracks"** — skip anything not on your server instead of falling back to YouTube
- **Spotify integration** — playlist import, playlist browsing, and Spotify-backed playback resolution
- **Playlist import: CSV** and **online playlist import**
- **External downloader support** — hand off to an installed app (e.g. yt-dlp) by package name
- Create/edit playlists, **change playlist cover**, duplicate handling ("skip duplicates" / "add anyway")
- **Hidden playlists** — hide from library, manage hidden list separately
- **Playlist tags** and a tag filter for library browsing
- **Custom artist separators** — define which symbols split multi-artist credits
- Edit song metadata locally (title, artists)
- **Backup and restore** of the whole app's data

## 4. Lyrics

- **12 lyric sources**, individually toggleable and **priority-reorderable:**
  LRCLIB, KuGou, BetterLyrics, YouLyPlus, SimpMusic, Unison, and Paxsenix providers for Apple Music,
  Musixmatch, Spotify, NetEase, YouTube, plus the built-in YouTube provider
- **Synced (line-by-line)**, **word-by-word** ("Word Sync"), and plain lyrics
- **Lyrics search across providers** with per-result badges (Synced / Plain), line and character counts
- **Translation** of lyrics into a target language (incl. AI-assisted translation)
- **Romanization** for Japanese, Korean, Chinese, Hindi and a generic fallback
- Lyric animations, text size, line spacing, line blur, click-to-seek, custom scroll key
- Lyrics displayed on the player, in a dedicated view, or full-screen with controls
- **Queue lyric preloading** with configurable count
- **Lyrics filtering (clean mode)** — custom word list, default list, censor characters, and
  **skip the filtered part in the audio**
- Lyrics caching + cache clearing
- **Lyric share cards** (with canvas video support) for social posting

## 5. Discovery

- Home feed: quick picks, speed dial, forgotten favorites, keep listening, account playlists,
  similar-to, new releases, remote sections
- **Quick picks** with configurable list length and display mode
- **Speed dial** — pin your go-to songs, reorder, remove
- **Start radio** from any song/artist, **auto-radio** on song taps
- Charts, moods & genres, new releases, news
- **"You might like"** playlist suggestions — sourced from playlist title, content, or both
- **Don't recommend this song** — hide from recommendations for 6 h / 1 day / 1 week / 1 month
- Search YouTube Music, search your library, filter by type (songs, videos, albums, artists,
  playlists, community and featured playlists)

## 6. HyprMix — skip-aware queue

- Records play sessions locally (start point + duration played); nothing leaves the device
- **Learns repeated intros** — consistent skips become a per-song start point
- **Learns skips** — songs you repeatedly bail on are down-ranked (Bayesian-smoothed, so one bad
  session can't bury a song)
- **Auto-skip learned intros** (only for songs started from HyprMix)
- Per-song queue diagnostics: plays, finishes, skips, "after a skip", score, last played
- Edit or reset a learned start point manually
- Delete all learning data at once

## 7. AI features

- **AI Integration settings** — any OpenAI-compatible provider: base endpoint, API key, model,
  connection test, model listing
- **AI Top Mixes** — builds personal mixes from your recent listening history, stored locally,
  refreshable on demand
- **AI lyric translation**
- Optional Discord RPC field translation before publishing

## 8. Recognition and analysis

- **Music recognition (Shazam-style)** — identify the song playing around you
  - Works from the **microphone** or **on-device audio capture** (recognizes audio from other apps)
  - **Background recognition service** with notification
  - **Quick Settings tile** and app shortcuts
- **Music Intelligence** — on-device audio fingerprinting/DSP analysis; a song played past 80%
  gets analyzed and appears in the Music Intelligence screen (nothing uploaded)

## 9. Music Together (listen together)

- **Host or join** a live session — **LAN** (no server needed) or **Online** (relay server, join link or code)
- Session code plus shareable join link
- Host controls: **guests can add songs**, **guests can control playback**, **require approval**
- Participant management: approve/reject, kick, ban, transfer host
- **Live activity log** ("X changed the song to Y", "X skipped to Y", joins, host transfers…)
- Host authority model — guests send requests, host applies them, everyone stays in sync
- Welcome/onboarding flow explaining the permissions model

## 10. Integrations

- **Last.fm scrobbling** (now-playing + scrobbles, minimum duration, scrobble delay %, delay seconds)
- **Libre.fm** and **custom scrobbling services** (custom endpoint + API key/secret overrides)
- **ListenBrainz** scrobbling with token
- **Discord Rich Presence** — OAuth login, activity name/details/state, activity type
  (playing/streaming/listening/watching/competing), presence status, platform spoof
  (Desktop/Xbox/Samsung/iOS/Android/Embedded/PS4/PS5), large/small images and text,
  up to two buttons with URL sources (song/artist/album/custom), show-when-paused,
  manual refresh, re-authorize on token expiry
- **Android Auto** — browseable library tree (home rows, quick picks, recent, liked, songs,
  artists, albums, playlists) with content-style hints
- **Google Cast** — device picker sheet, connect/disconnect (GMS edition)
- **App update channels** — Stable and Canary, in-app update notification, changelog and
  recent-commit view, APK download

## 11. System surfaces

- Rich **media notification** with controls
- **8 home-screen widgets**: music, album art, now-playing card, playback capsule, playback deck,
  playback spotlight, playback command, **Listening Insights** (time listened, top song, genres)
- **AOD mode** (always-on display player) with a Quick Settings tile, plus 20 customization rows:
  thumbnail shape/size/rotation/visibility, artist/album/progress/time labels/controls/exit button,
  background style, accent style, content position, text alignment, control style and size,
  horizontal padding, vertical spacing, title max lines, ambient intensity
- **Lockscreen / notification controls**, per-app media session
- **TV variant** — full TV build with focus-driven navigation rail
- **Adaptive layouts** — phone, tablet, foldable, big-screen mode with side rail
- Shake-to-skip and gesture customization

## 12. Look and feel

- **Material 3 Expressive** UI
- **Artwork-driven dynamic color**, or
- **69 named color palettes** (Ocean, Midnight, Emerald, Spotify, Sunset, Royal Purple, Sakura,
  Cyberpunk, Synthwave, Aurora, Rainbow, pastel and neon sets…), plus a custom color picker
- **Random palette on every app start** option
- **10 player designs** (V1–V10)
- **8 player backgrounds** — default, gradient, custom image, blur, coloring, blur+gradient,
  glow, animated glow
- Custom background image with blur, contrast and brightness sliders
- **Modular player layout editor** — add/remove/reorder components (cover art, seek bar, time,
  play/pause, like, previous, repeat, canvas artwork, title + artist, spacer), with Classic /
  Compact / Minimal presets and reset
- Mini player with anchor position and background style options
- **5 slider styles** — standard, wavy, thick, circular, simple
- **Canvas artwork** — animated cover art from YouTube Music or Apple Music, selectable source,
  with configurable cache size
- **Liquid glass / blur** effects with adjustable radius, album-art backdrop blur amount
- Pure black (AMOLED) theme, follow system / on / off
- Custom **font** (default, system, or your own .ttf)
- **App icon picker** — built-in and community icons
- Haptic feedback on interactive elements
- Animation toggles and **force high refresh rate** (up to the panel's max)
- Thumbnail corner radius, crop-to-square, per-shape (song/artist/AOD), random shapes
- Custom thumbnail swipe action

## 13. Interface and navigation

- Tabs: Home, Search, Library, Mood & Genres, with configurable default tab
- Grid or list views per content type, configurable item sizes
- Rich sorting everywhere: date added, name, artist, play time, most played, year, duration,
  A–Z/Z–A, ascending/descending, custom order
- Library filters: all / liked / downloaded, songs / videos / albums / artists / playlists
- Queue editing with lock, reorder, remove, "play next", "add to queue"
- Playlist edit lock
- **Searchable settings with deep links** (240 indexed rows — searching "theme" or "proxy"
  jumps to the exact toggle)
- Swipe-to-song, swipe gesture tuning, haptics
- Multi-select actions across library items

## 14. Content filtering

- **Hide explicit content**
- **Hide music videos**
- **Hide blocked titles** (Montagem / Funk style titles)
- Filter mode selection
- **Censored lyrics** with your own word list
- **Skip filtered parts in the audio**
- Pause/clear listen history, pause/clear search history

## 15. Privacy and networking

- No ads, no telemetry, no analytics — open source (GPL-3.0)
- **Proxy support** — HTTP/SOCKS, auth, connection test
- **DNS over HTTPS** with provider selection or custom URL
- **Bypass proxy for streams** (avoids IP mismatch on playback)
- **IP rotation** — refresh IP when playback/content breaks, active proxy count
- **Block screenshots and Recents previews**
- All listening stats, fingerprints, and HyprMix learning stay on-device

## 16. Data and storage

- **Smart Trimmer** — automatic cache pruning
- Separate limits and clear actions for: image cache, song cache, canvas cache, lyrics cache, downloads
- Choose a **custom storage folder** (SAF) for downloads
- **Backup / restore** of the full app state

## 17. Diagnostics

- **Nerd stats** overlay — codec, bitrate, sample rate, content length, buffer health, playback
  speed, state, media id
- **Codec badge on the player**
- **Stream source badge** — shows whether the stream came from InnerTube, extractor, engine, or Subsonic
- In-app **log viewer** with levels, filters, share/export
- Debug screen, lyrics-video pipeline debug flags
- Changelog and release notes

## 18. Localization

- 27 locale resource sets; app language and *content* language/country are independent
- Fresh installs default to English

---

## Requirements and limits (read before promising anything)

| Feature | Needs |
|---|---|
| Library, playlists, sync | YouTube Music account (optional for local files/Subsonic) |
| Any online content | YouTube Music available in your region (or proxy/DoH/IP rotation) |
| AI Top Mixes, AI translation | Your own AI provider endpoint + API key |
| Subsonic | A Subsonic/OpenSubsonic server |
| Spotify import / playback | Spotify account |
| Music Together **Online** rooms | The project's relay server reachable; LAN rooms work without it |
| Google Cast | `gms` build |
| TV layout | `tv` build |
| Glyph / flashlight / BNMV visualizers | The external companion app for the relevant hardware |
| Scrobbling | Last.fm / Libre.fm / ListenBrainz account |
| Release signing | Keystore credentials (see AGENTS.md) |

Not included: SponsorBlock, Wear OS app, iOS, desktop.
