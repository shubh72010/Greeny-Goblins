<div align="center">

<img src="./assets/logo.svg" width="120" height="120" alt="JusPlayer icon">

# JusPlayer

**A YouTube Music client for Android.**

Fork of [ArchiveTune](https://github.com/rukamori/ArchiveTune) by Rukamori · GPL-3.0 · Android 8.0+<br>
_docs: [English](README.md) · [日本語](README_JA.md)_

[**⬇ Download the APK**](https://github.com/shubh72010/Greeny-Goblins/releases/latest) &nbsp;·&nbsp; [All releases](https://github.com/shubh72010/Greeny-Goblins/releases) &nbsp;·&nbsp; [Full feature list](FEATURES.md) &nbsp;·&nbsp; [Discord](https://discord.gg/8VDJB7HztQ)

<img src="https://img.shields.io/github/v/release/shubh72010/Greeny-Goblins?label=release&color=6366f1" alt="Latest release" />
<img src="https://img.shields.io/github/downloads/shubh72010/Greeny-Goblins/total?label=downloads&color=6366f1" alt="Downloads" />
<img src="https://img.shields.io/badge/license-GPL--3.0-6366f1" alt="GPL-3.0" />

<img src="./fastlane/metadata/android/en-US/images/phoneScreenshots/screenshot_7.png" width="24%" alt="Player" />
<img src="./fastlane/metadata/android/en-US/images/phoneScreenshots/screenshot_2.png" width="24%" alt="Live lyrics" />
<img src="./fastlane/metadata/android/en-US/images/phoneScreenshots/screenshot_1.png" width="24%" alt="Browse" />
<img src="./fastlane/metadata/android/en-US/images/phoneScreenshots/screenshot_4.png" width="24%" alt="Listening statistics" />

</div>

---

## What this is

A music player that plays from three places: your YouTube Music account, local files on the
device, and a Subsonic/OpenSubsonic server (Navidrome, Airsonic-Advanced, Gonic, Ampache). No ads,
no account required for local or Subsonic playback, no telemetry.

It's a fork, so most of the app's bones are ArchiveTune's. What I've added on top is listed under
[What's different](#whats-different) and in detail in [FEATURES.md](FEATURES.md) — that file is
written from the source tree, not from this page.

> [!IMPORTANT]
> **Region:** if YouTube Music doesn't serve your country, you need a VPN/proxy set to a supported
> region for the first fetch. The app has built-in proxy, DoH and IP-rotation settings for this.

---

## What's different

Six things that are the actual reason to use this fork over anything else. Each one is in the code,
not in the pitch:

- **Dual-engine playback.** InnerTube clients and the bundled JusPlayer Engine both resolve the
  track; at `Highest` quality the app picks the higher bitrate and breaks ties under 8 kbps by
  codec rank (Opus > AAC > other). The player shows which engine won.
- **Crossfade and gapless on one audio path.** Audio blends over a second `ExoPlayer` while the UI
  switches on the tap frame, so taps never look ignored and the queue never snaps back mid-fade.
- **Subsonic matched playback.** A queued YouTube track plays your server's copy when it exists,
  with a "server only" mode that skips what your server doesn't have instead of falling back.
- **HyprMix.** Learns your skipped intros and songs you keep bailing on, entirely on-device
  (Bayesian-smoothed, so one bad session can't bury a track). Nothing is uploaded.
- **Music Together.** Host or join a listening session over LAN (no server) or online. Guests send
  requests, the host applies them, everyone stays in sync.
- **Searchable settings.** 240 indexed rows — type "theme" or "proxy" and it deep-links to the
  exact toggle.

Plus: 10 player designs, 8 player backgrounds, 69 palettes + custom, modular player layout editor,
word-by-word lyrics from 12 sources, 8 home-screen widgets, an AOD player, Android Auto, Google Cast
(`gms` build), Last.fm / Libre.fm / ListenBrainz scrobbling, Discord rich presence, and a full TV
build.

→ [Everything else, feature by feature](FEATURES.md)

---

## Install

Download the APK from [Releases](https://github.com/shubh72010/Greeny-Goblins/releases/latest) and
install it. Two flavours:

| Build | Use it if |
|---|---|
| `gms` | You want Google Cast. Pulls in Google Play Services. |
| `foss` | You want no Google dependencies at all. No Cast. |

The app has a built-in updater with Stable and Canary channels, so you don't have to re-download
by hand.

> [!WARNING]
> The GitHub Releases page above is the only trusted download source. Builds from anywhere else
> are not mine.

**What you need for what:** YouTube Music account for library/playlists · Subsonic server for
Subsonic playback · Spotify account for playlist import · your own OpenAI-compatible endpoint for
the optional AI mixes/translation · `gms` build for Cast · `tv` build for the TV layout.

**Not here:** SponsorBlock, a Wear OS app, iOS, desktop, and no Google Play listing. This is an
independent third-party client — not affiliated with Google or YouTube.

---

## Build it

JDK 21. The project has 3 flavour dimensions (`gms`/`foss` × `mobile`/`tv` × `universal`/per-ABI),
so every task needs a variant name — bare `assembleDebug` tries to build all 20 and will not do what
you want.

```bash
./gradlew assembleGmsMobileUniversalDebug              # one debug APK
./gradlew :app:compileGmsMobileUniversalDebugKotlin    # fastest correctness check
./gradlew :app:lintGmsMobileUniversalDebug             # lint
./gradlew :app:testGmsMobileUniversalDebugUnitTest     # unit tests
./gradlew assembleGmsMobileUniversalRelease            # signed release (needs keystore secrets)
```

Full setup, signing keys and architecture notes: [CONTRIBUTING.md](CONTRIBUTING.md) ·
[ARCHITECTURE.md](ARCHITECTURE.md).

---

## How this is built

Written by me, with AI assistance. I use whatever free models happen to be available in
[opencode](https://opencode.ai) at the time — they change weekly, so there's no single model to
credit — plus the assistants' own tooling. I review every change, run the build and tests, test on
real devices, and decide what ships. Design, feature decisions and the crossfade/playback behaviour
are mine. If it's broken, it's my bug, not a model's.

I'm saying this up front because you're going to see it in the commit history anyway, and because a
music player should be honest about where its audio and your data come from — same principle.

Found a real bug? [Open an issue](https://github.com/shubh72010/Greeny-Goblins/issues/new/choose)
with steps and a log from the in-app log viewer. That's more useful than telling me it looks
generated.

---

## Translating

The app ships 27 locale sets and is translated on Weblate. Corrections are welcome — especially if
a string reads like machine output in your language.

[![Translation status](https://translate.codeberg.org/widget/archivetune/horizontal-blue.svg)](https://translate.codeberg.org/engage/archivetune/)

---

## Credits

- [ArchiveTune](https://github.com/rukamori/ArchiveTune) by Rukamori — the fork base.
- [Metrolist](https://github.com/mostafaalagamy/Metrolist) by Mostafa Alagamy — the underlying framework.
- [SimpMusic](https://github.com/maxrave-dev/SimpMusic) by maxrave-dev — lyrics API provider.
- [BetterLyrics](https://better-lyrics.boidu.dev/) — word-by-word lyrics, unison and artwork providers.
- [Material Color Utilities](https://github.com/material-foundation/material-color-utilities).
- [Read You](https://github.com/Ashinch/ReadYou) and [Seal](https://github.com/JunkFood02/Seal) — UI inspiration.
- Everyone who filed an issue, tested a build, or fixed a translation.

**Maintainers:** shubh72010 (lead) · oliver-lebaigue-2-bright-bench (contributor)

---

## License

GPL-3.0. Third-party client; not affiliated with Google LLC or YouTube; doesn't bypass YouTube's
technical protections. Please buy the music you love from the artists.
