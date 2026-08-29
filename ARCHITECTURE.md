# Architecture — JusPlayer

`moe.rukamori.archivetune` · fork of [ArchiveTune](https://github.com/rukamori/ArchiveTune) · `versionCode 141 / versionName 13.8.0` in `app/build.gradle.kts:52-53`

## Modules

```
:app                      Android application (only Android module)
  app/src/main/kotlin/moe/rukamori/archivetune/MainActivity.kt  entry
  app/src/{gms,foss,tv,debug}  flavor sources (Cast = gmsImplementation only)
  app/src/main/assets/zones.config  visualizer presets
  8 Glance widgets in archivetune/widget/
:lastfm, :canvas, :shazamkit          standard JVM modules (src/main/kotlin)
:spotifycore                           kotlin.srcDir("src")
:core (submodule, optional)            InnerTube client
:lyrics/{kugou,lrclib,simpmusic,paxsenix,betterlyrics,unison,youlyplus}
:moriextractor, :morideobfuscator      optional, includeIfPresent in settings.gradle.kts:53-74
IconPack                               NOT a Gradle module — SVG source for buildSrc/GenerateIconPackTask
buildSrc/GenerateIconPackTask          Batik SVG→PNG, -Djava.awt.headless=true, wired per-variant via androidComponents.onVariants
```

JusPlayer Engine `com.github.shubh72010.JusPlayer-Engine:engine-{api,provider-newpipe,provider-api,model}:v1.6.0` from JitPack `exclusiveContent` (settings.gradle.kts:24-39). `engine-provider-newpipe` excludes `extractor` to avoid duplicate with `TeamNewPipe:NewPipeExtractor:0.26.3`.

## Build Variants

3 flavor dimensions `distribution(gms|foss) × device(mobile|tv) × abi(universal|arm64|armeabi|x86|x86_64)` = 20 variants. Default debug `GmsMobileUniversalDebug` (`applicationIdSuffix=.debug`). Always variant-qualified: `assembleGmsMobileUniversalDebug`, `lintGmsMobileUniversalDebug`, `testGmsMobileUniversalDebugUnitTest`.

Toolchain `gradle/libs.versions.toml`: JDK 21, AGP 9.2.1, Kotlin 2.4.0, Gradle 9.6.1, Compose 1.12.0-beta02, compileSdk=targetSdk=37, minSdk=26, Media3 1.10.1, Room 2.8.4. `org.gradle.caching=false`, `ksp.incremental=false`, `nonTransitiveRClass=false`. No Compose BOM — versions forced in `app/build.gradle.kts:435-444`. Room schemas `app/schemas`.

## Layers (strict UDF)

```
UI (Compose, stateless) → ViewModel (sealed Loading/Success/Empty/Error, StateFlow) → UseCase → Repository → Data (Room + InnerTube/JusPlayer Engine)
```

- Composables use `collectAsStateWithLifecycle()`. Map DB/DTO → UI models outside composables. Details in `.github/copilot-instructions.md`.
- DI via Hilt; persistence via Room; networking via Ktor+OkHttp; images via Coil 3.

## Playback (dual-engine)

- **InnerTube**: `YTPlayerUtils` with `WEB_REMIX` client.
- **JusPlayer Engine**: `NewPipeProvider.getStream` via `engine/JusPlayerEngineResolver.kt` (cached, fallback to InnerTube).
- `PlayerStreamClient` (constants/PreferenceKeys.kt:266) — 8 values: `WEB_REMIX`, `ARCHIVETUNE_EXTRACTOR`, `JUSPLAYER_ENGINE` + 5 client spoofs (`ANDROID_VR`, `HI_RES_LOSSLESS`, `IOS`, `TVHTML5`, `ANDROID_MUSIC`).
- `AudioQuality` (PreferenceKeys.kt:257) — `HIGHEST` calls `MusicService.resolveMaxQualityDualEngineDataSpec()` concurrently, picks higher bitrate (tie <8 kbps) then codec rank `opus 3 > aac 2 > other 1`, tie → InnerTube.
- `AudioQuality` / `PlayerStreamClient` surfaced in settings with badge.

## Visualizer (BNMV external)

Not bundled. Path: `visualizer/bnmv/` → `BngvTeeAudioProcessor` (ExoPlayer PCM tee) → `VisualizerHub` (shim) → `BnmvIntegrationManager` → 512 log bins 30Hz–16kHz → 768-byte packed UDP to `BNMV:8889` (handshake `8888`). `GlyphRenderer`/`FlashlightEngine` etc are dead code (kept for R8). Presets `app/src/main/assets/zones.config` (filesDir override via `VisualizerConfigLoader`) sent as `ACTION_SET_PRESET`. Root `assets/` is marketing images.

## Other Subsystems

- **Together**: endpoint fetched at runtime from `raw.githubusercontent.com/shubh72010/Greeny-Goblins/dev/ArchiveTuneKoiverseServer.txt` (6h cache); `JusPlayerKoiverseServer.txt` at root is not read. Ktor CIO + WebSockets server/client.
- **i18n**: `values/` English (1030 strings) is source; fresh install defaults to `en` (`AppLanguageKey` default `"en"`, `MainActivity:502` forces `Locale("en")`).
- **Secrets**: `local.properties` or env: `LASTFM_API_KEY/SECRET`, `TOGETHER_BEARER_TOKEN`, `CANVAS_BEARER_TOKEN`, `EXTRACTOR_BEARER`, `DISCORD_APPLICATION_ID` (default `1165706613961789445`), `NIGHTLY_BUILD_HASH`. Release signing `app/keystore/release.keystore` + `STORE_PASSWORD/KEYSTORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD` via `ilharp/sign-android-release:v2.0.0`.

## CI

- `build_pull_request.yml` — PR: `assembleGmsMobileUniversalDebug` + `lintGmsMobileUniversalDebug` (filters CXX5202).
- `build.yml` — push `main`/`dev`: 7 release variants + debug, Telegram notify.
- `release.yml` — push `main` touching `app/build.gradle.kts` or dispatch: tag `v<versionName>` check, double `clean assembleGmsMobileUniversalRelease` reproducibility (SHA256), sign, GitHub Release with conventional-commit changelog (`mikepenz/release-changelog-builder-action@v6`).
