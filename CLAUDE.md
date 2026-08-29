# CLAUDE.md — JusPlayer

Kotlin/Jetpack Compose YouTube Music client `moe.rukamori.archivetune` (`app/src/main/kotlin/moe/rukamori/archivetune/MainActivity.kt`). Fork of ArchiveTune. MVVM+UDF, Hilt/Room/Media3/Ktor/Coil 3.

## Commands (always variant-qualified)

20 variants = `gms|foss × mobile|tv × universal|arm64|armeabi|x86|x86_64`. Default debug `GmsMobileUniversalDebug` (`.debug` suffix). Unqualified `assembleDebug` builds all 20 and is slow — never use.

```bash
./gradlew assembleGmsMobileUniversalDebug
./gradlew :app:lintGmsMobileUniversalDebug
./gradlew :app:testGmsMobileUniversalDebugUnitTest
./gradlew :app:testGmsMobileUniversalDebugUnitTest --tests "*.StreamChunkResolverTest"
./gradlew clean assembleGmsMobileUniversalRelease   # reproducibility double-build
```

`CONTRIBUTING.md` is stale (JDK 17 / ktlintCheck) — ignore. Required JDK 21, no ktlint/detekt in CI. Toolchain in `gradle/libs.versions.toml`: AGP 9.2.1, Kotlin 2.4.0, Gradle 9.6.1, Compose 1.12.0-beta02, compileSdk/targetSdk 37, minSdk 26.

## Structure

- `:app` only Android module; 8 Glance widgets in `archivetune/widget/`; flavor sources `app/src/{gms,foss,tv,debug}`; Cast (`media3-cast`+`mediarouter`) is `gmsImplementation`-only.
- Optional submodules via `includeIfPresent` in `settings.gradle.kts`: `core`, `lyrics/{kugou,lrclib,simpmusic,paxsenix,betterlyrics,unison,youlyplus}`, `moriextractor`, `morideobfuscator`. `IconPack` is SVG source, not a module. Clone with `--recurse-submodules`.
- In-repo JVM: `:lastfm`, `:canvas`, `:shazamkit` (`src/main/kotlin`), `:spotifycore` (`kotlin.srcDir("src")`).
- Engine `com.github.shubh72010.JusPlayer-Engine:engine-{api,provider-newpipe,provider-api,model}:v1.6.0` from JitPack `exclusiveContent`; `engine-provider-newpipe` excludes `extractor`.
- `buildSrc/GenerateIconPackTask` SVG→PNG via Batik, needs `-Djava.awt.headless=true`, wired per-variant via `androidComponents.onVariants`.

## Architecture — must enforce

- Strict UDF: stateless UI → ViewModel (sealed `Loading/Success/Empty/Error`) → UseCase → Repository. `collectAsStateWithLifecycle()` in composables. Map DB/DTO→UI outside composables. See `.github/copilot-instructions.md` for blocking PR checklist.
- Playback dual-engine: InnerTube (`YTPlayerUtils/WEB_REMIX`) + JusPlayer Engine (`NewPipeProvider.getStream` via `engine/JusPlayerEngineResolver.kt`, fallback to InnerTube). `PlayerStreamClient` (PreferenceKeys.kt:266) 8 values; `AudioQuality.HIGHEST` runs `MusicService.resolveMaxQualityDualEngineDataSpec()` concurrently (higher bitrate, tie <8kbps → opus> aac, tie→InnerTube).
- BNMV external: `BngvTeeAudioProcessor` → `VisualizerHub` → `BnmvIntegrationManager` → 512 bins 30Hz–16kHz → 768B UDP 8889 (handshake 8888). `GlyphRenderer` etc dead code.
- Version in `app/build.gradle.kts` (`versionCode 141`, `versionName 13.8.0`) — bump triggers `release.yml` tag `v<versionName>` + reproducibility check.

## Conventions

- `ExperimentalMaterial3Api`/`ExperimentalMaterial3ExpressiveApi` globally via `compilerOptions` — don't add/remove per-file `@OptIn` (~110 files). `UnstableApi` per-file only (suppressed in root `lint.xml`); `app/lint.xml` suppresses `MissingTranslation`.
- No Compose BOM — `resolutionStrategy.force` pins Compose runtime. Desugaring dep present but `isCoreLibraryDesugaringEnabled=false`.
- Inline pins not in catalog: `androidx.browser:1.10.0`, `lifecycle-process:2.11.0`, `compose.adaptive:1.3.0-rc01`, `material-kolor:5.0.0-alpha07`, `org.json:20240303`, Glance 1.1.1.
- `local.properties`/env secrets: `LASTFM_API_KEY/SECRET`, `TOGETHER_BEARER_TOKEN`, `CANVAS_BEARER_TOKEN`, `EXTRACTOR_BEARER`, `DISCORD_APPLICATION_ID` (1165706613961789445), `NIGHTLY_BUILD_HASH`.

## CI

`build_pull_request.yml` (PR): single debug APK+lint. `build.yml` (main/dev): 7 release variants + debug, Telegram. `release.yml` (main touch `app/build.gradle.kts`): version check, reproducibility double-build, 7-variant signing, changelog, GitHub Release. Renovate base `dev`, group:all.
