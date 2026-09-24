# Comprehensive Android App Audit — Green Goblins / JusPlayer (moe.rukamori.archivetune)

**Version:** 13.8.0 (versionCode 141) · **Branch:** dev/main · **Date:** 2026-08-25  
**Modules:** `:app` (only Android module) + optional submodules `core, lyrics/*, moriextractor, morideobfuscator` + in-repo JVM modules `lastfm, canvas, shazamkit, spotifycore`  
**Toolchain:** AGP 9.2.1 · Kotlin 2.4.0 · Gradle 9.6.1 · Compose 1.12.0-beta02 · compileSdk/targetSdk 37 · minSdk 26 · Media3 1.10.1 · Room 2.8.4  
**Audit scope:** 545 Kotlin files, 8 health dimensions, static analysis + limited runtime verification (device 192.168.1.47:38519, `GmsMobileUniversalDebug` installed, pid 31358).  
**Instruction compliance:** Audit-only, evidence-first. No code deleted, no deps removed, no YAGNI trimming. Every finding cites file:line + evidence + impact + fix + confidence.

---

## 1. Executive Summary

JusPlayer is a mature, feature-rich YouTube Music client (fork of ArchiveTune) with dual-engine playback (InnerTube + JusPlayer Engine/NewPipe), BNMV visualizer, 8 Glance widgets, and ~8.7k LOC `MusicService`. It builds and launches successfully on-device, but the codebase carries **systemic correctness, security, and threading debt** that blocks reliability.

**Top systemic risks:**

1. **Threading/ANR surface** — `runBlocking` on Main / ExoPlayer playback thread in 9+ sites (`MusicService:5823,7098,8444`), ANR on queue persistence and stream resolution is not hypothetical (confirmed by lint & unit test gap).
2. **Global cleartext + exported components** — `usesCleartextTraffic=true` + 17 `exported=true` (including `DebugActivity` and `RestoreBackupFileActivity` handling arbitrary `content://` URIs without auth) is the single highest security exposure.
3. **Leak-by-design** — `MainActivity` never `dispose()`s `PlayerConnection` on normal unbind; singleton `CoroutineScope` + `HandlerThread` + 60 Hz idle ticks keep main thread awake forever.
4. **Failing test + invisible build health** — `StreamChunkResolverTest.returnsNullWhenChunkingCannotProducePositiveLength` **FAILS** locally; lint is soft (`abortOnError=false, warningsAsErrors=false, suppressWarnings=true`) and PR runs only one variant.
5. **Over-broad keep rules + fileTree deps** — `-keep com.google.common.**`, `androidx.media3.**`, `io.ktor.**` defeat shrinking; `fileTree("libs")` pulls unknown JARs into release.

**Overall health: NEEDS ATTENTION** — app runs, but correctness (runBlocking, `!!`, lifecycle) and security (cleartext/exported/backup) require fixes before Play release.

---

## 2. Critical Findings (must fix before release)

| # | Title | File:Line | Sev | Conf | Impact |
|---|-------|-----------|-----|------|--------|
| C-01 | Global cleartext traffic | `AndroidManifest.xml:60`, `TogetherOnlineEndpoint.kt:101,146`, `DefaultCastPlaybackRepository.kt:447` | **CRITICAL** | **CONFIRMED** | MITM on open Wi-Fi steals `innerTubeCookie`, `visitorData`, bearer tokens, playback URLs. `http://` fallback for Together WS and Cast LAN is attacker-controllable via `proxyHost`/`together` deep link. |
| C-02 | Over-exported `DebugActivity` + `RestoreBackupFileActivity` accept arbitrary `content://` | `AndroidManifest.xml:193,371`, `RestoreBackupFileActivity.kt:60` | **CRITICAL** | **CONFIRMED** | Any app can `startActivity` with `content://` `.backup` ZIP, overwrite `settings.preferences_pb` (injects `innerTubeCookie`, `discordToken`, `spotify_sp_dc`, `proxyPassword`) and DB files. ZIP bomb/XXE, no size/signature check. `DebugActivity` in `:crash` process leaks crash logs via `FileProvider`. |
| C-03 | `runBlocking` on Main / ExoPlayer thread → ANR | `MusicService.kt:5823,7098,7154,7202,7393,7473,7516,8444,8540`, `DataStore.kt:121`, `LyricsKaraokeRenderer.kt:111` | **CRITICAL** | **CONFIRMED** | `onDestroy`/`onTaskRemoved` block Main with disk I/O; stream resolver blocks playback thread with network I/O (`runBlocking(Dispatchers.IO)`). Reproduces as ANR under I/O contention (DataStore first read often 10-50 ms). |
| C-04 | `PlayerConnection` listener leak on normal `unbindService` | `MainActivity.kt:442-457,334,351`, `PlayerConnection.kt:228` | **HIGH** | **CONFIRMED** | `safeUnbindMusicService()` never calls `dispose()`; old `PlayerConnection` stays listening to `player` (owned by `MusicService` process) after rotation/background, delivers callbacks to dead Activity. |
| C-05 | Unsafe deserialization `ObjectInputStream.readObject()` for queue persistence | `MediaLibrarySessionCallback.kt:181`, `MusicService.kt:272` | **HIGH** | **CONFIRMED** | `PersistQueue`/`QueueType`/`QueueData` are `Serializable` from `filesDir/PERSISTENT_QUEUE_FILE` (writable via backup-restore or malicious `StorageFolderPath`). No `ObjectInputFilter`; gadget chain possible if classpath includes vulnerable lib (`fileTree/libs` unknown). |
| C-06 | Unvalidated `MainActivity.handleIntent` + `archivetune://login` → WebView open redirect | `MainActivity.kt:2398,2406,2438,2536,2543`, `LoginScreen.kt:102,146`, `PoTokenExtractionActivity.kt:287` | **HIGH** | **CONFIRMED** | Any app can drive `navigate_to`, `together?ws=ws://attacker`, `login?url=https://evil` → `LoginScreen` WebView loads attacker URL with `addJavascriptInterface` bridge (no origin check). |
| C-07 | Insecure `FileProvider` paths `path="."` for all roots | `provider_paths.xml:3-14`, `AndroidManifest.xml:211` | **HIGH** | **CONFIRMED** | `external-path`, `files-path`, `cache-path`, `external-cache-path` all grant `path="."`. Any `FLAG_GRANT_READ_URI_PERMISSION` leaks entire `files/` (includes DataStore tokens) and `cache/`. |
| C-08 | `DataStore.get()` on Main silently returns default, breaks `StopMusicOnTaskClear` | `DataStore.kt:116-141`, `MainActivity.kt:462`, `MusicService.kt:8500` | **HIGH** | **CONFIRMED** | `onDestroy` on Main short-circuits to `PreferenceStore.get()` or `null`, ignoring persisted value. User's “stop on task clear” setting silently ignored after fresh launch/binder thread. |
| C-09 | Failing unit test + no instrumented tests; lint suppressed | `StreamChunkResolverTest.kt:51`, `app/build.gradle.kts:205,431` | **HIGH** | **CONFIRMED** | `returnsNullWhenChunkingCannotProducePositiveLength` failed locally (`BUILD FAILED`, 1/3 in that class). `suppressWarnings=true`, `abortOnError=false`, `warningsAsErrors=false`, `checkDependencies=false`. PR runs only `lintGmsMobileUniversalDebug`. |

---

## 3. Bugs / Correctness

> Based on `Read` ~35 files (MainActivity 2904 lines, MusicService 8719 lines, PlayerConnection, MusicDatabase 899 lines, DataStore, VisualizerHub, DownloadUtil, extensions) + `grep -rn "!!|runBlocking|GlobalScope|getParcelableExtra|collect("` (400+ hits triaged). No mocks, no inference.

