# JusPlayer — AGENTS.md

Kotlin/Jetpack Compose YouTube Music client (`moe.rukamori.archivetune`, `app/src/main/kotlin/moe/rukamori/archivetune/MainActivity.kt`). Fork of ArchiveTune. MVVM + UDF, Hilt/Room/Media3/Ktor/Coil 3.

## Commands — always variant-qualified

3 flavor dims `gms|foss` × `mobile|tv` × `universal|arm64|armeabi|x86|x86_64` = 20 variants. Default debug = `GmsMobileUniversalDebug` (`applicationIdSuffix=.debug`). Unqualified `assembleDebug`/`lintDebug`/`testDebugUnitTest` build all 20 or fail — never use.

```bash
./gradlew assembleGmsMobileUniversalDebug              # single debug APK (fast path)
./gradlew :app:compileGmsMobileUniversalDebugKotlin    # compile only, fastest correctness gate
./gradlew :app:lintGmsMobileUniversalDebug             # lint (only this variant runs in CI)
./gradlew :app:testGmsMobileUniversalDebugUnitTest     # all unit tests
./gradlew :app:testGmsMobileUniversalDebugUnitTest --tests "*.SubsonicIdTest"   # single test
```

`CONTRIBUTING.md` is stale (`assembleDebug`, `ktlintCheck`, JDK 17) — ignore. Required JDK 21; no ktlint/detekt in CI.

## Structure

- `:app` is the only Android module. Flavor sources `app/src/{gms,foss,tv,debug}`; Cast (`mediarouter`) is `gmsImplementation`-only.
- 4 optional submodules pulled in via `includeIfPresent` in `settings.gradle.kts`: `core` (InnerTube), `lyrics` (7 provider modules: kugou/lrclib/simpmusic/paxsenix/betterlyrics/unison/youlyplus), `moriextractor`, `morideobfuscator` — the build succeeds without them. The 5th, `IconPack`, is NOT a Gradle module and not in `includeIfPresent`; it is the SVG source dir consumed by `buildSrc/GenerateIconPackTask` (needs `-Djava.awt.headless=true`, already in `gradle.properties`), wired per-variant via `androidComponents.onVariants`.
- `buildSrc/SettingsSearchIndexTask` **generates** `SettingsSearchIndex.g.kt` (settings search rows) at build time — edit the scan inputs, never the generated file.
- In-repo JVM modules: `:lastfm`, `:canvas`, `:shazamkit` (standard `src/main/kotlin`), `:spotifycore` (`kotlin.srcDir("src")`, not `src/main/kotlin`).
- JusPlayer Engine `com.github.shubh72010.JusPlayer-Engine:engine-{api,provider-newpipe,provider-api,model}:v1.6.0` from JitPack `exclusiveContent` — `engine-provider-newpipe` excludes `extractor` to avoid duplicate `TeamNewPipe:NewPipeExtractor:0.26.3`.
- `subsonic/` is a self-contained client package (see Architecture).

## Toolchain

`gradle/libs.versions.toml`: JDK 21 (`JvmTarget.JVM_21`), AGP 9.2.1, Kotlin 2.4.0, Gradle 9.6.1, Compose 1.12.0-beta02, media3 1.10.1, Room 2.8.4, Hilt 2.60.1, Ktor 3.5.1, Coil 3.5.0, `compileSdk=targetSdk=37`, `minSdk=26`. No Compose BOM — `resolutionStrategy.force` in `app/build.gradle.kts` pins the Compose + kotlin-metadata coordinates.

`gradle.properties` quirks: `org.gradle.caching=false`, `ksp.incremental=false`, `android.nonTransitiveRClass=false`, `org.gradle.configuration-cache=true` (problems=warn), `org.gradle.parallel=true`. Room schemas: `app/schemas` (`room.schemaLocation` arg).

## Secrets & Signing

Build secrets are read in `app/build.gradle.kts` from `local.properties` then env (missing = feature disabled, not an error; precedence is per-key, so don't assume it's uniform): `LASTFM_API_KEY`/`LASTFM_SECRET`, `TOGETHER_BEARER_TOKEN`, `CANVAS_BEARER_TOKEN`, `EXTRACTOR_BEARER`, `DISCORD_APPLICATION_ID` (default `1165706613961789445`), `NIGHTLY_BUILD_HASH` (7-char SHA, dev only).

