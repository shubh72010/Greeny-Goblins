# JusPlayer — AGENTS.md

Kotlin/Jetpack Compose YouTube Music client (`moe.rukamori.archivetune`). Fork of [ArchiveTune](https://github.com/rukamori/ArchiveTune). MVVM + UDF, Hilt/Room/Media3/Ktor/Coil 3.

## Structure

- `:app` — only Android module. Entrypoint `app/src/main/kotlin/moe/rukamori/archivetune/MainActivity.kt`. All UI/ViewModels/UseCases/Repos + 8 Glance widgets in `archivetune/widget/`.
- **Submodules (5, optional):** `core` (InnerTube), `lyrics` (7 providers: kugou/lrclib/simpmusic/paxsenix/betterlyrics/unison/youlyplus), `moriextractor`, `morideobfuscator`, `IconPack`. Clone with `--recurse-submodules`; CI uses `submodules: true`. Missing tolerated via `includeIfPresent` in `settings.gradle.kts` — build still succeeds.
- **In-repo JVM modules:** `:lastfm`, `:canvas`, `:shazamkit`, `:spotifycore` (`spotifycore` uses `kotlin.srcDir("src")`, not `src/main/kotlin`).
- `buildSrc/GenerateIconPackTask` — SVG→PNG via Batik, requires `-Djava.awt.headless=true` (set in `gradle.properties`), wired per-variant in `app/build.gradle.kts` via `androidComponents.onVariants`.
- JusPlayer Engine `com.github.shubh72010.JusPlayer-Engine:engine-{api,provider-newpipe,provider-api,model}:v1.6.0` from JitPack (`exclusiveContent` whitelist in `settings.gradle.kts`). `engine-provider-newpipe` excludes `extractor` to avoid duplicate `org.schabi.newpipe.extractor` with `TeamNewPipe:NewPipeExtractor:0.26.3`.

## Build — variant-qualified tasks only

3 flavor dimensions `gms|foss` × `mobile|tv` × `universal|arm64|armeabi|x86|x86_64`. Default `GmsMobileUniversalDebug` (`applicationIdSuffix=.debug`, flavor sources `app/src/{gms,foss,tv,debug}`, Cast is `gmsImplementation`-only).

```bash
./gradlew assembleGmsMobileUniversalDebug              # single debug APK — fast, preferred
./gradlew :app:lintGmsMobileUniversalDebug             # lint
./gradlew :app:testGmsMobileUniversalDebugUnitTest     # unit tests
./gradlew clean assembleGmsMobileUniversalRelease      # reproducibility check (release.yml does double-build + SHA256)
```

`assembleDebug`/`lintDebug`/`testDebugUnitTest` do not exist or build all 20 variants (slow). `CONTRIBUTING.md` commands (`assembleDebug`, `ktlintCheck`, `lintDebug`, JDK 17) are stale — there is no ktlint/detekt in CI; required JDK is **21**.

**Toolchain** (`gradle/libs.versions.toml`): JDK 21, AGP 9.2.1, Kotlin 2.4.0, Gradle 9.6.1, Compose 1.12.0-beta02, `compileSdk=targetSdk=37`, `minSdk=26`. `org.gradle.caching=false`, `ksp.incremental=false`, `android.nonTransitiveRClass=false`, `isCoreLibraryDesugaringEnabled=false` despite `coreLibraryDesugaring` dep. No Compose BOM — versions forced via `resolutionStrategy.force` in `app/build.gradle.kts`. Compose metrics: `-PenableComposeCompilerReports=true`. Room schemas: `app/schemas` (KSP arg `room.schemaLocation`).

**Inline-pinned deps not in catalog:** `androidx.browser:1.10.0`, `lifecycle-process:2.11.0`, `compose.adaptive:1.3.0-rc01`, `material-kolor:5.0.0-alpha07`, `org.json:20240303`, Glance `1.1.1`, Engine `v1.6.0`.

**Opt-ins:** `ExperimentalMaterial3Api`/`ExperimentalMaterial3ExpressiveApi` globally via `compilerOptions` (redundant per-file `@OptIn` in ~90 files — don't add/remove). Media3 `UnstableApi` per-file only (suppressed in root `lint.xml`). Serialization experimental used without per-file opt-in.

## Secrets & Signing

Read from `local.properties` or env (empty = feature disabled): `LASTFM_API_KEY`/`LASTFM_SECRET`, `TOGETHER_BEARER_TOKEN`, `CANVAS_BEARER_TOKEN`, `EXTRACTOR_BEARER`, `DISCORD_APPLICATION_ID` (default `1165706613961789445`), `NIGHTLY_BUILD_HASH` (7-char SHA, dev only). Release signing: `app/keystore/release.keystore` + `STORE_PASSWORD`/`KEYSTORE_PASSWORD` + `KEY_ALIAS` + `KEY_PASSWORD` via `ilharp/sign-android-release`; without it release builds unsigned. `*.jks`/`*.keystore` gitignored but `app/persistent-debug.keystore` + `Koiverse.jks` are tracked (unused) — debug falls back to `~/.android/debug.keystore` (CI generates).

## Code Quality & Testing

- Lint `abortOnError=false warningsAsErrors=false`. PR runs only `:app:lintGmsMobileUniversalDebug`. `app/lint.xml` ignores `MissingTranslation`; root `lint.xml` ignores Media3 `UnstableApi`. Edit both if changing suppressions.
- detekt minimal (unused private class/member, empty function) — not in CI, no ktlint.
- 2 unit tests in `app/src/test`: `StreamChunkResolverTest`, `DiscordPresencePolicyTest` (JUnit4 + Turbine, no mocks). No instrumented tests.

## CI (`.github/workflows/`)

- `build_pull_request.yml` (PR): `assembleGmsMobileUniversalDebug` + lint, filters benign `CXX5202` 32-bit warning.
- `build.yml` (push `main`/`dev`): 7 release variants (matrix) + debug APK, signs, Telegram notify.
- `release.yml` (push `main` touching `app/build.gradle.kts` or dispatch): checks `v<versionName>` tag, double `clean assembleGmsMobileUniversalRelease` for reproducibility, signs 7 variants, creates GitHub release with conventional-commit changelog.
- `header.yml` (manual): GPL-3.0 header via `addlicense`. Renovate base `dev`, single group, blocks pre-releases except Compose.

## Architecture

- Strict UDF: stateless UI → ViewModel (sealed `Loading/Success/Empty/Error`) → UseCase → Repository. Composables use `collectAsStateWithLifecycle()`. Map DB/DTOs to UI models outside composables (see `.github/copilot-instructions.md` — enforce before UI/arch changes).
- Playback dual-engine: InnerTube (`YTPlayerUtils`/`WEB_REMIX`) + JusPlayer Engine (`NewPipeProvider.getStream` via `engine/JusPlayerEngineResolver.kt:36`, cached, fallback to InnerTube). `PlayerStreamClient` enum exposes 3 in `PlayerSettings.kt:243` (`WEB_REMIX`, `ARCHIVETUNE_EXTRACTOR`, `JUSPLAYER_ENGINE`); `AudioQuality` (`AUTO`/`HIGH`/`HIGHEST`/`LOW`, `PreferenceKeys.kt:257`) — `HIGHEST` ("Max") runs `MusicService.resolveMaxQualityDualEngineDataSpec()` concurrently comparing bitrate (tie <8 kbps) then codec rank (opus 3 > aac 2 > other 1), tie → InnerTube.
- BNMV external integration (not bundled): `visualizer/bnmv/BnmvController` + `BnmvUdpStreamer` + `BnmvIntegrationManager`. Control via broadcasts `com.better.nothing.music.vizualizer.*` (see `BnmvConstants.kt`), manifest `<queries><package android:name="com.better.nothing.music.vizualizer"/></queries>`. Audio path: `BngvTeeAudioProcessor` (ExoPlayer PCM tee, 16-bit mono hop) → `VisualizerHub.onPcm` → `BnmvIntegrationManager` → `AudioProcessor` (512 log bins 30Hz-16kHz, 0..4095) → 768-byte packed UDP (2×12-bit→3 bytes) to BNMV IP:8889 at 60fps after `BNMV_DISCOVER` handshake on 8888. `VisualizerHub` is now a shim delegating to `BnmvIntegrationManager`; bundled `GlyphRenderer`/`GlyphVisualizerManager`/`FlashlightEngine`/`ContinuousHapticEngine` are dead code (kept for R8 tree-shaking, `app/libs/glyph-matrix-sdk-2_0.aar` unused). Preset keys (`np1`, `np2` etc) from `assets/zones.config` are sent via `ACTION_SET_PRESET`; brightness/gamma/threshold owned by external app.
- Version in `app/build.gradle.kts` (`versionCode 141`, `versionName 13.8.0`) — bumping `versionName` triggers release.
- "Together" endpoint fetched at runtime from `raw.githubusercontent.com/shubh72010/Greeny-Goblins/dev/ArchiveTuneKoiverseServer.txt` (cached 6h); root `JusPlayerKoiverseServer.txt` not read.
- i18n: `values/` is English (670 strings). Fresh installs default to `en` (`AppLanguageKey` default `"en"`, `MainActivity:502` forces `Locale("en")`). Do not revert to `SYSTEM_DEFAULT`/`Locale.getDefault()`.

## Instruction Files

- `AGENTS.md` is gitignored (`/.gitignore` `/AGENTS.md`) — local-only, `git add -f` to commit.
- `.github/copilot-instructions.md` is tracked despite gitignore — strict UDF/Compose PR checklist.