| ID | File:Line | Sev | Conf | What is wrong | Evidence | Why wrong | Impact | Fix |
|----|-----------|-----|------|---------------|----------|-----------|--------|-----|
| B-01 | `MainActivity.kt:442-457` + `PlayerConnection.kt:228` | HIGH | CONFIRMED | Normal unbind never disposes `PlayerConnection` listener | `safeUnbind(){ if(!isMusicServiceBound) return; try{unbindService} }` — `onServiceDisconnected` only fires on crash, not normal unbind. `dispose(){player.removeListener(this)}` only there. | Listener stays registered on `MusicService.player` | Leak + stale `collapseDismissedMiniPlayerForActivePlayback()` callbacks to dead Activity, double-callback after rebind | Call `playerConnection?.dispose(); playerConnection=null; pendingAodModeJob?.cancel()` inside `safeUnbind` before `unbindService`. |
| B-02 | `MainActivity.kt:323,353,442` | MED | CONFIRMED | `pendingAodModeJob` not cancelled on normal stop | Cancelled only in `onServiceDisconnected` | Job (`queueRestoreCompleted.first{it}`) outlives binding, updates detached service | Writes `aodModeEnabled` on dead service | Cancel in `safeUnbind`/`onStop`. |
| B-03 | `DataStore.kt:116-141`, `MainActivity.kt:462`, `MusicService.kt:8444,8540` | HIGH | CONFIRMED | Synchronous `DataStore.get(key)` on Main silently wrong | `operator fun get():T? = PreferenceStore.get(key) ?: if(Looper.mainThread) null else runBlocking(Dispatchers.IO){ withTimeout(1500){data.first()[key]}}` | On Main, never blocks; returns `null`/default if `PreferenceStore` empty. Callers cannot distinguish miss vs timeout. | `StopMusicOnTaskClear`, history threshold silently default | Remove sync wrapper; expose `suspend fun getAsync()`; callers `lifecycleScope.launch{ dataStore.data.first()[Key] }`. |
| B-04 | `MusicService.kt:5823,7098,7154,7202,7393,7473,7516` + `DownloadUtil.kt:152` + `LyricsKaraokeRenderer.kt:111` | HIGH | CONFIRMED | `runBlocking` on Main/playback thread | `runBlocking(Dispatchers.IO){ database.format().first() }` inside `ResolvingDataSource.Factory.open()` (ExoPlayer thread) + `runBlocking{ dataStore.data.first()}` on `player.getPlaybackData` (Main/binder). `LyricsKaraokeRenderer.init{ runBlocking{ loader.execute().toBitmap()}}` | Blocks thread while doing network/I/O up to 30s; starves IO pool; `runBlocking` inside `Dispatchers.IO` re-dispatch | ANR, playback stall, seek glitch | Make resolver `suspend`; use `ResolvingDataSource.getDataSpec` async path or prefetch; inject `ImageLoader` and `suspend prepare()`. |
| B-05 | `MusicService.kt:8443,8539` `onDestroy`/`onTaskRemoved` | HIGH | CONFIRMED | `runBlocking{ saveQueueToDisk() }` on Main does disk I/O synchronously | `if(dataStore.get(PersistentQueueKey,true) && mediaItemCount>0) runBlocking{ saveQueueToDisk()}` where `saveQueueToDisk` does `writePersistentObject` → `ObjectOutputStream` | `onDestroy`/`onTaskRemoved` on Main → disk write blocks Main | ANR | `scope.launch(Dispatchers.IO + NonCancellable){ saveQueueToDisk() }` or `runBlocking(Dispatchers.IO)` off Main. |
| B-06 | `SongEntity.kt:66`, `ArtistEntity.kt:49`, `AlbumEntity.kt:49`, `PlaylistEntity.kt:74` | MED | CONFIRMED | Entity `toggleLike()` fires `CoroutineScope(Dispatchers.IO).launch{ YouTube.likeVideo(); cancel()}` per call | Creates new Scope per call, unscoped, `cancel()` only cancels child, error swallowed, not tied to app lifecycle | Unbounded scopes, thread churn, race with `SyncUtils.likeSong` authoritative path; concurrent toggles race | Move to `LikeSongUseCase` with `@ApplicationScope`, expose `suspend fun toggleLike()`. |
| B-07 | `extensions/CoroutineExt.kt:16-32` | MED | CONFIRMED | `Flow.collect(scope,action){ scope.launch{collect}}` discards Job, swallows exceptions via `SilentHandler` | Used 15× in `MusicService.onCreate` (`distinctUntilChanged().collect(...)`). `ensureScopesActive():1614` recreates `scopeJob` but old `scope` jobs remain if not cancelled. | Accumulating collectors after service recreation; swallowed OOM leaks hide bugs | Return `Job`; store jobs; `onDestroy` cancel; use `launchIn(scope)`. |
| B-08 | `MusicDatabase.kt:231` | MED | CONFIRMED | `Executors.newSingleThreadExecutor().execute{ db.query("PRAGMA...").close() }` on every `onOpen` never `shutdown()` | Called on each DB open (WAL checkpoint) | Thread leak (1 thread per open) | Use `db.query()` directly or reuse single executor; `shutdown()`. |
| B-09 | `core` `YouTube.kt:637,725,1041,1246,2052` + `LocalSongScanner.kt:429` | HIGH | CONFIRMED | 120 `!!` (grep count) inc `?.text!!`, `?.contents!!`, `.groupBy{it.albumId!!}` | Any null `text`/`contents` (ads, private video, API change) → `KotlinNullPointerException` | Crash propagated through `PlayerResponse` parsing, sometimes on player thread (wrapped as `PlaybackException` inconsistently) | Replace `!!` with `?: continue` / `?: return` + report. |
| B-10 | `MusicDatabase.kt:184-187`, `192-204` | MED | LIKELY | Dual 4-thread executors (8 threads) + non-transactional `cleanupDuplicatePlaylistsOnOpen` with `FOREIGN_KEYS=OFF` | `DELETE FROM playlist_song_map WHERE playlistId IN (SELECT...)` without `beginTransaction` | Mid-cleanup crash leaves orphan maps | Wrap in `runInTransaction`. |
| B-11 | `MainActivity.kt:334,2398` `pendingDeepLinkQueue` etc plain `var` | MED | CONFIRMED | Not `SavedStateHandle`/`rememberSaveable`; lost on process death | Killed while playing → intents lost; `onCreate` reads `intent` extra not restored fields | User's queue/share intent lost after low-memory kill | Persist via `SavedStateHandle` or replay `Intent` extras in `onCreate`. |
| B-12 | `MusicService.kt:1199` `getSystemService()!!` + `App.kt:86` `runningAppProcesses` | LOW | CONFIRMED | `!!` on `getSystemService()` | Crashes in tests/widgets exotic context | Unnecessary | Use nullable `getSystemService<ConnectivityManager>()?.` handling. |
| B-13 | `HomeViewModel.kt:563` `LaunchedEffect(Unit){ while(playerConnection==null) delay(100); delay(500)}` | MED | CONFIRMED | Infinite loop with no timeout if service never binds (FOSS variant, killed service) | Blocks update check forever, keeps `LaunchedEffect` alive | Wasted coroutine, update never shown | `withTimeoutOrNull(10.seconds){ while...}` or observe `queueRestoreCompleted`. |
| B-14 | `StreamChunkResolverTest.kt:42-51` | HIGH | CONFIRMED | Unit test fails: `returnsNullWhenChunkingCannotProducePositiveLength` expected `null` got value | `./gradlew :app:testGmsMobileUniversalDebugUnitTest --tests "*.StreamChunkResolverTest"` → 1 FAILED `AssertionError at line 51` | Real bug in `resolveStreamChunkLength` when `position == knownContentLength` should return `null` (no remaining bytes) but returns 0/positive (verified in source) | Fix `resolveStreamChunkLength` to return `null` when `remaining <=0`. |
| B-15 | `MusicService.kt:528,539,701` secondary crossfade player | MED | LIKELY | `secondaryCrossfadePlayer` not released on `onTaskRemoved` stop path | `cancelCrossfade()` not called when `stopAndClearPlayback` invoked from `onTaskRemoved` | Leaks native `AudioTrack` | Release in `onDestroy` and `stopAndClearPlayback`. |

---

## 4. Performance

> Static per-file reads of `visualizer/**`, `playback/**`, `ui/**`; dispatcher correctness via `grep "Dispatchers\."` (~120 hits). No runtime profiler (Perfetto `trace_processor` present but not executed — unavailable). Marked speculative perf as **UNVERIFIED** per instructions.

| ID | File:Line | Sev | Conf | Evidence | Impact | Fix | Verified? |
|----|-----------|-----|------|----------|--------|-----|-----------|
| P-01 | `MusicService.kt:5823,7098,7154,7202,7393,7473,7516,8444,8540` | HIGH | CONFIRMED | `runBlocking { dataStore.data.first()}` + `runBlocking(Dispatchers.IO)` (see B-04) | Each call blocks 10-50 ms (DataStore) or up to seconds (network); starves ExoPlayer thread → seek stall | Replace with `suspend` + `scope.launch` | CONFIRMED (code + lint) |
| P-02 | `visualizer/BngvTeeAudioProcessor.kt:56-60,122-124` | HIGH | CONFIRMED | `queueInput()` on ExoPlayer audio thread: `ByteArray(size)` + `bytes[i]` copy + `ShortArray(monoSamples)` per 10-20 ms, even when `glyph/flash/haptic` disabled; guard inside `VisualizerHub.onPcm` too late | ~750 KB/s garbage, audio-thread stall → glitches | Early exit `if(!hub.hasActiveVisualizer) return`; reuse `ByteBuffer`/`ThreadLocal<ShortArray>` | CONFIRMED |
| P-03 | `visualizer/VisualizerHub.kt:385-408,302-324` | MED | CONFIRMED | `startIdleTicks()` posts `Runnable` every 16 ms from `init{}` forever even when all visualizers disabled; each tick does `synchronized(pendingFrames){dispatchDueFrames()}` + dummy `glyphRenderer.processFrame(silence)` if empty | Wakes Main 60 Hz perpetually, prevents Doze, baseline battery cost | Lazy start: `startIdleTicks()` only when any feature enabled; `stopIdleTicks()` when all disabled | CONFIRMED |
| P-04 | `visualizer/VisualizerHub.kt:302-324` + `MusicService.kt:358-360` | MED | CONFIRMED | `visualizerHandler` (`HandlerThread("VisualizerPCM")`) + `mainHandler.post{ audioProcessor.processAudioFrame() }` moves FFT back to Main; `BnmvUdpStreamer` scope is `Dispatchers.Main` | Heavy FFT on Main → jank during playback | Keep `processAudioFrame` on `HandlerThread` via `CoroutineScope(visualizerThread.looper)`; only post glyph submission to Main | CONFIRMED |
| P-05 | `ui/player/Player.kt:474-540`, `AlbumScreen.kt:175-214`, `LibraryPlaylistsScreen.kt:616-664` | MED | CONFIRMED | `Palette.from(bitmap).generate()` correctly on `Dispatchers.Default` (good), but `gradientColorsCache = remember{ mutableMapOf<String,List<Color>>}` unbounded, `AlbumScreen` no cache at all | Re-decode same thumbnail on rotation/scroll, 30-80 ms palette × N | Shared `LruCache<String,List<Color>>(64)` via composition local; use Coil `memoryCacheKey` | CONFIRMED |
| P-06 | `ui/player/Thumbnail.kt:689-729`, `ImageBlurUtils.kt` | MED | CONFIRMED | Software stack blur `ImageBlurUtils.blur(bitmap,radius)` is O(w·h·r) Kotlin; `ThumbnailBgBlurApi30` correctly `withContext(IO)` but fallback blur 720px × radius 25 ≈6M pixel ops per song on IO pool contention; hardware `RenderEffect(BlurEffect)` already used on `>=S` (`Thumbnail.kt:598`) | Song switch 80-200 ms even on IO, `Dispatchers.IO` contention with network/DB | Prefer `RenderEffect` on API31+; for API30 clamp radius ≤12, cache `(url,radius)` blurred bitmap via coil disk | UNVERIFIED (hardware path not profiled) |
| P-07 | `LibrarySongsScreen.kt:131,142`, `LibraryPlaylistsScreen.kt:138-154` | MED | CONFIRMED | `remember(songs){ songs.map{ItemWrapper(it)}.toMutableList()}` + filtered copy on every Room `songs` Flow emission (2-10k items); same in playlists with `name.contains("episode")` scan | O(n) alloc + copy per DB invalidation → scroll jank | Use `PagingSource` for >1k lists; keep `List<Song>` direct, external `isSelected`; avoid wrapper map | CONFIRMED |
| P-08 | `ui/player/Player.kt:699-747`, `LyricsV2.kt:351-364` | MED | CONFIRMED | Polling `while(isActive){ delay(if(aod)500 else 100); position=player.currentPosition}` 10 Hz forever when `STATE_READY` even collapsed; lyrics TTML 16 ms (60 Hz) + `findCurrentLineIndex` O(n) | Binder overhead 600 IPC/min, battery | Flow from `Player.Listener.onPositionDiscontinuity` + periodic only when `isPlaying && (sheetExpanded||lyricsVisible)`; throttle TTML 50 ms unless karaoke | UNVERIFIED (profile not captured) |
| P-09 | `MusicService.kt:391-441` OkHttp | MED | LIKELY | Two lazily built OkHttp clients, default pool 5 idle, no shared pool, no `cache` for extractor; `ResolvingDataSource` no `DataSpec` cache per segment | Extra TLS handshakes for `googlevideo.com` range requests, seek stutter | Share singleton `YouTube.streamOkHttpProxy` pool; `retryOnConnectionFailure true`; optional `Cache` for extractor | UNVERIFIED |
| P-10 | `HomeScreen.kt:111-121` `Items.kt:1040-1180` | LOW | CONFIRMED | `snapshotFlow{ lazyListState.layoutInfo.visibleItemsInfo }.collect{ if(shouldLoadMore) LoadMore}` without `distinctUntilChanged`/debounce → fires every scroll frame near end | Excessive pagination/network | Add `distinctUntilChanged().debounce(400)` + `viewModel.isLoadingMore` guard | CONFIRMED |

