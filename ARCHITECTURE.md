# ArchiveTune — Architecture & Codebase Analysis

> **App:** ArchiveTune (v13.4.0, codename: Koiverse)  
> **Package:** `moe.rukamori.archivetune`  
> **License:** GPL-3.0  
> **Author:** Rukamori (github.com/rukamori)  
> **Website:** [archivetune.koiiverse.cloud](https://archivetune.koiiverse.cloud)  
> **Server API:** `https://archivetune-api.koiiverse.cloud`  

ArchiveTune is a **privacy-focused, feature-rich YouTube Music client for Android**, built entirely from scratch with Kotlin and Jetpack Compose. It is not a wrapper — it implements its own InnerTube (YouTube Music) client, its own playback engine on top of Media3/ExoPlayer, and aggregates lyrics from 14+ providers.

---

## Table of Contents

1. [Project Structure](#1-project-structure)
2. [Build System & Gradle](#2-build-system--gradle)
3. [Library Modules Deep-Dive](#3-library-modules-deep-dive)
4. [App Module Architecture](#4-app-module-architecture)
5. [Data Layer (Room Database)](#5-data-layer-room-database)
6. [Playback Engine (MusicService)](#6-playback-engine-musicservice)
7. [UI Layer (Jetpack Compose)](#7-ui-layer-jetpack-compose)
8. [Lyrics Pipeline](#8-lyrics-pipeline)
9. [Social Listening (Together)](#9-social-listening-together)
10. [External Integrations](#10-external-integrations)
11. [Dependency Graph](#11-dependency-graph)
12. [Data Flow Walkthrough](#12-data-flow-walkthrough)
13. [Configuration & Preferences](#13-configuration--preferences)

---

## 1. Project Structure

```
Greeny-Goblins/                      # Root project (Android)
├── app/                             # Main Android application module
│   ├── src/
│   │   ├── main/
│   │   │   ├── kotlin/moe/rukamori/archivetune/
│   │   │   │   ├── App.kt                    # @HiltAndroidApp Application class
│   │   │   │   ├── MainActivity.kt            # Single-activity Compose host
│   │   │   │   ├── DebugActivity.kt           # Crash handler activity
│   │   │   │   ├── BuildInfo.kt               # Version info utilities
│   │   │   │   ├── AnimationPreferences.kt    # Animation style settings
│   │   │   │   ├── ai/                        # AI integration (lyrics translation, playlist suggestions)
│   │   │   │   ├── constants/                 # Preference keys, media session commands, enums
│   │   │   │   ├── db/                        # Room database (17 entities, DAO, converters)
│   │   │   │   ├── di/                        # Hilt DI modules
│   │   │   │   ├── extensions/                # Kotlin extensions (Context, ExoPlayer, Strings, etc.)
│   │   │   │   ├── library/                   # Library top mixes use case
│   │   │   │   ├── localmedia/                # Local audio file scanner
│   │   │   │   ├── lyrics/                    # 12+ LyricsProvider implementations + LyricsHelper
│   │   │   │   ├── models/                    # Serializable models (MediaMetadata, PersistQueue, etc.)
│   │   │   │   ├── musicrecognition/          # ShazamKit integration (QS tile)
│   │   │   │   ├── network/                   # Connectivity monitoring
│   │   │   │   ├── playback/                  # MusicService, PlayerConnection, queues, EQ, sleep timer
│   │   │   │   ├── repository/                # News & library top mix repositories
│   │   │   │   ├── spotify/                   # Spotify integration (login, library, queue)
│   │   │   │   ├── storage/                   # Storage location management
│   │   │   │   ├── together/                  # Social listening (WebSocket server/client)
│   │   │   │   ├── ui/                        # COMPOSE UI (30+ screens, 80+ components)
│   │   │   │   │   ├── component/             # Reusable composables (buttons, dialogs, lyrics, etc.)
│   │   │   │   │   ├── menu/                  # Context menus (song, album, artist, playlist)
│   │   │   │   │   ├── player/                # Full-screen player, mini-player, AOD, lyrics, queue
│   │   │   │   │   ├── screens/               # All screens (home, search, library, settings, etc.)
│   │   │   │   │   ├── svg/                   # SVG rendering utilities
│   │   │   │   │   ├── theme/                 # M3 theming, typography, color extraction, palette
│   │   │   │   │   └── utils/                 # UI utilities (scroll, nav, layout, etc.)
│   │   │   │   ├── utils/                     # App-level utilities (DataStore, network, coil, etc.)
│   │   │   │   ├── viewmodels/                # 34 Hilt ViewModels
│   │   │   │   └── widget/                    # 7 Glance widgets
│   │   │   └── AndroidManifest.xml
│   │   ├── debug/                             # Debug resources (shortcuts)
│   │   ├── gms/                               # Google Play Services manifest
│   │   └── tv/                                # Android TV manifest & banner
│   └── build.gradle.kts
│
├── innertube/                       # YouTube Music InnerTube API client (JVM library)
├── spotifycore/                     # Spotify API client (internal GraphQL + REST)
├── paxsenix/                        # Multi-source lyrics framework (Netease, Apple Music, etc.)
├── betterlyrics/                    # TTML lyrics parser (BetterLyrics format)
├── canvas/                          # Animated album artwork (ArchiveTune Canvas API)
├── kugou/                           # KuGou music lyrics API
├── lrclib/                          # LrcLib lyrics API
├── lastfm/                          # Last.fm API (scrobbling, auth)
├── unison/                          # Unison lyrics API
├── simpmusic/                       # SimpMusic lyrics format support
├── shazamkit/                       # Shazam music recognition
├── server/                          # (Empty - placeholder for relay server)
│
├── assets/                          # App assets (Announcements, badges, icons)
├── fastlane/                        # App metadata for stores
├── gradle/                          # Version catalog (libs.versions.toml)
└── .github/                         # CI/CD workflows
```

---

## 2. Build System & Gradle

### 2.1 Technology Stack

| Component | Version | Purpose |
|-----------|---------|---------|
| AGP | 9.2.1 | Android Gradle Plugin |
| Kotlin | 2.4.0 | Language & Compose compiler |
| KSP | 2.3.9 | Symbol processing (Room, Hilt) |
| Compose | 1.12.0-alpha03 | UI toolkit |
| Material 3 | 1.5.0-alpha21 | Design system |
| Media3 (ExoPlayer) | 1.10.1 | Playback engine |
| Ktor | 3.5.0 | HTTP client/server (networking, Together WebSocket) |
| Hilt | 2.59.2 | Dependency injection |
| Room | 2.8.4 | Local database |
| Coil | 3.4.0 | Image loading |
| Glance | 1.1.1 | App widgets |

### 2.2 Build Variants (27 combinations)

The app uses 3 flavor dimensions:

```
distribution: gms (default) | foss | izzy
device:       mobile | tv
abi:          universal | arm64 | armeabi | x86 | x86_64
```

- **gms**: Full Google Play Services (Discord, default)
- **foss**: No Google dependencies
- **izzy**: FOSS variant for IzzyOnDroid repo (no auto-updater)

### 2.3 Version Catalog (`gradle/libs.versions.toml`)

Centralized dependency management via Gradle version catalog. Key libraries:

- **Networking:** Ktor (client + server), OkHttp (DNS-over-HTTPS), jsoup
- **Compose:** runtime, foundation, ui, animation, material3, reorderable
- **Media:** Media3 (exoplayer, session, hls, okhttp data source), squigglyslider
- **Database:** Room (runtime, compiler, ktx)
- **DI:** Hilt (with KSP compiler)
- **Images:** Coil 3 (with GIF, OkHttp network)
- **Markdown:** Markwon (core, strikethrough, tables, tasklist, HTML, image, linkify)
- **Widgets:** Glance (appwidget, material3)
- **Accompanist:** lyrics-ui, lyrics-core
- **Utilities:** Guava, Timber, Brotli, Rhino, RE2J, Kuromoji

---

## 3. Library Modules Deep-Dive

### 3.1 `:innertube` — YouTube Music InnerTube Client

**Location:** `innertube/`  
**Build:** JVM library (Kotlin/JVM with serialization)  
**Dependencies:** Ktor (core, okhttp, content-negotiation, encoding), Brotli, NewPipe Extractor, RE2J, Rhino  
**Size:** ~1875 lines in YouTube.kt, ~865 lines in InnerTube.kt, ~30+ model/page files

This is the **heart of ArchiveTune** — it replaces the official YouTube Music API with a fully reimplemented InnerTube protocol client.

**Key classes:**

| Class | File | Purpose |
|-------|------|---------|
| `InnerTube` | `InnerTube.kt` | Low-level HTTP client for InnerTube endpoints. Handles authentication (cookie, PoToken, visitor data), request signing, proxy rotation, DNS-over-HTTPS |
| `YouTube` | `YouTube.kt` | High-level API: search, browse, home feed, playlists, albums, artists, streaming URL resolution, library management, account info, history. 1875 lines of endpoint logic + page parsing |
| `PlaybackAuthState` | `PlaybackAuthState.kt` | Manages authentication state for playback (PoToken, visitor data, session ID, fingerprint) |
| `RotatingProxySelector` | `proxy/RotatingProxySelector.kt` | Proxy rotation for IP rotation |
| `RotatingProxyClient` | `proxy/RotatingProxyClient.kt` | Ktor HttpClient wrapper with proxy rotation |
| `ProxyConfig` | `proxy/ProxyConfig.kt` | Proxy configuration model |

**Page parsers** (under `pages/`):
- `HomePage.kt` — YouTube Music home feed
- `SearchPage.kt` / `SearchSummaryPage.kt` / `SearchSuggestionPage.kt` — Search
- `AlbumPage.kt` — Album detail
- `ArtistPage.kt` / `ArtistItemsPage.kt` / `ArtistItemsContinuationPage.kt` — Artist pages
- `PlaylistPage.kt` / `PlaylistContinuationPage.kt` — Playlists
- `NextPage.kt` — Next/related content
- `LibraryPage.kt` / `LibraryContinuationPage.kt` / `LibraryAlbumsPage.kt` — User library
- `ChartsPage.kt` / `MoodAndGenres.kt` / `ExplorePage.kt` / `NewReleaseAlbumPage.kt` — Discovery
- `HistoryPage.kt` — Listening history
- `RelatedPage.kt` — Related content
- `BrowseResult.kt` — Generic browse result

**Renderer models** (under `models/`):
- `MusicCarouselShelfRenderer.kt`, `MusicShelfRenderer.kt`, `MusicResponsiveListItemRenderer.kt`, `MusicCardShelfRenderer.kt`, `MusicPlaylistShelfRenderer.kt`, etc.

**Features:**
- Search suggestions, search results
- Home feed, explore, moods & genres, charts, new releases
- Album/artist/playlist page parsing
- Streaming URL resolution (with format selection, client profiles: ANDROID_VR, WEB_REMIX, IOS, TVHTML5, ANDROID_MUSIC, HI_RES_LOSSLESS)
- Playback authentication (PoToken generation, visitor data, session cookies)
- Account login (cookie-based), library management, history
- Proxy support (HTTP/SOCKS), IP rotation, DNS-over-HTTPS
- BotGuard token generation (anti-bot challenge solving)
- Video transcript fetching (for subtitle-based lyrics)

### 3.2 `:spotifycore` — Spotify API Client

**Location:** `spotifycore/`  
**Build:** JVM library (Kotlin/JVM with serialization)  
**Dependencies:** Ktor (core, okhttp, content-negotiation, encoding)  
**Size:** ~1642 lines in Spotify.kt

**Purpose:** Internal Spotify API client that uses **both** the private GraphQL API (`api-partner.spotify.com`) and the public REST API (`api.spotify.com/v1/`).

**Key classes:**

| Class | File | Purpose |
|-------|------|---------|
| `Spotify` | `Spotify.kt` | Main API client — login with `sp_dc`/`sp_key` cookies, fetch playlists, library, recommendations |
| `SpotifyAuth` | `SpotifyAuth.kt` | Authentication handling |
| `SpotifyMapper` | `SpotifyMapper.kt` | Maps Spotify models to internal models |
| `SpotifyHashProvider` | `SpotifyHashProvider.kt` | Hashing utilities for deduplication |

**Models:** Playlist, Track, Album, Artist, User, Token, SearchResult, Recommendations, Paging, LibraryItem, HomeFeed

**Features:**
- Cookie-based authentication (`sp_dc` + `sp_key`)
- Fetch user playlists, saved tracks, followed artists
- Search across Spotify catalog
- Get recommendations
- Home feed (made for you, recently played)
- Uses GraphQL internally; falls back to REST

### 3.3 `:paxsenix` — Multi-Source Lyrics Framework

**Location:** `paxsenix/`  
**Build:** JVM library (Kotlin/JVM with serialization)  
**Dependencies:** Ktor (core, cio, okhttp, content-negotiation, logging)

**Purpose:** Unified framework for fetching lyrics from multiple Chinese/international sources.

**Sub-providers managed within paxsenix:**

| Source | Model file | Description |
|--------|-----------|-------------|
| Apple Music | `AppleMusicModels.kt` | Apple Music lyrics API |
| Netease | `NeteaseModels.kt` | Netease Cloud Music lyrics |
| Generic | `GenericPaxsenixModels.kt` | Shared models for Musixmatch, Spotify, YouTube |

**Key class:**
- `PaxsenixLyrics.kt` — Entry point, sets User-Agent, dispatches to sub-providers

**Features:**
- Aggregates lyrics from Apple Music, Netease, Musixmatch, Spotify, YouTube
- Server selection based on availability/quality

### 3.4 `:betterlyrics` — TTML Lyrics Parser

**Location:** `betterlyrics/`  
**Build:** JVM library (Kotlin/JVM with serialization)  
**Dependencies:** Ktor (core, okhttp, content-negotiation, encoding)

**Purpose:** Parses TTML (Timed Text Markup Language) subtitles into synced lyrics format.

**Key classes:**
- `BetterLyrics.kt` — Fetches and parses TTML lyrics from YouTube Music's "Lyrics" / "Description" / "Subtitles" sources
- `TTMLParser.kt` — XML TTML parser (handles `<tt>`, `<body>`, `<div>`, `<p>` elements with timing)
- `TestTTML.kt` — Standalone test harness for TTML parsing

### 3.5 `:canvas` — Animated Artwork (ArchiveTune Canvas)

**Location:** `canvas/`  
**Build:** JVM library (Kotlin/JVM with serialization)  
**Dependencies:** Ktor (core, okhttp, content-negotiation, encoding)

**Purpose:** Fetches animated album artwork from the ArchiveTune Canvas API service.

**Key classes:**
- `ArchiveTuneCanvas.kt` — Initializes with bearer token, fetches canvas artwork for a track
- `AppleMusicProvider.kt` — Apple Music canvas (animated artwork) provider

**Models:**
- `CanvasArtworkModel.kt` — Canvas artwork response model

**Server:** Canvas API is hosted at `https://archivetune-api.koiiverse.cloud`

### 3.6 Other Library Modules

All are **JVM libraries** (`kotlin.jvm` + `kotlin.serialization`) using Ktor for HTTP:

| Module | Purpose | Key File | API |
|--------|---------|----------|-----|
| `:kugou` | KuGou lyrics | `KuGou.kt` | KuGou search/download lyrics API |
| `:lrclib` | LrcLib lyrics | `LrcLib.kt` | LrcLib public API |
| `:lastfm` | Last.fm scrobbling | `LastFM.kt` | Last.fm API (auth, scrobble, now playing) |
| `:unison` | Unison lyrics | `Unison.kt` | Unison lyrics API |
| `:simpmusic` | SimpMusic lyrics | `SimpMusicLyrics.kt` | SimpMusic lyrics format |
| `:shazamkit` | Music recognition | `Shazam.kt` + `ShazamSignatureGenerator.kt` | Shazam audio fingerprinting & recognition |

---

## 4. App Module Architecture

### 4.1 Architectural Pattern: MVVM + Service Layer

```
┌──────────────────────────────────────────────────────────────┐
│                        UI Layer (Compose)                     │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────────────┐  │
│  │ Screens  │ │Components│ │  Menus   │ │  BottomSheet   │  │
│  │ (30+)    │ │ (48+)    │ │ (16)     │ │  Player  (15)  │  │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └───────┬────────┘  │
│       │            │            │               │           │
│  ┌────▼────────────▼────────────▼───────────────▼────────┐  │
│  │              ViewModels (34 @HiltViewModel)             │  │
│  │  Home, Search, Library, Player, Settings, Stats, etc.  │  │
│  └────────────────────────┬───────────────────────────────┘  │
├───────────────────────────┼───────────────────────────────────┤
│                    Data Layer                                 │
│  ┌────────────────────────┼───────────────────────────────┐  │
│  │          ┌─────────────▼──────────────┐                │  │
│  │          │    Room Database (SQLite)   │               │  │
│  │          │ 17 Entities, 3 Views, DAO  │               │  │
│  │          └────────────────────────────┘               │  │
│  │  ┌──────────┐ ┌───────────┐ ┌────────────────────┐   │  │
│  │  │ DataStore│ │ Network   │ │ YouTube/Spotify/   │   │  │
│  │  │ (Prefs)  │ │ (Monitor) │ │ Library Modules    │   │  │
│  │  └──────────┘ └───────────┘ └────────────────────┘   │  │
│  └───────────────────────────────────────────────────────┘  │
├──────────────────────────────────────────────────────────────┤
│                    Service Layer                              │
│  ┌──────────────────────────────────────────────────────────┐│
│  │          MusicService (MediaLibraryService)              ││
│  │  ┌──────────┐ ┌──────────┐ ┌────────┐ ┌──────────────┐ ││
│  │  │ ExoPlayer│ │ Media3   │ │ Queue  │ │ EQ, Crossfade│ ││
│  │  │          │ │ Session  │ │ (5 impl)│ │ Sleep Timer  │ ││
│  │  └──────────┘ └──────────┘ └────────┘ └──────────────┘ ││
│  │  ┌──────────┐ ┌──────────┐ ┌──────────────────────────┐ ││
│  │  │ Download │ │ Scrobble │ │ Discord RPC, Together,   │ ││
│  │  │ Service  │ │ (LF/LB)  │ │ Widgets, AudioFocus, etc │ ││
│  │  └──────────┘ └──────────┘ └──────────────────────────┘ ││
│  └──────────────────────────────────────────────────────────┘│
└──────────────────────────────────────────────────────────────┘
```

### 4.2 Dependency Injection (Hilt)

| Module | Install Site | Provides |
|--------|-------------|----------|
| `AppModule` | `SingletonComponent` | `MusicDatabase`, `DatabaseProvider`, `@PlayerCache` (SimpleCache, 512MB LRU), `@DownloadCache` (unbounded SimpleCache) |
| `NetworkModule` | `SingletonComponent` | `NetworkConnectivityObserver` |
| `LyricsHelperEntryPoint` | `SingletonComponent` | `@EntryPoint` for non-Hilt classes (MusicService) to inject `LyricsHelper`, `LyricsPreloadManager` |

- **App** (`@HiltAndroidApp`): Application entry point
- **MainActivity** (`@AndroidEntryPoint`): Single activity, field-injected `MusicDatabase`, `DownloadUtil`
- **ViewModels** (`@HiltViewModel`): All 34 ViewModels inject `MusicDatabase` directly

### 4.3 Key Entry Points

#### `App.kt` (Application)

```kotlin
@HiltAndroidApp
class App : Application(), SingletonImageLoader.Factory
```

**`onCreate()`** initializes in two phases:

1. **Critical sync** (`initializeCriticalSync`):
   - Initialize Canvas artwork cache
   - Initialize ArchiveTuneCanvas API
   - Set Paxsenix User-Agent
   - Configure YouTube locale (from system locale)
   - Initialize Last.fm with API key/secret

2. **Deferred async** (`initializeDeferredAsync`):
   - Load preferences from DataStore
   - Apply content country/language to YouTube locale
   - Restore Last.fm session
   - Apply proxy settings (type, host, port, auth)
   - Configure IP rotation
   - Set up DNS-over-HTTPS
   - Apply playback authentication state
   - Apply visitor data
   - Random theme on startup (if enabled)
   - Set uncaught exception handler (redirects to `DebugActivity`)
   - Coil ImageLoader (disk cache, crossfade, hardware bitmaps)

#### `MainActivity.kt`

Single-activity Compose host with:
- **Service binding** to `MusicService` via `PlayerConnection`
- **Bottom navigation** (Home, Search, Mood & Genres, Library) + optional rail for tablets/TV
- **Bottom sheet player** (dismissed → collapsed mini-player → expanded full player)
- **Search bar** (M3 SearchBar, online/local toggle)
- **Deep link handling** (YouTube URLs, `archivetune://together`, audio files, share intents)
- **Dynamic theme** from album art color extraction
- **Update notification** fetcher
- **Screenshot blocking** (privacy setting)
- **Language/locale management** (pre-Android 14)
- **App shortcuts** (library, search, music recognition)

#### `DebugActivity.kt`

Process-isolated (`:crash`) crash handler that displays the stack trace to the user.

---

## 5. Data Layer (Room Database)

### 5.1 Database Architecture

**File:** `db/MusicDatabase.kt`  
**Room version:** 29  
**Entities:** 17  
**Views:** 3  
**Mode:** WAL (Write-Ahead Logging)

```kotlin
@Database(
    entities = [
        SongEntity::class, ArtistEntity::class, AlbumEntity::class,
        SongArtistMap::class, SongAlbumMap::class, AlbumArtistMap::class,
        PlaylistEntity::class, PlaylistSongMap::class,
        PlaylistPlayCount::class, PlaylistTagMap::class,
        Event::class, FormatEntity::class, LyricsEntity::class,
        RelatedSongMap::class, SetVideoIdEntity::class,
        SearchHistory::class, PlayCountEntity::class,
        TagEntity::class, ListeningBySlot::class
    ],
    views = [PlaylistSongMapPreview::class, SortedSongArtistMap::class, SortedSongAlbumMap::class]
)
```

### 5.2 Entity Relationships

```
LocalItem (abstract: id, title, thumbnailUrl)
├── Song (embeds SongEntity + List<ArtistEntity> + AlbumEntity? + FormatEntity?)
├── Artist (embeds ArtistEntity + List<Song>)
├── Album (embeds AlbumEntity + List<ArtistEntity> + List<Song>)
├── Playlist (embeds PlaylistEntity + List<PlaylistSong>)
└── PlaylistSong (embeds Song + PlaylistSongMap)

Relations:
  Song ──M:N──> Artist       (via SongArtistMap)
  Song ──M:N──> Album        (via SongAlbumMap)
  Album ──M:N──> Artist      (via AlbumArtistMap)
  Playlist ──M:N──> Song     (via PlaylistSongMap)
  Song ──1:N──> Event        (listen history)
  Song ──1:1──> FormatEntity (audio format info)
  Song ──1:1──> LyricsEntity (cached lyrics)
  Song ──1:N──> RelatedSongMap (radio/recommendations)
  Song ──1:1──> SetVideoIdEntity (video ID mappings)
  ── PlayCountEntity (per-song play count)
  ── PlaylistPlayCount (per-playlist play count)
  ── SearchHistory (search queries)
  ── TagEntity + PlaylistTagMap (user tags on playlists)
  ── ListeningBySlot (time-bucketed listening stats)
```

### 5.3 DAO (`DatabaseDao.kt`)

1847-line `@Dao` with operations covering:
- Songs (CRUD, search, filter by artist/album, top played, recently played, random)
- Artists (CRUD, search, with song counts)
- Albums (CRUD, search, with song counts)
- Playlists (CRUD, add/remove songs, reorder, search)
- Events (insert, query by time range, song, count)
- Lyrics (insert/update, search)
- Formats (insert/update)
- Related songs (insert/query)
- Video ID mappings (insert/query)
- Search history (insert, recent, clear)
- Play counts (increment, top X)
- Tags (CRUD, assign/remove from playlists)
- Statistics (listening by time slot, total time, play counts)

### 5.4 Type Converters

Custom converters for: `LocalDateTime`, `List<String>`, `List<Int>`, `List<Long>`, enums

### 5.5 Migration Strategy

- Manual migration functions in `MusicDatabase.kt`
- Schema reconciliation on migration failure
- Schema exported to `app/schemas/` via KSP

---

## 6. Playback Engine (MusicService)

### 6.1 Service Architecture

**Class:** `MusicService` (extends `MediaLibraryService`, 6320 lines)  
**Class:** `MediaLibrarySessionCallback` (1949 lines, browsable tree)  
**Class:** `PlayerConnection` (Activity-side state mirror)

```
┌─────────────────────────────────────────────────────────────────┐
│                        MusicService                             │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │                     ExoPlayer Instance                    │  │
│  │  • Custom DataSource (Cache, OkHttp, Resolving)          │  │
│  │  • Custom Renderers (SonicAudioProcessor, SilenceSkipping)│  │
│  │  • DefaultTrackSelector (with SafeTrackSelector fallback) │  │
│  └──────────────────────┬───────────────────────────────────┘  │
│                         │                                       │
│  ┌──────────────────────▼───────────────────────────────────┐  │
│  │              MediaLibrarySession + Callback               │  │
│  │  • Serves browsable tree for Android Auto/Wear OS        │  │
│  │  • Handles custom commands (ToggleLike, StartRadio, etc) │  │
│  │  • Manages media session notification                    │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                 │
│  ┌──────────┐ ┌──────────┐ ┌────────────┐ ┌────────────────┐  │
│  │ Queue    │ │ EQ +     │ │ Crossfade  │ │ Sleep Timer    │  │
│  │ (5 impl) │ │ Effects  │ │ (2 players)│ │ (time + end)   │  │
│  └──────────┘ └──────────┘ └────────────┘ └────────────────┘  │
│  ┌──────────┐ ┌──────────┐ ┌────────────┐ ┌────────────────┐  │
│  │ Download │ │ Scrobble │ │ Discord    │ │ Together       │  │
│  │ Cache    │ │ (LF/LB)  │ │ RPC        │ │ (Social)       │  │
│  └──────────┘ └──────────┘ └────────────┘ └────────────────┘  │
│  ┌──────────┐ ┌──────────┐ ┌────────────┐ ┌────────────────┐  │
│  │ Widget   │ │ Audio    │ │ Stream     │ │ Persistent    │  │
│  │ Updater  │ │ Focus    │ │ Recovery   │ │ Queue Saver   │  │
│  └──────────┘ └──────────┘ └────────────┘ └────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### 6.2 PlayerConnection (Activity-Side Mirror)

Binds to MusicService via Binder, exposes reactive state:

```kotlin
val playbackState: StateFlow<Int>
val isPlaying: StateFlow<Boolean>
val mediaMetadata: StateFlow<MediaMetadata?>
val queue: StateFlow<List<MediaItem>>
val shuffleMode: StateFlow<Boolean>
val repeatMode: StateFlow<Int>
val playbackError: StateFlow<PlaybackError?>
val aodModeEnabled: StateFlow<Boolean>
```

**Methods:** `playQueue()`, `playNext()`, `togglePlay()`, `skipToNext()`, `skipToPrevious()`, `seekTo()`, `setRepeatMode()`, `setShuffleMode()`, etc.

### 6.3 Queue Implementations

| Queue | Purpose | Pagination |
|-------|---------|-----------|
| `Queue` (interface) | `getInitialStatus()`, `nextPage()`, `hasNextPage()` | Virtual |
| `EmptyQueue` | No items | None |
| `ListQueue` | Static list | None |
| `YouTubeQueue` | YouTube playlist/video | Continuation token |
| `YouTubeAlbumRadio` | Radio from YouTube artist/album | Continuation token |
| `LocalAlbumRadio` | Radio from local library | None |
| `LocalMixQueue` | Mixed queue from local library | None |

### 6.4 Playback Features

- **Stream client selection:** AUTO, ANDROID_VR, WEB_REMIX, IOS, TVHTML5, ANDROID_MUSIC, HI_RES_LOSSLESS
- **Audio quality:** AUTO, HIGH, HIGHEST, LOW
- **Audio normalization:** Per-format LUFS-based gain
- **Audio offload:** Bypasses processing
- **Crossfade:** Dual ExoPlayer instances with volume automation (0-10s)
- **Equalizer:** 10-band EQ, Bass Boost, Virtualizer, Loudness Enhancer, presets
- **Skip silence:** Automatic silence detection via `SilenceSkippingAudioProcessor`
- **Volume normalization:** Per-format gain adjustment
- **Sleep timer:** Time-based (minutes) or end-of-song
- **Bluetooth:** Auto-play on device connection, `A2DP` device detection
- **Mute detection:** Auto-pauses when volume is zero
- **Audio route recovery:** Reconnects on device changes
- **Persistent queue:** Saves to file, restores on restart
- **Infinite queue:** Auto-loads more when near end
- **Stream recovery:** Retry on failure with backoff
- **Offline playback:** ExoPlayer download service + download cache

### 6.5 MediaLibrarySessionCallback

The browsable tree served to external clients (Android Auto, Wear OS, etc.):

```
Root
├── Library
│   ├── Recently Played
│   ├── Songs (sorted: by date added, name, play count)
│   ├── Artists
│   ├── Albums
│   └── Playlists
│       ├── Local Playlists
│       ├── YouTube Playlists
│       └── Spotify Playlists
├── YouTube
│   ├── Search (dynamic)
│   ├── Home
│   ├── Moods & Genres
│   └── Charts
└── Settings
    └── Equalizer
```

---

## 7. UI Layer (Jetpack Compose)

### 7.1 Navigation Structure

Single-activity with Jetpack Navigation Compose (~30 routes):

```
Bottom navigation:
  ┌──────┐  ┌────────┐  ┌───────────────┐  ┌─────────┐
  │ Home │  │ Search │  │ Mood & Genres │  │ Library │
  └──────┘  └────────┘  └───────────────┘  └─────────┘

Route hierarchy:
  /home → HomeScreen (quick picks, forgotten favorites, keep listening, speed dial)
  /library → LibraryScreen (tabs: songs, albums, artists, playlists, mixes)
  /search_results/{query} → OnlineSearchResult
  /local_search → LocalSearchScreen
  /album/{albumId} → AlbumScreen
  /artist/{artistId} → ArtistScreen, ArtistSongsScreen, ArtistAlbumsScreen, ArtistItemsScreen
  /online_playlist/{playlistId} → OnlinePlaylistScreen
  /local_playlist/{playlistId} → LocalPlaylistScreen
  /spotify_playlist/{playlistId} → SpotifyPlaylistScreen
  /auto_playlist/{playlist} → AutoPlaylistScreen
  /cache_playlist/{playlist} → CachePlaylistScreen
  /top_playlist/{top} → TopPlaylistScreen
  /youtube_browse/{browseId} → YouTubeBrowseScreen
  /history → HistoryScreen
  /stats → StatsScreen
  /year_in_music → YearInMusicScreen
  /news, /view_news/{newsId} → News screens
  /music_recognition → MusicRecognitionScreen
  /mood_and_genres → MoodAndGenresScreen
  /charts_screen → ChartsScreen
  /new_release → NewReleaseScreen
  /browse/{browseId} → BrowseScreen
  /account → AccountScreen
  /settings + 20+ sub-routes → Settings
  /login?url= → LoginScreen (WebView)
```

### 7.2 Player UI

The player is implemented as a **bottom sheet** with 3 states:

1. **Dismissed** — hidden
2. **Collapsed** — mini-player bar (album art, title, play/pause, progress)
3. **Expanded** — full-screen player

**Player design styles (9 variants):** V1–V9, configurable in settings
**Background styles:** Default, Gradient, Blur, Glow, Animated Glow, Custom Image
**AOD mode:** 17 geometric shapes, always-on-display layout

### 7.3 Theming System

**File:** `ui/theme/Theme.kt`

- **Material 3** with `MaterialExpressiveTheme` (expressive shapes + motion)
- **Default accent:** `0xFFED5564` (coral red)
- **Dynamic theming:** Extracts dominant color from current album art via `Palette` library, animates color scheme with spring animations
- **User-customizable:** Theme seed palette (primary/secondary/tertiary/neutral), pure black mode, dark mode (auto/on/off)
- **Material Kolor:** `com.materialkolor:material-kolor` for Material You color generation from seed colors
- **System dynamic color:** Android 12+ fallback
- **Typography:** Poppins (primary), SF Pro Display Bold (lyrics), system font option, custom font loading
- **Shapes:** extraSmall=8dp, small=12dp, medium=16dp, large=24dp, extraLarge=32dp

### 7.4 Widgets (7 Glance Widgets)

| Widget | Description |
|--------|-------------|
| Music Info | Album art + song title + artist + play/pause |
| Album Art | Large album art display |
| Now Playing Card | Card-style now playing display |
| Playback Deck | Deck-style playback controls |
| Playback Capsule | Compact capsule with controls |
| Playback Spotlight | Spotlight-style player |
| Playback Command | Simple play/pause command |

### 7.5 Screens Overview (30+)

| Screen | Route | Features |
|--------|-------|----------|
| **HomeScreen** | `home` | Quick picks, forgotten favorites, keep listening, speed dial, YouTube categories |
| **LibraryScreen** | `library` | 5 tabs (songs, albums, artists, playlists, mixes), sort/filter, tag filtering |
| **OnlineSearchScreen** | `search` | YouTube Music search with suggestions, local toggle |
| **AlbumScreen** | `album/{id}` | Album detail, track list, artist info, radio, download |
| **ArtistScreen** | `artist/{id}` | Artist bio, top songs, albums, related artists, radio |
| **OnlinePlaylistScreen** | `online_playlist/{id}` | YouTube playlist with pagination, download, shuffle |
| **LocalPlaylistScreen** | `local_playlist/{id}` | Local playlist management, reorder, edit |
| **HistoryScreen** | `history` | Listening history with date grouping, clear |
| **StatsScreen** | `stats` | Total plays/time, top songs/artists/albums, time-of-day heatmap |
| **YearInMusicScreen** | `year_in_music` | Annual listening report, top genres, total time |
| **SettingsScreen** | `settings` | 20+ sub-screens (appearance, player, lyrics, Internet, privacy, etc.) |
| **MusicRecognitionScreen** | `music_recognition` | Shazam-style audio recognition UI |

### 7.6 Key UI Components

- `BottomSheetPage` — Reusable expandable bottom sheet
- `Lyrics` / `LyricsV2` / `LyricsEnhanced` / `LyricsGlassStyle` — Multiple lyrics display styles
- `LyricsImageCard` — Shareable lyrics as images
- `PlayerSlider` / `BigSeekBar` — Custom seek bars
- `FloatingNavigationToolbar` / `TvNavigationRail` — Navigation bars
- `DraggableScrollBarOverlay` — Fast-scroll overlay
- `ExpressivePullToRefreshBox` — M3 expressive pull-to-refresh
- `GridItemRandomizer` — Randomized grid layout
- `HideOnScrollFAB` — Scroll-aware FAB
- `ShimmerTheme` — Shimmer loading placeholders

---

## 8. Lyrics Pipeline

### 8.1 Architecture

**Coordinator:** `LyricsHelper` (294 lines)  
**Providers (12):** See below  
**Preloader:** `LyricsPreloadManager` (pre-fetches for upcoming queue)

### 8.2 Provider Chain

`LyricsHelper` queries providers in user-configured order, returning first result:

```
baseProviders (default order):
1. BetterLyricsProvider       → :betterlyrics module (TTML YouTube Music)
2. LrcLibLyricsProvider        → :lrclib module (LrcLib public API)
3. KuGouLyricsProvider         → :kugou module (KuGou API)
4. SimpMusicLyricsProvider     → :simpmusic module (SimpMusic format)
5. UnisonLyricsProvider        → :unison module (Unison API)
6. PaxsenixAppleMusicLyricsProvider
7. PaxsenixNeteaseLyricsProvider
8. PaxsenixSpotifyLyricsProvider
9. PaxsenixMusixmatchLyricsProvider
10. PaxsenixYouTubeLyricsProvider
11. YouTubeSubtitleLyricsProvider  → YouTube video subtitles (via InnerTube transcript)
12. YouTubeLyricsProvider          → YouTube auto-generated lyrics
```

**MediaMetadata** drives the lyrics lookup (song title, artists, album, duration).

### 8.3 Caching

- Two LRU caches: `lyricsListCache` and `singleLyricsCache` (max 50 entries each)
- Lyrics stored in Room `LyricsEntity` for offline access
- `LYRICS_NOT_FOUND` sentinel value marks songs that don't have lyrics (avoid re-fetching)

### 8.4 Lyrics Display

Four display modes:
- **Fade** — lines fade in/out
- **Glow** — current line glows
- **Karaoke** — word-by-word highlighting
- **Apple Style** — Apple Music style centered layout

Plus `V2` enhanced layout with additional styling options.

---

## 9. Social Listening (Together)

### 9.1 Architecture

```
┌──────────────────────┐          ┌──────────────────────┐
│   Host Device        │          │   Guest Device(s)    │
│  ┌────────────────┐  │  WebSocket │  ┌────────────────┐  │
│  │ TogetherServer  │◄─┼────────┼──│ TogetherClient   │  │
│  │ (Ktor Server,   │  │          │  │ (Ktor Client,   │  │
│  │  LAN)           │  │          │  │  LAN or Online) │  │
│  └────────┬───────┘  │          │  └────────┬───────┘  │
│           │           │          │           │           │
│  ┌────────▼───────┐  │          │  ┌────────▼───────┐  │
│  │ TogetherClock  │  │          │  │ TogetherClock  │  │
│  │ (Sync)         │  │          │  │ (Sync)         │  │
│  └────────────────┘  │          │  └────────────────┘  │
│  ┌────────────────┐  │          │                       │
│  │ TogetherOnline │  │  HTTPS   │  ┌────────────────┐  │
│  │ Host           │──┼──────────┼─▶│ TogetherOnline │  │
│  │ (Online Relay) │  │          │  │ Endpoint       │  │
│  └────────────────┘  │          │  └────────────────┘  │
└──────────────────────┘          └──────────────────────┘
```

### 9.2 Components

| Component | File | Purpose |
|-----------|------|---------|
| `TogetherServer` | `TogetherServer.kt` | Local Ktor WebSocket server (LAN sessions) |
| `TogetherClient` | `TogetherClient.kt` | WebSocket client for joining sessions |
| `TogetherOnlineHost` | `TogetherOnlineHost.kt` | Online relay for internet-based sessions |
| `TogetherOnlineEndpoint` | `TogetherOnlineEndpoint.kt` | HTTP API to discover relay servers |
| `TogetherClock` | `TogetherClock.kt` | Synchronized playback clock with latency compensation |
| `TogetherGuestPlaybackPlanner` | `TogetherGuestPlaybackPlanner.kt` | Schedules guest-initiated playback changes |
| `TogetherModels` | `TogetherModels.kt` | `RoomState`, `Participant`, `Track`, `Role`, `SessionState` |
| `TogetherMessages` | `TogetherMessages.kt` | WebSocket message protocol |
| `TogetherLink` | `TogetherLink.kt` | Deep link encoding/decoding (`archivetune://together`) |
| `TogetherJson` | `TogetherJson.kt` | JSON serialization utilities |
| `TogetherOnlineApi` | `TogetherOnlineApi.kt` | Online API client |

### 9.3 Protocol

Messages include: `Join`, `Leave`, `Play`, `Pause`, `Seek`, `Skip`, `QueueAdd`, `QueueRemove`, `StateSync`, `ClockSync`, `Chat`, `RoleChange`.

### 9.4 Features
- LAN (local WebSocket) and online relay modes
- Host/guest roles with configurable permissions
- Synchronized playback (latency-compensated clock)
- Guest-initiated queue management
- Deep link sharing

---

## 10. External Integrations

### 10.1 Spotify Integration

| Component | Purpose |
|-----------|---------|
| `SpotifyAccountViewModel` | OAuth login (sp_dc + sp_key cookies in WebView) |
| `SpotifyLibraryRepository` | Fetch user's Spotify playlists |
| `SpotifyPlaybackResolver` | Resolve Spotify track IDs → YouTube equivalents for playback |
| `SpotifyPlaylistQueue` | Spotify playlist → internal queue adapter |

Flow: Login → fetch playlists → select playlist → resolve each track via YouTube search → play.

### 10.2 Last.fm Scrobbling

- **Provider:** `:lastfm` module → `LastFM.kt`
- **Integration:** `MusicService` scrobbles (now playing + scrobble)
- **Configuration:** Scrobble delay (percentage or seconds), min duration, cached scrobbles
- **Settings:** Token auth, session persistence

### 10.3 ListenBrainz

- **Manager:** `ListenBrainzManager.kt`
- **Protocol:** Token-based API scrobbling
- **Integration:** Alongside Last.fm in MusicService

### 10.4 Discord Rich Presence

- **Manager:** `DiscordPresenceManager.kt`
- **Features:** Custom activity details, state, large/small images, buttons, timestamps
- **Configuration:** Update interval, per-platform status (mobile/desktop)

### 10.5 AI Integration

| Component | File | Purpose |
|-----------|------|---------|
| `AiTextService` | `ai/AiTextService.kt` | Unified AI client (ChatGPT, Gemini, Claude, OpenRouter) |
| `AiModels` | `ai/AiModels.kt` | AI model definitions and configurations |
| `AiLyricsTranslator` | `ai/AiLyricsTranslator.kt` | AI-powered lyrics translation |
| `AiLyricsDocument` | `ai/AiLyricsDocument.kt` | AI lyrics document model |
| `AiJsonUtils` | `ai/AiJsonUtils.kt` | JSON parsing for AI responses |

**Uses:** Playlist suggestions, lyrics translation, smart recommendations.

### 10.6 Music Recognition (ShazamKit)

- **Module:** `:shazamkit` → `Shazam.kt` + `ShazamSignatureGenerator.kt`
- **Entry:** `MusicRecognitionTileService.kt` — Quick Settings tile
- **UI:** `MusicRecognitionScreen.kt` — Recognition UI
- **Flow:** Record audio → generate signature → match against Shazam database → return track info

### 10.7 Canvas (Animated Artwork)

- **Module:** `:canvas` → `ArchiveTuneCanvas.kt`
- **Integration:** `CanvasArtworkPlaybackCache.kt` (cache), `CanvasArtworkPlayer.kt` (display), `CanvasArtworkResolver.kt` (URL resolution)
- **API:** Bearer token from `CANVAS_BEARER_TOKEN` build config
- **Server:** `https://archivetune-api.koiiverse.cloud`

### 10.8 Updater

- **Class:** `Updater` in `utils/`
- **Channels:** STABLE, NIGHTLY, DAILY_NIGHTLY
- **Source:** GitHub releases
- **Features:** Version checking, download, release notes display

---

## 11. Dependency Graph

```
                    ┌──────────────────┐
                    │   :app (Android) │
                    │   Compose UI     │
                    │   MusicService   │
                    │   Room DB        │
                    │   ViewModels     │
                    └───┬───┬───┬───┬──┘
          ┌─────────────┘   │   │   └─────────────┐
          ▼                 ▼   ▼                 ▼
   ┌────────────┐   ┌────────────┐   ┌──────────────────┐
   │ :innertube │   │ :spotifycore│   │ :paxsenix        │
   │ (YouTube)  │   │ (Spotify)   │   │ (Apple/Netease/  │
   │            │   │             │   │  Musixmatch/YT)  │
   └────────────┘   └────────────┘   └──────────────────┘
          │                                    │
          ▼                                    ▼
   ┌────────────┐   ┌────────────┐   ┌──────────────────┐
   │ :betterlyrics│  │ :kugou     │   │ :canvas           │
   │ (TTML)      │   │ (KuGou)    │   │ (Animated Art)   │
   └────────────┘   └────────────┘   └──────────────────┘
   ┌────────────┐   ┌────────────┐   ┌──────────────────┐
   │ :lrclib    │   │ :unison    │   │ :simpmusic        │
   │ (LrcLib)   │   │ (Unison)   │   │ (SimpMusic)      │
   └────────────┘   └────────────┘   └──────────────────┘
   ┌────────────┐   ┌────────────┐   ┌──────────────────┐
   │ :lastfm    │   │ :shazamkit │   │                   │
   │ (Last.fm)  │   │ (Shazam)   │   │                   │
   └────────────┘   └────────────┘   └──────────────────┘

All library modules are JVM-only (no Android dependencies).
Only :app is an Android module.
No library depends on another library — all are independently consumed by :app.
```

---

## 12. Data Flow Walkthrough

### 12.1 Playing a Song (end-to-end)

```
1. User taps song in UI
       │
2. ViewModel calls PlayerConnection.playQueue(queue)
       │
3. PlayerConnection sends command via Binder to MusicService
       │
4. MusicService creates appropriate Queue implementation:
   - YouTube video → YouTubeQueue
   - YouTube album → YouTubeAlbumRadio
   - Local song → LocalAlbumRadio / ListQueue
   - Spotify → SpotifyPlaylistQueue
       │
5. MusicService calls YouTube.playerResponse(songId)
       │
6. :innertube module:
   a. Resolves song ID → InnerTube player request
   b. Sends HTTP POST to YouTube API with auth (PoToken, cookies, visitor data)
   c. Parses PlayerResponse → stream URLs, formats, DRM info
   d. Returns MediaInfo with available formats
       │
7. StreamChunkResolver selects best format based on:
   - User's audio quality preference (AUTO/HIGH/HIGHEST/LOW)
   - Stream client profile (ANDROID_VR, WEB_REMIX, IOS, etc.)
   - Hi-res/lossless availability
       │
8. CacheDataSource resolves final URL through:
   a. OkHttpDataSource (with proxy if configured)
   b. ResolvingDataSource (auth headers, stream client headers)
   c. SimpleCache (player cache, 512MB LRU)
       │
9. ExoPlayer starts playback with selected format
       │
10. MusicService records Event to Room DB (listen history)
       │
11. MusicService updates:
    - MediaSession metadata (for notification, Android Auto)
    - PlayerConnection StateFlows (UI reactivity)
    - Widget updater
    - Last.fm now playing (if configured)
    - Discord RPC (if configured)
    - Together session (if active)
       │
12. Concurrently: LyricsPreloadManager pre-fetches lyrics for next 3 queue items
```

### 12.2 Search Flow

```
1. User types in SearchBar
       │
2. After debounce: OnlineSearchViewModel calls YouTube.search(query)
       │
3. :innertube sends search request → parses SearchResponse
       │
4. Returns List<SongItem>, List<AlbumItem>, List<ArtistItem>, List<PlaylistItem>
       │
5. UI displays categorized results (songs, albums, artists, playlists, videos)
       │
6. User taps result → navigates to detail screen or plays directly
```

### 12.3 Library Sync Flow

```
1. User plays YouTube content
       │
2. MusicService records Event in Room
       │
3. On library screen: ViewModel queries Room for:
   - Recently played songs/albums/artists
   - Playlists (local + YouTube)
   - Top played songs
   - Recently added
       │
4. YouTube library (liked songs, playlists) fetched via
   YouTube.library() → LibraryPage parser
       │
5. Spotify library fetched via SpotifyAccountViewModel →
   SpotifyLibraryRepository → :spotifycore
```

### 12.4 Theme Color Extraction Flow

```
1. Song starts playing in MusicService
       │
2. PlayerConnection.mediaMetadata StateFlow emits new metadata
       │
3. MainActivity collects the flow in LaunchedEffect
       │
4. Downloads album art thumbnail via Coil ImageLoader
       │
5. Extracts dominant color using Palette library (extractThemeColor)
       │
6. Updates themeColor mutableStateOf
       │
7. ArchiveTuneTheme recomposes with new color
   → Material color scheme regenerated via material-kolor
   → UI animates to new color scheme with spring animation
```

### 12.5 Together Session Flow

```
HOST:
1. User starts "Listen Together" → TogetherServer starts on LAN
2. TogetherClock initializes synchronized clock
3. Host shares link via deep link (archivetune://together/...)
4. When guests join, host sends current playback state (track, position, timestamp)
5. Periodic clock sync messages keep everyone in sync

GUEST:
1. Opens archivetune://together link → deep link triggers MainActivity
2. TogetherClient connects to host's WebSocket server (or online relay)
3. Receives current playback state → starts playing at synchronized position
4. Can request queue additions (if permitted by host role)
5. Chat messages broadcast to all participants
```

---

## 13. Configuration & Preferences

### 13.1 DataStore Preferences

**File:** `constants/PreferenceKeys.kt` (832 lines)

Over **200 preference keys** organized by feature:

| Category | Example Keys |
|----------|-------------|
| **Theme** | `DarkModeKey`, `PureBlackKey`, `DynamicThemeKey`, `CustomThemeColorKey`, `RandomThemeOnStartupKey` |
| **Player** | `PlayerDesignStyleKey` (V1-V9), `PlayerBackgroundStyleKey`, `AudioQualityKey`, `StreamClientKey`, `CrossfadeDurationKey`, `SkipSilenceKey`, `VolumeNormalizationKey`, `AudioOffloadKey` |
| **Lyrics** | `LyricsProviderOrderKey`, `PreferredLyricsProvider`, `LyricsAnimationStyleKey`, `LyricsFontScaleKey` |
| **Network** | `ProxyEnabledKey`, `ProxyTypeKey`, `ProxyHostKey`, `ProxyPortKey`, `EnableDnsOverHttpsKey`, `IpRotationEnabledKey` |
| **Account** | `VisitorDataKey`, `LastFMSessionKey`, `TogetherDisplayNameKey` |
| **Library** | `LibraryFilterKey`, `LibrarySortKey` |
| **History** | `PauseSearchHistoryKey`, `HistorySourceKey` |
| **Privacy** | `DisableScreenshotKey`, `StopMusicOnTaskClearKey` |
| **AI** | `AiProviderKey`, `AiModelKey`, `AiApiKey` |
| **Discord** | `DiscordEnabledKey`, `DiscordApplicationIdKey`, `DiscordPresenceUpdateIntervalKey` |
| **Widgets** | Various widget customization keys |
| **Updates** | `UpdateChannelKey` (STABLE/NIGHTLY/DAILY_NIGHTLY) |
| **Storage** | `MaxImageCacheSizeKey`, `MaxPlayerCacheSizeKey`, `SmartTrimmerKey` |

### 13.2 Build Config Fields

| Field | Source | Purpose |
|-------|--------|---------|
| `LASTFM_API_KEY` | local.properties / env | Last.fm API key |
| `LASTFM_SECRET` | local.properties / env | Last.fm API secret |
| `TOGETHER_BEARER_TOKEN` | local.properties / env | Together API auth |
| `CANVAS_BEARER_TOKEN` | local.properties / env | Canvas API auth |
| `DISCORD_APPLICATION_ID` | local.properties / env | Discord app ID |
| `NIGHTLY_BUILD_HASH` | local.properties / env | Nightly build identifier |
| `DISTRIBUTION` | Flavor | `gms` / `foss` / `izzy` |
| `UPDATER_AVAILABLE` | Flavor | Auto-updater enabled |

---

## Key Architectural Decisions & Rationale

1. **Why InnerTube client instead of YouTube API?**
   - YouTube Music has no official public API for music streaming
   - InnerTube is the internal protocol used by all YouTube clients
   - Allows full control (playback auth, format selection, no rate limits)

2. **Why single Activity?**
   - Jetpack Compose Navigation works best with single-activity architecture
   - Bottom sheet player requires shared activity state
   - Simplifies lifecycle management

3. **Why Hilt?**
   - Standard for Android DI (official Google recommendation)
   - Works seamlessly with ViewModel, Compose Navigation
   - `@EntryPoint` pattern allows non-Hilt classes (MusicService) to access DI

4. **Why Room over other databases?**
   - First-class Kotlin Flow support (reactive queries)
   - Type-safe DAOs with compile-time verification
   - SQLite-backed for local-first offline data

5. **Why Ktor over OkHttp/Retrofit?**
   - Unified HTTP client + server (Together needs both)
   - Kotlin multiplatform compatible (future-proofing)
   - Built-in WebSocket support (Together protocol)

6. **Why 14 lyrics providers?**
   - Redundancy: if one is down, others fill in
   - Different quality: some have synced, some have translations
   - Regional coverage: KuGou for China, Netease for Korea/Japan, LrcLib for English

7. **Why separate library modules instead of one monolithic module?**
   - Clear separation of concerns (each API client is independent)
   - Faster compilation (JVM modules are quick)
   - Testability (each can be tested independently)
   - Potential for reuse in other projects

---

*Generated from codebase analysis — last updated 2026-06-17*