Release signing: `app/keystore/release.keystore` with `RELEASE_KEYSTORE_PASSWORD`/`RELEASE_KEY_ALIAS`/`RELEASE_KEY_PASSWORD`, falling back to the unprefixed `STORE_PASSWORD`/`KEY_ALIAS`/`KEY_PASSWORD`. If the keystore file is present but credentials are missing, **release tasks throw a GradleException** naming the missing keys (non-release builds only warn and use a placeholder). If the keystore file is *absent* and a release task runs, it also throws — it never falls back to a debug key. CI supplies the keystore via the `RELEASE_KEYSTORE_BASE64` secret, decoded before any Gradle invocation. `*.jks`/`*.keystore` are gitignored, but `app/persistent-debug.keystore` + `Koiverse.jks` (+ `.base64`) are tracked and unused by the signing block.

Subsonic and other user credentials are **runtime DataStore values, never build secrets** — never hardcode a server URL or password in code or tests.

## Architecture — check before UI/arch changes

- Strict UDF: stateless UI → ViewModel (sealed `Loading/Success/Empty/Error`) → UseCase → Repository. Composables use `collectAsStateWithLifecycle()`. Map DB/DTOs to UI models outside composables. `.github/copilot-instructions.md` has the blocking PR checklist.
- Playback dual-engine: InnerTube (`YTPlayerUtils`/`WEB_REMIX`) + JusPlayer Engine (`NewPipeProvider.getStream` via `engine/JusPlayerEngineResolver.kt`, cached, fallback to InnerTube). `PlayerStreamClient` (`constants/PreferenceKeys.kt`) has 9 values: WEB_REMIX, ARCHIVETUNE_EXTRACTOR, JUSPLAYER_ENGINE, SUBSONIC + ANDROID_VR/HI_RES_LOSSLESS/IOS/TVHTML5/ANDROID_MUSIC client spoofs. `AudioQuality.HIGHEST` runs `resolveMaxQualityDualEngineDataSpec()` concurrently, picks higher bitrate (tie <8 kbps) then codec rank opus 3 > aac 2 > other 1, tie → InnerTube (extractor and Subsonic excluded from winning ties).
- **Crossfade is two systems sharing one audio path — this is the easiest thing in the repo to break.** Audio blends over `crossfadeDurationMs` via a second `ExoPlayer`; the UI must switch *instantly* or taps look ignored.
  - `canUseGlobalCrossfade()` requires ALL of: enabled, duration ≥ `MIN_CROSSFADE_DURATION_MS` (500), player READY + playing, Together session `Idle`, not already crossfading, no deck mixer.
  - `globalCrossfadePreviewIndex: StateFlow<Int?>` is the UI's source of truth *during* a blend. `PlayerConnection` mirrors it into `currentMediaItemIndex`/`currentWindowIndex`; `Thumbnail.kt` reads the connection index, never `player.currentMediaItemIndex`. Cleared on handoff/fallback in `finishCrossfade`/`volumeFadeSeek`.
  - `optimisticPlayMediaId` does the same job for `playQueue` (list taps): the tapped song publishes on the tap frame and is held against stale player events until the player actually lands on it (10 s ceiling, self-healing).
  - **Any new write to `currentMediaMetadata` must be guarded** by `!isGlobalCrossfadePreviewActive() && !isOptimisticPlayPending()` (see the guards in `onMediaItemTransition`/`onEvents`), or the UI snaps back mid-fade.
  - Repeat tap on the pending target returns `true` (swallowed, so the Queue fallback doesn't cut the blend); a *different* target retargets. `tryGlobalCrossfadeToNext/Previous` chain from the preview anchor.
- Subsonic (provider-agnostic: Navidrome, Airsonic-Advanced, Gonic, Ampache): `subsonic/` holds client (Ktor, `u/t/s` salted-MD5 auth), `SubsonicId` (`subsonic:<serverId>:<kind>:<id>`), models/mapper/repository. Track ids must survive the `SubsonicId` round-trip. Two playback paths in `MusicService`: `resolveSubsonicDataSpec` (direct server track) and `resolveSubsonicMatchDataSpec` (match a queued YouTube track to the server, fall back to YouTube when unmatched). Matched playback uses a distinct `subsonic-match:` cache key so it can't collide with cached YouTube bytes. `SubsonicStrictOnlyKey` makes an unmatched track throw a `PlaybackException` that the existing error path auto-skips. Settings live in `SubsonicSettings.kt`, browse UI in `SubsonicLibraryScreen.kt` + `SubsonicLibraryViewModel`.
- "Together" sessions intercept playback control: as a guest, queue taps and skips go through `requestTogetherControl` instead of local crossfade/seek, and crossfade is disabled entirely unless the session is `Idle`.
- BNMV is external (not bundled): `visualizer/bnmv/` → `BngvTeeAudioProcessor` → `VisualizerHub` (shim) → `BnmvIntegrationManager` → 512 log bins 30Hz-16kHz → 768-byte UDP to BNMV 8889 (handshake 8888). `GlyphRenderer`/`FlashlightEngine`/etc are dead code (keep for R8). Presets from `app/src/main/assets/zones.config` (filesDir override first, see `VisualizerConfigLoader`) via `ACTION_SET_PRESET`; root `assets/` is marketing images, not app assets.
- Version in `app/build.gradle.kts` (`versionCode 142`, `versionName 15.0.0`) — release builds and the GitHub release are driven off this.
- "Together" endpoint fetched at runtime (`together/TogetherOnlineEndpoint.kt`) from `raw.githubusercontent.com/shubh72010/Greeny-Goblins/refs/heads/dev/ArchiveTuneKoiverseServer.txt` with a 6 h cache TTL; `JusPlayerKoiverseServer.txt` at repo root is not read.
- i18n: `values/` is English. Fresh install defaults to `en` (`AppLanguageKey` default `"en"`, `MainActivity` forces `Locale("en")`) — don't revert to `SYSTEM_DEFAULT`.

## Code Quality

- Lint `abortOnError=false warningsAsErrors=false`. CI runs only `:app:lintGmsMobileUniversalDebug`. Root `lint.xml` suppresses `UnsafeOptInUsageError` for media3's `UnstableApi`. `app/lint.xml` ignores `MissingTranslation` but sets `MissingQuantity` to **error** with `values-cs`/`values-lt`/`values-sk` path-exceptions — adding a new `<plurals>` entry without translating those three will fail lint.
- detekt minimal (`detekt.yml`: `UnusedPrivateClass`, `UnusedPrivateMember`, `EmptyFunctionBlock`, one more) — not in CI. No instrumented tests. Unit tests are plain JUnit4 + Turbine, no mocks; results in `app/build/test-results/testGmsMobileUniversalDebugUnitTest/`. Current classes: `StreamChunkResolverTest`, `DiscordPresencePolicyTest`, `SubsonicIdTest`, `LyricsFilterTest`, `SongHidesTest`, `DeviceTierTest`, `CheckpointMathTest`, `DspAnalyzerTest`, `FeatureAggregatorTest`, `StreamingAnalysisTest`.
- Deliberate shortcuts may be marked with a `ponytail:` comment naming the ceiling — leave them in place rather than "fixing" them blind.

## CI

- `build.yml` ("Build and Scan") — two jobs. PR job: `assembleGmsMobileUniversalDebug` + `:app:lintGmsMobileUniversalDebug`, filtering the `CXX5202` 32-bit warning; uploads a PR artifact. Release job: validates required secrets (fails on `main` if missing), decodes the release keystore (**no debug fallback**), builds `assembleGmsMobileUniversalRelease` (real key, R8, `--no-configuration-cache`) plus a debug APK.
- `header.yml` (manual `workflow_dispatch`): GPL-3.0 header via `addlicense`.
- `notify_user.yml`: comments on issues referenced by commits. `deploy_artifacts.py` / `notify_telegram.py` support scripts.
- Renovate base `dev`, `group:all`, blocks pre-releases except Compose.

## Instruction Files

- `AGENTS.md` and `CLAUDE.md` are **tracked**, and `.github/copilot-instructions.md` is tracked — even though `.gitignore` lists `/AGENTS.md` and `/.github/copilot-instructions.md`. Ignore rules don't apply to already-tracked paths, so plain `git add` works; no `git add -f` needed.
- `CLAUDE.md` is a shorter sibling of this file. Keep the two consistent when changing commands or architecture.