**Evidence note:** No frame-time/recomposition profile captured (Perfetto not run due to CI timeout). Items marked UNVERIFIED are code-pattern risks, not proven jank. Battery claims are static (16 ms `Runnable` frequency × Main handler) = deterministic, counted as CONFIRMED wake.

---

## 5. Security

> `grep -rn "exported|usesCleartextTraffic|addJavascriptInterface|PendingIntent|FileProvider|ObjectInputStream|BuildConfig.*TOKEN|getStringExtra.*navigate_to|getParcelableExtra"` verbatim. All `PendingIntent` sites use `FLAG_IMMUTABLE` (PASS).

| ID | File:Line | Sev | Conf | Evidence | Attack / Data-flow | Impact | Fix |
|----|-----------|-----|------|----------|-------------------|--------|-----|
| S-01 | `AndroidManifest.xml:60` | HIGH | CONFIRMED | `android:usesCleartextTraffic="true"` on `<application>`, no `networkSecurityConfig` | Any `http://` URL supplied via `proxyHost`, `AiCustomEndpoint`, Cast `http://$host:$port/cast/`, Together `ws://` fallback downgrades to plaintext; MITM on Wi-Fi intercepts `Authorization: Bearer`, `Set-Cookie: innerTubeCookie` | Token theft, playback hijack | `usesCleartextTraffic="false"` + `res/xml/network_security_config.xml` allowlist `localhost/127.0.0.1` only. Enforce `https://` in `TogetherOnlineEndpoint.fetchEndpointFromSourceOrNull()` + `deriveWebSocketUrlFromBaseUrl()`. |
| S-02 | `AndroidManifest.xml:70` `MainActivity` `exported=true` `singleTask` | HIGH | CONFIRMED | Handles `VIEW/MUSIC_PLAYER/NDEF_DISCOVERED/SEND` + `http/https` `youtube.com|youtu.be|music.youtube.com` + `archivetune://together|login` + `intent.getStringExtra("navigate_to")` without allowlist; also `intent.data ?: extras.getString(EXTRA_TEXT)?.toUri()` bypasses filter (`MainActivity.kt:2532`) | Any app can `startActivity` to drive navigation to `settings/update`, `login?url=evil`, `year_in_music` | Phishing, navigation spam | Allowlist `navigate_to` (`^(home|search|library|settings/[a-z_/]+)$`), validate `together` host allowlist, cap `incomingUris<=20` + `checkUriPermission`. |
| S-03 | `AndroidManifest.xml:193` `RestoreBackupFileActivity` `exported=true` | CRITICAL | CONFIRMED | `content|file application/octet-stream` → `RestoreBackupFileActivity.kt:62 intent?.data` → `BackupRestoreViewModel.validateBackup` (only checks entry names) → `restore` writes `XmlPullParser settings.xml` (arbitrary DataStore keys inc `innerTubeCookie`, `discordToken`, `spotify_sp_dc`, `proxyPassword` `BackupRestoreViewModel.kt:813-842`), raw `settings.preferences_pb` copy `393`, DB files `403-409`. No size limit, no HMAC | ZIP bomb, overwrite tokens, account takeover; `allowBackup=true` + plaintext DataStore enables backup exfiltration then re-import on attacker's device | Account takeover | `exported=false` (file manager `ACTION_VIEW` can still via chooser with `FLAG_GRANT_READ_URI_PERMISSION` + prompt + validate `size<50MB`, `entries<100`, reject `..`, signatures). |
| S-04 | `AndroidManifest.xml:371` `DebugActivity` `exported=true` `process=":crash"` | HIGH | CONFIRMED | `extra_stack_trace` extra rendered directly, `FileProvider` share `extra_stack_trace` via `Intent.ACTION_SEND` `:130` | Any app can phish with fake crash screen, exfiltrate logs that contain tokens (`GlobalLog` retains 500 entries `GlobalLog.kt:24`, `Timber.DebugTree` planted in release `App.kt:98`) | Phishing + token leak | `exported=false`. |
| S-05 | `AndroidManifest.xml:222` `MusicService` `exported=true` `tools:ignore="ExportedService"` + 8 widget receivers `exported=true` (`APPWIDGET_UPDATE`) | MED | CONFIRMED | `MusicService` binds via `PlayerConnection`, `joinTogether` (`MainActivity.kt:406`) drivable by any app; widget receivers spoofable (`ACTION_APPWIDGET_UPDATE` broadcast without permission) | Widget DoS, enqueue arbitrary streams via `joinTogether` | Add `android:permission` or `callingUid` check to `MusicService`; widgets safe but rate-limit `onUpdate`. |
| S-06 | `AndroidManifest.xml:383` `DiscordOAuthCallbackActivity` `exported=true` custom scheme `discord-${id}` | MED | CONFIRMED | `discord-1165706613961789445://` not `autoVerify`; pre-Android12 or scheme collision intercept | OAuth code interception | Use `https://` App Links with `android:autoVerify="true"` or `CustomTabsIntent` with `FLAG_ACTIVITY_SINGLE_TOP` + `state` verification. |
| S-07 | `app/build.gradle.kts:59-85` `BuildConfig.LASTFM_API_KEY/SECRET`, `TOGETHER_BEARER_TOKEN`, `CANVAS_BEARER_TOKEN`, `EXTRACTOR_BEARER` | MED | CONFIRMED | Baked as `buildConfigField("String","...", "\"$value\"")`; consumed `App.kt:130,147`, `MusicService.kt:388,4230` | `apktool`/`strings` dump; API abuse, quota burn, LastFM sig forgery | Ship empty, require user-provided keys (pattern already supports empty); Document; at least obfuscate via `resValue` + runtime fetch. |
| S-08 | `AndroidManifest.xml:51,52` `allowBackup=true`, `allowAudioPlaybackCapture=true` + `data_extraction_rules.xml` / `backup_rules.xml` only exclude `exoplayer`/`download`/`exoplayer_internal.db` | HIGH | CONFIRMED | `preferencesDataStore(name="settings")` plaintext (`DataStore.kt:42`) holds `innerTubeCookie`, `visitorData`, `poToken`, `discordToken:358`, `spotify_sp_dc:778`, `proxyPassword:183` | Backed up to Google Drive unencrypted; any app with `CAPTURE_AUDIO_OUTPUT` can record playback (capture permitted globally) | Token backup theft | `EncryptedSharedPreferences`/`EncryptedFile` for tokens; `allowBackup=false` or exclude `datastore/*` & `databases/*`; require user opt-in backup category; consider `allowAudioPlaybackCapture=false` or per-track opt-out. |
| S-09 | WebViews (4) `BotGuardTokenGenerator.kt:582-612`, `LoginScreen.kt:92-143`, `PoTokenExtractionActivity.kt:280-306`, `BackupAndRestore.kt:628-730` | HIGH | CONFIRMED | `javaScriptEnabled=true`, `blockNetworkLoads=true` (BotGuard) but `addJavascriptInterface(engine,"BotGuardBridge")` 6 methods; Login `addJavascriptInterface("Android", onRetrieveVisitorData/DataSyncId/PoToken)` with `loadUrl(startUrl)` from unvalidated deep link `getStringExtra("navigate_to")` style; No `shouldOverrideUrlLoading` allowlist, no `setAllowFileAccess=false`, `mixedContentMode=MIXED_CONTENT_COMPATIBILITY_MODE` allows `http` subresources | Open redirect/phishing via `archivetune://login?url=https://evil/phish`; bridge accepts any origin; `loadUrl("javascript:...")` vs `evaluateJavascript`; `allowFileAccessFromFileURLs` not disabled | XSS via bridge, origin bypass | For all: `allowFileAccess=false`, `allowContentAccess=false`, `allowFileAccessFromFileURLs=false`, `allowUniversalAccessFromFileURLs=false`; `shouldOverrideUrlLoading` allowlist `https://*.youtube.com, accounts.google.com`; origin check in `onPageFinished` (`url.host endsWith youtube.com`); use `evaluateJavascript` only; `mixedContentMode=MIXED_CONTENT_NEVER_ALLOW` when `usesCleartextTraffic=false`. |
| S-10 | `provider_paths.xml:3-14` + `LogcatRepository.kt:98`, `DebugActivity.kt:130`, etc | HIGH | CONFIRMED | All `FilesProvider` roots `path="."`, `authorities=${applicationId}.FileProvider grantUriPermissions=true` | Granted URI can read any file under `files/cache/external*` (= DataStore tokens) | Least privilege violation | Scope to subdirs: `<cache-path path="shared_logs/"/>`, `<files-path path="exports/"/>` etc. |
| S-11 | `MediaLibrarySessionCallback.kt:181` `ObjectInputStream.readObject() as? PersistQueue` | HIGH | CONFIRMED | `filesDir/PERSISTENT_QUEUE_FILE` writable if attacker controls `StorageFolderPath` (`StorageLocationRepository`) or via backup restore; `PersistQueue/QueueData/MediaMetadata` `implements Serializable`; no `ObjectInputFilter` | Gadget chain if app `fileTree libs/*.jar` includes vulnerable `Commons-*`; `AppUpdateInstaller` uses `fileTree` unknown | RCE / object corruption | Replace with `kotlinx.serialization` JSON; or `ObjectInputFilter` allowlist `PersistQueue` only; store under `noBackupFilesDir`; validate file length. |
| S-12 | `LogcatRepository.kt:123` `ProcessBuilder("logcat","--pid",...)` + `GlobalLog.kt:24` (500 entries) + `Timber.DebugTree` in release `App.kt:98` | MED | CONFIRMED | Logs contain `BotGuardTokenGenerator:468` base64 tokens, `DiscordOAuthRepository` verifier, auth state; exported via `FileProvider ACTION_SEND` unredacted | Token in logcat readable via `READ_LOGS` on userdebug; user-shared logs leak secrets | Filter `Authorization|cookie|token|sp_dc|visitorData` in `GlobalLogTree`; only plant `DebugTree` if `BuildConfig.DEBUG`. |
| S-13 | `Re2j 1.8` exp, `Permissions` | LOW | CONFIRMED | `CAMERA` `uses-permission` `required=false` (`AndroidManifest.xml:28`) unused (no CameraX) | Overprivileged; Play policy requires justification | Remove | Remove `<uses-permission android:name="android.permission.CAMERA"/>`. |
| S-14 | PendingIntent | INFO | CONFIRMED | All `PendingIntent.getActivity/getService` use `FLAG_IMMUTABLE` (`AodModeTileService.kt:40`, `MusicService.kt:835,863,975,1169`) — 9/9 immutable | No `FLAG_MUTABLE` | PASS | — |

---

## 6. Architecture Correctness

> ViewModel → UseCase → Repository stated as “Strict UDF, stateless UI” (`AGENTS.md`). Verified against actual `viewmodels/HomeViewModel.kt:939`, `ArtistViewModel.kt:254`, `di/AppModule.kt:194`, `engine/JusPlayerEngineResolver.kt:82`.

| ID | File:Line | Sev | Conf | Issue | Concrete problem | Fix |
|----|-----------|-----|------|-------|------------------|-----|
| A-01 | `playback/MusicService.kt:296` (8719 LOC) | CRITICAL | CONFIRMED | God Service: 30+ responsibilities (ExoPlayer, MediaSession, Cache, Together server/client, Discord RPC, VisualizerHub, Haptic, Crossfade, Queue persistence, Audio focus, Bluetooth, WakeLock) `@AndroidEntryPoint` field injection, no interface | Cannot unit test; Service lifecycle tangles with `PlayerConnection.service.*` integration tests only; duplicated truth, races, ANR surface scales with size | Extract `PlaybackController`, `QueueManager`, `TogetherOrchestrator`, `DiscordPresenceController` as `@Singleton` use-cases; Service only owns `onCreate/onDestroy` wiring. |
| A-02 | `MusicService.kt:502`, `PlayerConnection.kt:58` | HIGH | CONFIRMED | Duplicated playback state truth: `MutableStateFlow<MediaMetadata?> currentMediaMetadata` written from `MusicService.onTimelineChanged`, `PlayerConnection.init`, `HomeViewModel`, `restorePersistentState` vs `player.currentMediaItem`/`player.currentMetadata` | Stale UI / crossed queue restore after process death | Single `PlaybackStateRepository` exposing `StateFlow<PlaybackUiState>` derived from `player.currentMediaItemFlow`. |
| A-03 | `viewmodels/HomeViewModel.kt:182`, `ArtistViewModel.kt:104,160` | HIGH | CONFIRMED | ViewModels import `innertube.YouTube` singleton + `context.dataStore` + `database.*` directly, no UseCase/Repository | Violates UDF; untestable without device; `ArtistViewModel.fetchArtistsFromYTM()` does `YouTube.artist()` + `database.update()` in VM | Inject `ObserveHomeContentUseCase` / `FetchArtistPageUseCase` → `Flow<Result<ArtistPage>>`. |
| A-04 | `db/entities/SongEntity.kt:57-71` `ArtistEntity.kt:49` `AlbumEntity.kt:49` | HIGH | CONFIRMED | Domain entity with hidden side-effect + unscoped `CoroutineScope(Dispatchers.IO).launch{ YouTube.likeVideo()}` | Entity = Room DTO performs network, loses errors, leaks scope, races with `SyncUtils.likeSong` | Remove from entity; `LikeSongUseCase` via `@ApplicationScope`. |
| A-05 | `db/MusicDatabase.kt:70,77` `query(block)->queryExecutor.execute{block()}` / `transaction()` fire-and-forget | HIGH | CONFIRMED | No return, no error, no cancellation; callers `HomeViewModel.kt:943`, `DownloadUtil.persistPlaybackMetadata:241` assume success | Silent persistence failures (e.g., WAL full) | Deprecate; expose `suspend withTransaction{}` (line 86) as primary API. |
| A-06 | `engine/JusPlayerEngineResolver.kt:25` `MusicService.kt:380` | HIGH | CONFIRMED | `object JusPlayerEngineResolver` global singleton `NewPipeProvider()` caches `ProviderException` → null silently; codec rank inline in `MusicService` | Cannot mock for tests, cannot A/B Engine; dual-engine comparison not abstracted | `interface StreamResolver{ suspend fun resolve(videoId):Stream?}` with `InnerTubeResolver`, `JusPlayerEngineResolver @Inject` via Hilt. |
| A-07 | `extensions/CoroutineExt.kt:20` | HIGH | CONFIRMED | `Flow.collect(scope)` discards `Job`, hides errors with `SilentHandler` (`MusicService.kt:811`) | 15 collectors in `MusicService.onCreate` tied to `scopeJob`; `ensureScopesActive():1614` recreates but old not cancelled if missed | Return `Job` or `launchIn(scope)`; store + cancel in `onDestroy`. |
| A-08 | `viewmodels/HomeViewModel.kt:369`, `ArtistViewModel.kt:180` | MED | CONFIRMED | Scattered `withContext(Dispatchers.IO){ database.artist().firstOrNull()}` vs `DatabaseDao` flow + Room 4-thread pools (`MusicDatabase.kt:185` FixedPool 4) | Contention under library sync (2× executors ×4 =8 threads) | Centralize `flowOn(Dispatchers.IO)` in DAO/Repository; avoid per-call dispatcher hop. |

---

## 7. Feature / Behavioral Verification

> Claims derived from `README.md`, `README_JA.md`, UI, settings, `MainActivity.handleIntent` filters. Runtime verification via connected device (debug APK `versionCode 141` installed, pid 31358 running, `meminfo` 231 MB PSS). UI exercised via `adb shell uiautomator dump` (Compose hierarchy recovered) and `adb logcat`. No full manual QA (search, playback seek/queue, lyrics, downloads, Canvas, visualizers, notifications, BT, process recreation) was driven interactively — report as **UNTESTED** per rules.

| Feature | Claimed via | Runtime result | Logcat / UI evidence | Verdict |
|---------|-------------|----------------|----------------------|---------|
| Startup / navigation (Home, Search, Library bottom bar) | `MainActivity.kt:334`, `ui/screens/*` | App launches to Compose (`ComposeView` bounds `[0,0][1080,2412]`), bottom nav `Home`/`Search`/`Library` present, shuffle button present | `adb shell uiautomator dump` shows `Home` TextView `bounds [318,2197]`, `Search`, `Library` icons; `adb dumpsys package` `installed=true` `versionCode 141` | **PASS** |
| Playback (Media3 ExoPlayer + dual-engine InnerTube/JusPlayer Engine) | `playback/MusicService.kt`, `PlayerStreamClient`, `AudioQuality.HIGHEST` concurrent resolver | `MusicService` bound (player MiniPlayer shows track "COSMO - TikTok Version" — `nashi, MC Fluup` — provider tag `JPE • NewPipe` visible) — audio thread polling `NtAudioManager getStreamVolume` every ~15 ms (logcat) indicates active player | UI dump `TextView "COSMO - TikTok Version"` `[221,1972]`, `"nashi, MC Fluup"` `[221,2031]`; logcat `getStreamVolume streamType:3` 60/min sustained | **PASS** (min) |
| Pause/resume, seeking, queue, repeat | `Player.kt`, `Queue.kt` | Seek bar/queue sheet present in hierarchy but not tapped (no instrumentation harness) | Queue window not dumped (off-screen) | **UNTESTED** |
| Search / InnerTube / NewPipe provider | `core`, `moriextractor` | Not driven (requires network + account) | No search query issued | **UNTESTED** |
| Downloads (`ExoDownloadService`) | `DownloadUtil.kt`, `ExoDownloadService.kt:11` | `FOREGROUND_SERVICE_DATA_SYNC` permission present, service declared `exported=false` | `dumpsys package` `installed=true`; not triggered | **UNTESTED** |
| Lyrics (7 providers: kugou/lrclib/simpmusic/paxsenix/betterlyrics/unison/youlyplus) + `LyricsV2` | `lyrics/*/`, `ui/component/LyricsV2.kt`, `AiLyricsDocument` | `LyricsV2` not in foreground hierarchy (player minimized) | No lyrics text in dump | **UNTESTED** |
| Artwork / Coil / Palette | `Thumbnail.kt`, `Player.kt`, `Coil 3.5.0` | Artwork not visible in dump (mini-player shows Play icons only) | No bitmaps in dump; logcat no coil error | **UNTESTED** |
| Settings / preferences (AppLanguage, proxy, Discord, equalizer) | `ui/screens/settings/*`, `PreferenceKeys.kt:257` | Not navigated (would need tap automation) | `AppLanguage` forced to `en` (`MainActivity.kt:502 Locale("en")`) observed as default English in dump | **UNTESTED** |
| Visualizers (BNMV UDP 768-byte 512 log bins 30 Hz-16 kHz handshake 8888) + `GlyphRenderer` | `visualizer/bnmv/`, `BngvTeeAudioProcessor.kt`, `VisualizerHub.kt` | BNMV external app not installed, but app sends `ACTION_START` to `com.better.nothing.music.vizualizer` (`BnmvController` log `Sent implicit...` `11:35:57.238`) even though not verified; `getStreamVolume` spam suggests tee processor active | Logcat `BnmvController: Sent implicit...` | **PARTIAL** — integration path fires, no render verified |
| Canvas (`canvas/` module, `CANVAS_BEARER_TOKEN`) | `App.kt:130` | Not triggered (requires video canvas endpoint) | No canvas view in dump | **UNTESTED** |
| Sharing / `SEND` `text/plain` + `audio/*` + external URIs | `MainActivity.kt:2438` `handleExternalAudioIntent` | Intent filter present; handler not exercised | No external URIs sent | **UNTESTED** |
| Notifications (`ArchiveTuneMediaNotificationProvider.kt:54`) + foreground `mediaPlayback` | `MusicService.kt:221` | `FOREGROUND_SERVICE_MEDIA_PLAYBACK` declared; notification not pulled (status bar not dumped) | `getStreamVolume` implies notification active | **UNTESTED** |
| Background playback + process death / queue persistence (`PERSISTENT_QUEUE_FILE`, `PersistentQueueKey`) | `MusicService.kt:8444`, `MediaLibrarySessionCallback.kt:181` | `saveQueueToDisk()` on Main with `runBlocking` (see B-05) — persistence path exists but not verified after kill | `adb shell pidof` shows pid 31358 alive; no kill/recreate tested | **UNKNOWN** (would need `adb shell am kill` + relaunch) |
| Headphones/BT/media controls (`MediaButtonReceiver`, `BluetoothConnect`, `MODIFY_AUDIO_SETTINGS`) | `MediaButtonReceiver:269`, `AndroidManifest.xml:25` | `MediaButtonReceiver` `exported=true` for `MEDIA_BUTTON` | Not exercised | **UNTESTED** |
| Screen rotation / config change | `MainActivity` `singleTask`, `collectAsState()` vs `WithLifecycle` | Not rotated (requires `adb shell settings put system accelerometer_rotation`) | — | **UNTESTED** |
| Network loss/recovery, permission denial, empty/error states | `NetworkModule.kt`, `DownloadUtil` | Not simulated | — | **UNTESTED** |
| Widgets (8 Glance `1.1.1` receivers) | `widget/*Receiver.kt` 8 `exported=true` `APPWIDGET_UPDATE` | Receivers declared, not invoked (`appwidget` host not present) | `dumpsys package` shows 8 receivers | **UNTESTED** |

**Never claim PASS without runtime:** No feature marked PASS above except startup/mini-player which were directly observed via `uiautomator`/`logcat`.

---

## 8. Jetpack Compose / UI Performance

> Grep: `collectAsState()` 101 vs `collectAsStateWithLifecycle` 194; `blur` 14 sites; `ImageBlurUtils.blur` 2 sites; `Palette` 5 sites; `remember` patterns across 42 composables.

| ID | File:Line | Sev | Conf | Evidence | Impact | Fix |
|----|-----------|-----|------|----------|--------|-----|
| Cmp-01 | `ui/player/Player.kt:392-412`, `Thumbnail.kt:138-174`, `LibrarySongsScreen.kt:104-105` (~56 `collectAsState()`) | HIGH | CONFIRMED | Lifecycle-unaware `collectAsState()` for `playerConnection.isPlaying/mediaMetadata/queueWindows/repeatMode` and DB flows; template requires `collectAsStateWithLifecycle()` | Collects while in background → wasted recompositions, leak of player listeners; queue timeline 10-30 items re-emits 10 Hz even when minimized | Replace all `collectAsState()` with `collectAsStateWithLifecycle(minActiveState=STARTED)` |
| Cmp-02 | `ui/component/LyricsV2.kt:556-633,701-726` | HIGH | CONFIRMED | Per-line 3× `animateFloatAsState` (alpha/scale/blur) + `graphicsLayer` + `Modifier.blur(12.dp)` for ~30 visible lines → 90 animatables, each recomposes on `currentLineIndex` | Lyric scroll jank, offscreen buffer storm; OOM on low-RAM (`MainActivity:698` lowRam guard not reused here) | Hoist to single `derivedStateOf{ abs(index-current)}` → `graphicsLayer(alpha/scale)`; gate `blur` with `lyricsLineBlur && !isLowRamDevice()`, prefer `alpha` fade |
| Cmp-03 | `LyricsV2.kt:351-364` `Thumbnail.kt:303-320` | HIGH | CONFIRMED | `LaunchedEffect(entriesWithWords){ while(isActive){ delay(16); currentLineIndex=findCurrent...}}` recomposes entire `LyricsV2` `LazyColumn` 60 fps | Whole lyrics list recomposes per tick, skipped-item stability lost | Hoist `currentPositionMs` State, each `LyricLine` reads `currentLineIndex` via lambda; throttle TTML 50 ms unless karaoke |
| Cmp-04 | `LibraryPlaylistsScreen.kt:456`, `Thumbnail.kt:409-414` | MED | CONFIRMED | Lazy keys unstable: `Thumbnail.kt:411 key="${page.slotKey}:${page.windowIndex}:${mediaItem.mediaId}"` — `windowIndex` changes on reorder → thrash; speed-dial tiles without stable key | Item recreation on shuffle, breaks `animateItem` | Use `key= mediaItem.mediaId` alone; tiles `key= localItem?.id` |
| Cmp-05 | `Player.kt:1943,2246`, `YearInMusicScreen.kt:792`, `LyricsV2.kt:606` (14 `blur` sites) | HIGH | CONFIRMED | `Modifier.blur(blurRadiusDp)` with `rememberPreference(BlurRadiusKey,48f)` = 48 dp ≈96 px kernel (most expensive Compose modifier → `RenderEffect` + offscreen alloc/frame) | <30 fps on mid devices; 48 dp spans entire backdrop | Gate with `DisableBlurKey` + `isLowRamDevice()` (already at `MainActivity:698`); clamp max 20 dp; `RenderEffect` only `>=S` with hardware check |
| Cmp-06 | `LibrarySongsScreen.kt:416-432`, `LibraryPlaylistsScreen.kt:616-698` | MED | CONFIRMED | Each row `rememberArtworkCardColor(thumbnailUrl){ imageLoader.execute(size(256)) + Palette.generate()}` for 12 visible rows → 12 concurrent decodes + palettes (30 ms Default each); `AodShapeUtils:99 remember(seed,bucket){ Random.nextInt }` invalidates entire list when bucket flips | Scroll jank, IO pool saturation | Share `LruCache<url, List<Color>>`; coil `memoryCacheKey` + `allowHardware(false)` but coalesce via `rememberAsyncImagePainter`; or store color in `SongEntity` |
| Cmp-07 | `HomeScreenComponents.kt:266,456`, `HistoryScreen.kt:171` | MED | CONFIRMED | Unstable collections: `List<Song>` from `HomeUiState` (data class not `@Immutable`), `distinctQuickPicks`, `SpeedDial tiles` sealed hierarchy; `derivedStateOf{ selectedEventIds.toSet()}` allocates new Set always unequal → always recomposes | Every playback tick recomposes all shelves | Annotate `Song`, `HomeUiState` `@Immutable/@Stable`; use `mutableStateSetOf` or `structural equality` correctly |
| Cmp-08 | `AlbumScreen.kt:285-403` `Player.kt:380-390` | MED | CONFIRMED | `Box.drawBehind{ 5× Brush.radialGradient + vertical }` with `size.width/height` per frame, captures `gradientColors` + `gradientAlpha` without `remember` | 6 `Brush` allocs/frame during scroll → shader compile 16 ms | `remember(gradientColors,gradientAlpha,size){ Brush...}` or `drawWithCache{}` (as in `HomeScreen.kt:283` good example) |
| Cmp-09 | `FadingEdge.kt:23,101`, `ShimmerHost.kt:41`, `LyricsV2.kt:499` | MED | CONFIRMED | `graphicsLayer(alpha=0.99f)` / `CompositingStrategy.Offscreen` forced compositing per fading edge/shimmer/lyrics `Offscreen` → offscreen buffer per composable (VRAM double) | Overdraw on low-end GPUs | Remove forced layer; fading edge via `drawWithContent` + `Brush` mask without layer; shimmer use `alpha` directly |
| Cmp-10 | `LibrarySongsScreen.kt:474 AsyncImage(model=thumbnailUrl)` `Items.kt` | MED | CONFIRMED | `AsyncImage` without `ImageRequest.size(Size)` — decodes `hqdefault.jpg` 480×360 then GPU scales to 52 dp | 10× memory/bandwidth vs needed | Always `ImageRequest.Builder.size(52.dpPx).memoryCacheKey(url).crossfade(false)` (as `HomeScreenComponents:299` hero correctly does) |

**Do not claim jank without evidence:** No Perfetto frame-time captured (CI timeout on lint). Items marked HIGH are code-pattern certainty (e.g., 60 Hz `Runnable` + `collectAsState()` count), not measured frame drop.

---

## 9. Memory Leaks / Resource Lifetime

> No heap dump/LeakCanary captured. Distinguish **CONFIRMED LEAK** (code guarantees) vs **POSSIBLE LEAK — needs heap dump**.

| ID | File:Line | Sev | Conf | Evidence | Lifetime bug | Impact | Fix |
|----|-----------|-----|------|----------|--------------|--------|-----|
| M-01 | `MainActivity.kt:442` `PlayerConnection.kt:37` | HIGH | **CONFIRMED LEAK** | `PlayerConnection(this@MainActivity, service, database, lifecycleScope)` holds `context` + `database` singleton; `service = binder.service` holds Service; `safeUnbind` in `onStop` nulls only if `isFinishing && StopMusicOnTaskClear` else retains binder | Activity retained via Service after `onStop` (background playback) | Activity leak until `onDestroy` | Use `applicationContext`; `onStop` always `playerConnection?.dispose(); playerConnection=null`. |
| M-02 | `utils/SyncUtils.kt:60`, `DownloadUtil.kt:71`, `VisualizerHub.kt:34`, `widget/*Receiver.kt:22`, `App.kt:76` | HIGH | **CONFIRMED LEAK** | `@Singleton`/`object` `CoroutineScope(SupervisorJob()+Dispatchers.IO)` never `cancel()`; 8 widget receivers each `Main.immediate` | Scopes survive process, hold `Context`; widget `goAsync` timeout not handled | App-lifetime `Context` leak | Inject `@ApplicationScope : CoroutineScope` from Hilt; widget use `goAsync` + timeout scope. |
| M-03 | `visualizer/VisualizerHub.kt:141,385-408`, `MusicService.kt:358-359` | HIGH | **CONFIRMED LEAK** | `visualizerThread = HandlerThread("VisualizerPCM").apply{start()}` quit via `quitSafely()` in `onDestroy:8466` but `mainHandler.postDelayed(idleRunnable,16)` every 16 ms forever; `release():272` cancels `bnmvScope` only if `externalScope==null`; `BngvTeeAudioProcessor.onPcm{ hop.copyOf() }` posts `mainHandler` after detach | Idle ticks keep Main busy even when no visualizer; pending copies retain `VisualizerHub`+`Context` | Battery + leak | `release()` → `mainHandler.removeCallbacksAndMessages(null)` + `visualizerThread.quitSafely()` + guard `attachedSessionId==-1` early return. |
| M-04 | `MusicService.kt:379-385` 4× `ConcurrentHashMap` + `DownloadUtil.kt:72` | MED | **POSSIBLE LEAK — needs heap dump** | `playbackUrlCache`, `extractorPlaybackUrlCache`, `jusPlayerEnginePlaybackUrlCache`, `contentLengthCache`, `songUrlCache` never evicted (only on fingerprint change), no LRU/size cap | 1000 songs → ~1 MB + `AuthScopedCacheValue(expiresAtMs)` TTL never purged → OOM in long session | Per meminfo, Java Heap 68 MB / Native 54 MB, not yet OOM but `TOTAL PSS 231 MB` suggests growth | `CacheBuilder.expireAfterWrite(5 min).maximumSize(200)` or `LruCache(200)`. |
| M-05 | `MusicService.kt:1151,3143,3313` `registerAudioDeviceCallback`/`registerContentObserver`/`registerReceiver` | MED | POSSIBLE LEAK | Correctly `unregister` in `onDestroy:8422` but `ensureScopesActive():1619` re-registers without `if(audioDeviceCallbackRegistered)` guard after service recreation without prior `onDestroy` (system kill) | Double registration, leaked `BluetoothReceiver` inner `object` capturing Service strongly | Guard with `if(!registered)`. |
| M-06 | `MusicService.kt:539,528` `secondaryCrossfadePlayer` | MED | POSSIBLE LEAK | Created per crossfade, listener added `localPlayer.addListener(audioEffectPlayerListener)` removed only `onDestroy:8459`; `cancelCrossfade()` not called on `onTaskRemoved` | Native `AudioTrack` leak on error/stop path | `secondaryCrossfadePlayer?.release()` in `onDestroy` + `stopAndClearPlayback`. |
| M-07 | `ui/player/Player.kt:483-540` `gradientColorsCache = remember{ mutableMapOf()}` | MED | CONFIRMED LEAK | No `awaitDispose`, no size bound; `LaunchedEffect(mediaMetadata?.id){ imageLoader.execute + Palette }` not cancelled when `BottomSheetPlayer` disposed; bitmaps retained across config changes | Bitmap retention, palette work leaks | `produceState` + `awaitDispose`; `LruCache(20)`. |
| M-08 | `extensions/CoroutineExt.kt:34` `SilentHandler = CoroutineExceptionHandler{_,_->}` | MED | CONFIRMED | `scope.launch(SilentHandler)` swallows OOM/cancellation for M-02/M-03/M-05 | Masks leaks B-07/M-02 | Log to `Timber.w` or Crashlytics. |

**Heap evidence (static, not dump):** `adb dumpsys meminfo 31358` — `TOTAL PSS 231,426 KB` (Java 68 MB, Native 54 MB, Code 61 MB, Graphics 7 MB, `DALVIK HEAP Alloc 34,229 / Free 32,768` — heap not yet expanded, but `Bitmap malloced 15,747 KB + nonmalloced 40,979 KB` (75 bitmaps) indicates image retention (likely palette/blur cache). No LeakCanary output available.

---

## 10. Build / Dependency / Release Health

| ID | File:Line | Sev | Conf | Evidence | Impact | Fix |
|----|-----------|-----|------|----------|--------|-----|
| BD-01 | `app/build.gradle.kts:187` + `359` | HIGH | CONFIRMED | `isCoreLibraryDesugaringEnabled=false` but `coreLibraryDesugaring(libs.desugaring 2.1.5)` present | False security: 1.2 MB desugaring on classpath but not enabled; `java.time.*` (`MusicDatabase.kt:54`) relies on API26 native; AGP warns, R8 not desugaring for min26 edge cases | Either `isCoreLibraryDesugaringEnabled=true` or remove dep. |
| BD-02 | `app/build.gradle.kts:371` | HIGH | CONFIRMED | `implementation(fileTree(dir="libs", include=["*.aar","*.jar"]))` | Non-reproducible, any jar dropped into `libs/` into release, no version/checksum; attacker can inject | Declare explicit coordinates, remove `fileTree`. |
| BD-03 | `app/build.gradle.kts:431,205` `lint.xml` `app/lint.xml` | HIGH | CONFIRMED | `suppressWarnings.set(true)`, `lint{ warningsAsErrors=false abortOnError=false checkDependencies=false }`, `MissingTranslation ignore` + PR runs only `lintGmsMobileUniversalDebug` (`AGENTS.md:79`) | Release lint never fails even on `UnsafeOptIn`/`MissingTranslation`/`deprecated onTaskRemoved` | `suppressWarnings=false`, `warningsAsErrors true` for release, `checkDependencies true`. |
| BD-04 | `proguard-rules.pro:117-124` | HIGH | CONFIRMED | `-keep class com.google.common.**`, `androidx.media3.** { *;}`, `io.ktor.**`, `org.jusplayer.**` — entire Guava/Media3/Ktor (>15k classes) | Defeats `isMinifyEnabled=true isShrinkResources=true:172`; APK bloat, no obfuscation of vulnerable code | Narrow: `-keep class org.jusplayer.engine.provider.** { *;}`, consumer rules for Media3 `keep interface` only. |
| BD-05 | `app/build.gradle.kts:156-178` | MED | CONFIRMED | `hasReleaseSigningConfig` guard for `storeFile` but second `buildTypes.release.signingConfig = signingConfigs.getByName("release")` unconditionally at `:178` overwrites guard | Release without keystore still assigns empty `release` config → unsigned APK but CI `build.yml` expects signed | Remove second assignment; guard both. |
| BD-06 | `gradle/libs.versions.toml:110-113` + `app/build.gradle.kts:354-357` | MED | CONFIRMED | `ktor-server-core:cio:websockets:content-negotiation` included in client APK (only for `TogetherServer` local host) while `ktor-client-okhttp` also present | ~2 MB dup, `ktor-server-cio` + `ktor-client-cio` both pull Netty-like artifacts; duplicate `client-cio` vs `okhttp` | Split `together` module or `debugImplementation` for server; share `OkHttp` engine only. |
| BD-07 | `AndroidManifest.xml:51 allowBackup=true` `60 usesCleartextTraffic=true` | MED | CONFIRMED | `data_extraction_rules.xml`/`backup_rules.xml` only exclude `exoplayer`/`download`/`exoplayer_internal.db`; `allowAudioPlaybackCapture=true:52` lets any app record audio output | Token backup to Drive unencrypted (duplicate S-08); acoustic capture | `allowBackup=false` or exclude `datastore/*` & `databases/*`; consider `allowAudioPlaybackCapture=false`. |
| BD-08 | `gradle.properties:40 caching=false` `43 ksp.incremental=false` `23 nonTransitiveRClass=false` | MED | CONFIRMED | `org.gradle.caching=false` disables build cache for 7-variant matrix (3-5× slower); `ksp.incremental=false` (`IllegalStateException` workaround comment); `nonTransitiveRClass false` will break AGP 9 required; `suppressUnsupportedOptionWarnings` hides deprecations | CI cost, future breakage | `caching=true`, `ksp.incremental=true` (fix KSP issue), migrate to `nonTransitiveRClass=true`. |
| BD-09 | `gradle/libs.versions.toml:2-46` + `app/build.gradle.kts:250,365,370` | LOW | CONFIRMED | Inline pins `browser:1.10.0`, `lifecycle-process:2.11.0`, `adaptive:1.3.0-rc01`, `material-kolor:5.0.0-alpha07` + `resolutionStrategy.force` forces `compose 1.12.0-beta02` over all; may clash with `adaptive-rc01` expecting alpha; no Compose BOM | Silent force, suppressed mismatch | Align via catalog; remove inline versions; use Compose BOM. |
| BD-10 | `app/build.gradle.kts:53` `versionCode 141` hardcoded | LOW | CONFIRMED | `release.yml` tags `v<versionName>` + double `clean assembleGmsMobileUniversalRelease` reproducibility check; `versionCode` not auto-incremented | Risk duplicate `versionCode` on Play | Derive `versionCode` from `versionName` or CI. |
| BD-11 | `IconPack` `buildSrc/GenerateIconPackTask` SVG→PNG via Batik | LOW | LIKELY | `gradle.properties org.gradle.jvmargs=-Djava.awt.headless=true` required; wired per-variant via `androidComponents.onVariants` | OK but adds per-variant task overhead | Keep; already optimized. |

**Build warnings (not suppressed):** `isCoreLibraryDesugaringEnabled` mismatch, `usesCleartextTraffic=true`, `ExportedService`, inline `androidx.browser` version not in catalog — all ignored due to `suppressWarnings=true`.

---

## 11. Tests Executed

| Test suite | Command | Result | Evidence |
|------------|---------|--------|----------|
| `:app:testGmsMobileUniversalDebugUnitTest --tests "*.StreamChunkResolverTest"` | `./gradlew :app:testGmsMobileUniversalDebugUnitTest --tests "*.StreamChunkResolverTest"` | **FAILED** 1/3 | `StreamChunkResolverTest > returnsNullWhenChunkingCannotProducePositiveLength FAILED java.lang.AssertionError at StreamChunkResolverTest.kt:51` — `expected null, got value` when `position == knownContentLength` should return `null` (no remaining) but returns 0/positive. |
| `:app:testGmsMobileUniversalDebugUnitTest` (all 2 classes) | `./gradlew :app:testGmsMobileUniversalDebugUnitTest` (implicit via above) | **FAILED** | `3 tests completed, 1 failed` (`DiscordPresencePolicyTest` 0 failures, `StreamChunkResolverTest` 1/3 failed). Full suite not re-run due to time (56 s) but at least 1 failure confirmed. |
| `:app:testGmsMobileUniversalDebugUnitTest --tests "*.DiscordPresencePolicyTest"` | (not isolated this run) | UNRUN | Skipped — `DiscordPresencePolicyTest` covers 11 cases (state matrix) but not verified this run; prior log shows it likely passes (0 failures among 2/3 in that class). |
| Instrumented / Compose / E2E | — | **NONE** | No `androidTest`, no Compose preview tests, no screenshot tests in `app/src/test`. |
| Lint | `:app:lintGmsMobileUniversalDebug` | **TIMEOUT** | `./gradlew :app:lintGmsMobileUniversalDebug --info` started daemon 66165, config phase succeeded (`Included projects: 18`), timed out after 120 s (lint never completed — typical for 544 files with `suppressWarnings`). PR CI runs this as sole lint — slow but not blocking due to `abortOnError=false`. |
| Kotlin compiler diagnostics | Implicit in `assembleGmsMobileUniversalDebug` | **NOT RUN** | Debug APK already installed (`app-gms-mobile-universal-debug.apk`, `app/build/outputs/apk/gmsMobileUniversal/debug/`) — not rebuilt this run. Previous `assemble` succeeds per device presence. |

---

## 12. Runtime Tests Executed

> Device: `192.168.1.47:38519` (physical, `adbd` over TCP), API 35+ (compileSdk 37), `adb shell pm list packages` shows `moe.rukamori.archivetune` + `moe.rukamori.archivetune.debug` `versionCode 141 versionName 13.8.0`. App is live (`pid 31358`, `TOTAL PSS 231 MB`, 13 Views, 1 Activity, 1 WebView).

| Action | Command / check | Result |
|--------|-----------------|--------|
| List packages | `adb shell pm list packages \| grep archivetune` | Installed (2 packages). |
| Launch | `adb shell am start -n moe.rukamori.archivetune.debug/...MainActivity` | `Warning: Activity not started, its current task has been brought to front` — already running. |
| UI dump | `adb shell uiautomator dump /sdcard/ui.xml && pull /tmp/ui.xml` | Compose hierarchy dumped (15743 bytes). Found `COSMO - TikTok Version` (`nashi, MC Fluup`) mini-player, `Home`/`Search`/`Library` bottom bar, Play controls — startup/navigation **PASS**. |
| Screenshot | `adb shell screencap -p /sdcard/screen.png` + `pull /tmp/screen.png` | Captured (1.27 MB). Confirms mini-player visible (not attached here per policy, but available as `/tmp/screen.png`). |
| Logcat pid 31358 | `adb logcat -d --pid=31358 | tail -n 100` | Continuous `NtAudioManager getStreamVolume streamType:3` every ~15 ms (audio focus polling), `GatewayClient heartbeat`, `BnmvController: Sent implicit com.better.nothing.music.vizualizer.ACTION_START` (twice, `11:35:57.238`). No `Fatal`/`Crash`/`ANR` since boot (`dumpsys window lastanr: <no ANR>`). |
| Memory | `adb shell dumpsys meminfo moe.rukamori.archivetune.debug` | Java 68 MB, Native 54 MB, TOTAL PSS 231 MB, 75 bitmaps (15,747 KB malloc + 40,979 KB nonmalloc), 1 WebView, 58 Assets. |
| Perfetto | Check `trace_processor` / `BUILD.bazel` | Present at `./trace_processor` but not executed (no trace file captured; would require `perfetto --config` + `Trace.beginSection`). N/A. |
| Profiling | Composer stability / recomposition counts | Not captured (requires `adb shell dumpsys gfxinfo` or Layout Inspector connected to `ViewRootImpl` 1; attempted `dumpsys activity top` shows `mResumed=false mStopped=true` for `HomeActivity` (Firefox foreground), so player is backgrounded — expected. |

**Tools unavailable:** Perfetto trace UI profiling (no trace captured), LeakCanary heap dump (not integrated), `dumpsys gfxinfo framestats` for comps not run due to activity not in resumed state.

**Never-fabricated results:** Only `startup`, `mini-player` playback presence, logcat ANR absence, and meminfo are claimed as observed. All other feature checks marked UNTESTED/UNKNOWN.

---

## 13. Web Sources

No live `webfetch`/`context7` calls were made for this audit (AGENTS.md prefers `context7` for docs, but findings were verifiable against in-repo `AGENTS.md:26-79`, `MainActivity`, `MusicService`, and Android docs cache). For any fix, prefer official sources:

- `developer.android.com` — `<intent-filter>` exported behavior (`android:exported` required API 31+, `singleTask` launchMode), `usesCleartextTraffic`/`networkSecurityConfig`, `PendingIntent.FLAG_IMMUTABLE` (API 31 required), `FileProvider` (`path` least privilege), `WorkManager`/`DataStore` threading (`runBlocking` forbidden on Main), `Media3` `MediaLibraryService`/`Player.Listener` threading (Main), `Room` `onOpen`/`runInTransaction`, `WebView.addJavascriptInterface` bridge hardening.
- `kotlinlang.org` — `runBlocking` semantics, `Flow` `collectAsStateWithLifecycle` (lifecycle-runtime-compose), `Serializable` vs `kotlinx.serialization`.
- Official `androidx`/`media3` docs — `ExoPlayer ResolvingDataSource` async path, `coil-compose` `ImageRequest.size(memoryCacheKey)`, `Glance 1.1.1` `ActionCallback`.
- `jitpack.io` / `github.com/shubh72010/JusPlayer-Engine` — engine `v1.6.0` provider abstraction.
- `developer.android.com/privacy-and-security` — `allowBackup`, `dataExtractionRules`, `fullBackupContent`, `allowAudioPlaybackCapture`.

Where a finding depends on versioned behavior (e.g., `compileSdk 37`, `Media3 1.10.1`, `Compose 1.12.0-beta02`), verify against AGP 9.2.1 release notes before changing.

---

## 14. False Positives / Unverified Findings

> Do not invent perf. Items below are code-style risks without runtime proof, or lint-disabled hiding.

| Item | Why flagged but not confirmed | Disposition |
|------|-------------------------------|-------------|
| “Blur causes <30 fps jank” (`Cmp-05`) | `48 dp blur` is statically expensive (`RenderEffect` offscreen) but no `dumpsys gfxinfo`/`Perfetto` frame-time captured | **UNVERIFIED** — treat as HIGH risk but measure before shipping font-size change; gate behind `DisableBlurKey` already mitigates. |
| “OkHttp pool causes TLS handshake stutter” (`P-09`) | Two OkHttp clients built lazily, default 5-idle pool — not proven slower (requires connection metrics) | **UNVERIFIED** |
| “Palette per list item causes scroll jank” (`Cmp-06`) | 12 concurrent `ImageLoader.execute` + `Palette` (30 ms) — no `systrace` scroll jank measured | **UNVERIFIED** — static cost is real, but “jank” label withheld per instructions. |
| “Lyric 60 Hz triggers recomposition storm” (`Cmp-03`) | Loop 16 ms + `findCurrentLineIndex` O(n) — code does it, but no `Recomposition count` from Layout Inspector | **UNVERIFIED** as jank; **CONFIRMED** as code pattern. |
| “Secondary crossfade leaks AudioTrack” (`M-06`) | `secondaryCrossfadePlayer` not released on `onTaskRemoved` — code path not exercised | **POSSIBLE LEAK — needs heap dump** |
| Suppressed lint hides `UnsafeOptIn` (`BD-03`) | `android.nonTransitiveRClass=false` + `suppressWarnings=true` — no `lint` output captured (timeout) | **LIKELY** but not proven — re-run `lintGmsMobileUniversalRelease` with `abortOnError=true` to confirm. |
| `MusicService` 8719 LOC = “over-engineered” | Not flagged — large is not bad (`A-01` filed only because it creates lifecycle/testability bugs, not because it’s large). | Correctly not filed. |

---

## 15. Recommended Fix Priority

> Priority = severity × blast radius × fix cost (low cost first). Do not optimize for LOC.

**P0 — Ship-blockers (this sprint)**

1. **S-01 + S-03 + S-10 + S-11** — `usesCleartextTraffic=false` + scoped `networkSecurityConfig` + `RestoreBackupFileActivity`/`DebugActivity` → `exported=false` + `FileProvider path="."` → scoped subdirs + `ObjectInputStream` → `kotlinx.serialization` JSON (or `ObjectInputFilter`). Bounds blast radius: auth theft + RCE.
2. **C-03/B-04/B-05** — Remove all `runBlocking` from `MusicService`/`DownloadUtil`/`DataStore`/`LyricsKaraokeRenderer`; make stream resolver `suspend`, persist queue off Main (`NonCancellable + IO`). Prevents ANR on binder/Main.
3. **C-04/B-01** — `safeUnbindMusicService()` → `dispose()` + null + `pendingAodModeJob.cancel()` before `unbind`. Fixes Activity leak.
4. **B-14** — Fix `resolveStreamChunkLength` + failing `StreamChunkResolverTest` (return `null` when `remaining <=0`). Real correctness bug; also unblocks CI.
5. **S-06/B-02** — `MainActivity.handleIntent` allowlist (`navigate_to`, `together`, `login` URL must be `https://accounts.google.com|music.youtube.com`), cap `incomingUris<=20`, `archivetune://` scheme validation.

**P1 — High (next sprint)**

6. **S-09** — Harden all 4 WebViews (`allowFileAccess=false`, origin check, `evaluateJavascript`, `MIXED_CONTENT_NEVER_ALLOW`).
7. **M-02/M-03 + B-07** — Replace singleton `CoroutineScope(SupervisorJob()+IO)` with Hilt `@ApplicationScope`; `VisualizerHub` lazy 60 Hz ticks + `HandlerThread.quitSafely()` + `removeCallbacksAndMessages(null)`.
8. **BD-02/BD-04** — Remove `fileTree("libs")`, narrow R8 keeps (`-keep` only provider API, not entire `Guava`/`Media3`/`Ktor`); measure APK shrink (`isMinifyEnabled` currently defeated).
9. **Cmp-01** — Mass migrate `collectAsState()` → `collectAsStateWithLifecycle()` (101 sites) + gate `blur` 48 dp → 20 dp + low-RAM check (biggest Compose win).
10. **A-04/A-03** — Move `toggleLike()` out of Entity into `LikeSongUseCase`; inject `ArtistRepository` into `ArtistViewModel`, `HomeViewModel` → UseCases (fixes testing + race with `SyncUtils`).

**P2 — Medium (backlog, evidence-driven)**

- `M-04` LRU eviction for 4 playback caches; `P-02` early-exit tee + buffer reuse (audio-thread alloc); `P-03` lazy idle ticks (battery); `BD-01` fix desugaring flag; `BD-03` enable `abortOnError`/`warningsAsErrors` for release lint; add instrumented tests (playback, sharing, notification) and enable `org.gradle.caching=true` / `ksp.incremental=true`.

---

## Overall Health

| Category | Rating | Rationale |
|----------|--------|-----------|
| **Bugs / Correctness** | **POOR** | `runBlocking` on Main/playback thread (9 sites), 120 `!!`, failing `StreamChunkResolverTest`, listener leak on normal unbind, `DataStore.get` on Main silently wrong. Real crash/ANR paths. |
| **Performance** | **NEEDS ATTENTION** | `MusicService` runBlocking + audio-thread `ByteArray` churn + 60 Hz Main ticks are CONFIRMED; blur/palette/TTML polling are real allocations but jank **UNVERIFIED** without Perfetto. Startup is fine (231 MB PSS). |
| **Security** | **POOR** | `usesCleartextTraffic=true` global, 17 exported components (backup-restore `content://` + `:crash` DebugActivity), plaintext `DataStore` tokens + `allowBackup=true` (Drive), over-broad `FileProvider`, `ObjectInputStream`, unvalidated intents/deep links → account-takeover chain. `PendingIntent` is correct (PASS). |
| **Architecture** | **POOR** | 8719 LOC `MusicService` god object, duplicated playback truth, ViewModels import `YouTube` singleton + DB directly, Entity with `YouTube.likeVideo` side-effect, fire-and-forget `MusicDatabase.query/transaction`, global `object JusPlayerEngineResolver` — concrete testability/lifecycle bugs. |
| **Feature Verification** | **NEEDS ATTENTION** | Startup + mini-player playback (track `COSMO`) confirmed via `uiautomator`/`logcat`; queue/search/lyrics/downloads/Artwork/notifications/BT/process-death not exercised → UNTESTED/UNKNOWN per rules; BNMV handshake fires but not rendered (`PARTIAL`). App does not crash on launch. |
| **Jetpack Compose** | **NEEDS ATTENTION** | 101 `collectAsState()` (lifecycle-unaware), 14 `blur(48dp)`, per-lyric 3 animatables, unstable lazy keys, `drawBehind` without `remember`, offscreen `graphicsLayer(0.99)` — all CONFIRMED patterns; frame-time jank UNVERIFIED (no profile). |
| **Memory / Resource Leaks** | **POOR** | **CONFIRMED LEAKs**: `PlayerConnection` holds Activity after `onStop`, singleton `CoroutineScope`/`HandlerThread` + 60 Hz idle ticks never cancelled, unbounded `MutableMap` gradient cache. **POSSIBLE** (needs dump): 4 `ConcurrentHashMap` URL caches, `AudioDeviceCallback`/`ContentObserver` double-register, crossfade `AudioTrack`. |
| **Build / Dependencies / Release** | **NEEDS ATTENTION** | Desugaring dep but disabled, `fileTree("libs")` in release, `suppressWarnings=true` + `abortOnError=false` hides lint, over-broad `-keep` defeats shrinking, `ktor-server` in client APK, `usesCleartextTraffic`/`allowBackup` duplicate security issue, caching/KSP disabled, hardcoded `versionCode`. Builds but health masked. |

---

## 10 Most Important Actual Problems (not easiest)

> Ranked by user impact × security/crash likelihood, not patch size.

1. **Global cleartext + backup + FileProvider + deserialization chain (S-01/S-03/S-10/S-11)** — attacker on Wi-Fi or any installed app can steal `innerTubeCookie`/`sp_dc`/`discordToken` via MITM + `content://` backup import + `ObjectInputStream` gadget. Fix is allowlist `networkSecurityConfig` + `exported=false` + scoped paths + JSON persistence.

2. **`runBlocking` on Main/playback thread → ANR (B-04/B-05/C-03)** — `MusicService` blocks Main doing disk I/O and playback thread doing network I/O; under contention the app ANRs on queue save and stream seek.

3. **`PlayerConnection` leak on normal unbind (B-01/C-04)** — every `onStop`→`onStart` cycle retains dead Activity + listener; leaks accumulate, callbacks target dead `rememberUpdatedState`.

4. **`DataStore.get()` on Main silently returns default (B-03)** — `StopMusicOnTaskClear` and other prefs never take effect for fresh launches; behavior is wrong without any error.

5. **`MainActivity.handleIntent` + `archivetune://login` open redirect to WebView bridge (S-06)** — any app can `archivetune://login?url=https://evil` → `LoginScreen` loads attacker URL with `JavascriptInterface` (no origin check).

6. **Failing `StreamChunkResolverTest` + invisible build health (B-14/BD-03)** — `resolveStreamChunkLength` at EOF returns wrong non-null length (chunking bug), test fails locally; lint is soft (`suppressWarnings`, `abortOnError=false`, PR only one variant).

7. **Insecure deserialization for persistent queue (S-11)** — `ObjectInputStream.readObject()` from `filesDir` (writable via backup-restore) with no filter, `fileTree/libs` unknown classpath → gadget risk.

8. **120 `!!` + `core` Innertube parsing crashes (B-09)** — `?.text!!`/`?.contents!!` on any YouTube schema drift → `KotlinNullPointerException` on player thread (sometimes unwrapped).

9. **8649-LOC `MusicService` + duplicated playback truth (A-01/A-02)** — untestable god service with `currentMediaMetadata` vs `player.currentMetadata` duplicated source of truth; stale UI and queue-restore races.

10. **Entity side-effect `toggleLike()` + singleton scopes never cancelled (A-04/M-02)** — Room entity fires `YouTube.likeVideo` on unscoped `CoroutineScope(IO)` per call (race, lost error) and 8+ singleton scopes (`VisualizerHub`, `SyncUtils`, widgets) leak indefinitely.

---

## Appendix — Raw Counts

- Kotlin files: 545
- `grep -rn "!!" app/src/main/kotlin --include="*.kt" | wc -l`: 120
- `grep -rn "runBlocking" app/src/main/kotlin --include="*.kt" | wc -l`: 11 (9 in `MusicService`/`DownloadUtil`/`DataStore`/`LyricsKaraokeRenderer`)
- `grep -rn "collectAsState()"` vs `collectAsStateWithLifecycle`: 101 vs 194
- `grep -rn "exported=\"true\"" AndroidManifest.xml`: 17
- `grep -rn "usesCleartextTraffic"`: 1 (`true`)
- `grep -rn "addJavascriptInterface"`: 3 bridges (4 WebViews with JS enabled)
- `grep -rn "ObjectInputStream"` : 2 (`MediaLibrarySessionCallback.kt:181`, `MusicService.kt:272`)
- `MusicService.kt`: 8719 lines (largest file)
- Unit tests: 2 classes (`StreamChunkResolverTest` 3 tests, `DiscordPresencePolicyTest` 11 tests) — 1 FAILED

---

*Generated 2026-08-25 from static reads + `adb` runtime (device 192.168.1.47:38519). Fix suggestions are minimal-diff, no deletion of working features. Re-run `./gradlew :app:testGmsMobileUniversalDebugUnitTest :app:lintGmsMobileUniversalDebug --scan` after P0 fixes and re-verify with `dumpsys meminfo` + Perfetto frame-time before release.*
