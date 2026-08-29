# JusPlayer — AGENTS.md

Kotlin/Jetpack Compose YouTube Music client (`moe.rukamori.archivetune`, `app/src/main/kotlin/moe/rukamori/archivetune/MainActivity.kt`). Fork of ArchiveTune. MVVM + UDF, Hilt/Room/Media3/Ktor/Coil 3.

## Commands — always variant-qualified

3 dims `gms|foss` × `mobile|tv` × `universal|arm64|armeabi|x86|x86_64` = 20 variants. Default debug = `GmsMobileUniversalDebug` (`applicationIdSuffix=.debug`). Unqualified `assembleDebug`/`lintDebug`/`testDebugUnitTest` build all 20 or fail — never use.

```bash
./gradlew assembleGmsMobileUniversalDebug              # single debug APK (fast path)
./gradlew :app:lintGmsMobileUniversalDebug             # lint (only this variant runs in PR CI)
./gradlew :app:testGmsMobileUniversalDebugUnitTest     # all unit tests (2 tests: StreamChunkResolverTest, DiscordPresencePolicyTest)
./gradlew :app:testGmsMobileUniversalDebugUnitTest --tests "*.StreamChunkResolverTest"  # single test
./gradlew clean assembleGmsMobileUniversalRelease      # reproducibility check (release.yml double-builds + SHA256)
```

`CONTRIBUTING.md` is stale (`assembleDebug`, `ktlintCheck`, JDK 17) — ignore. Required JDK 21, no ktlint/detekt in CI.

## Structure

- `:app` is the only Android module. 8 Glance widgets in `archivetune/widget/`. Flavor sources `app/src/{gms,foss,tv,debug}` — Cast (`media3-cast` + `mediarouter`) is `gmsImplementation`-only.
- 5 git submodules: `core` (InnerTube), `lyrics` (7 provider modules: kugou/lrclib/simpmusic/paxsenix/betterlyrics/unison/youlyplus), `moriextractor`, `morideobfuscator` included via `includeIfPresent` in `settings.gradle.kts`; `IconPack` is NOT a Gradle module — it's the SVG source dir consumed by `GenerateIconPackTask`. Clone with `--recurse-submodules`; build succeeds without them. CI uses `submodules: true`.
- In-repo JVM modules: `:lastfm`, `:canvas`, `:shazamkit` (standard `src/main/kotlin`), `:spotifycore` (`kotlin.srcDir("src")`, not `src/main/kotlin`).
- JusPlayer Engine `com.github.shubh72010.JusPlayer-Engine:engine-{api,provider-newpipe,provider-api,model}:v1.6.0` from JitPack `exclusiveContent` — `engine-provider-newpipe` excludes `extractor` to avoid duplicate `TeamNewPipe:NewPipeExtractor:0.26.3`.
- `buildSrc/GenerateIconPackTask` — SVG→PNG via Batik, requires `-Djava.awt.headless=true` (in `gradle.properties`), wired per-variant via `androidComponents.onVariants` in `app/build.gradle.kts`.

## Toolchain

`gradle/libs.versions.toml`: JDK 21, AGP 9.2.1, Kotlin 2.4.0, Gradle 9.6.1, Compose 1.12.0-beta02, `compileSdk=targetSdk=37`, `minSdk=26`. `org.gradle.caching=false`, `ksp.incremental=false`, `android.nonTransitiveRClass=false`, `isCoreLibraryDesugaringEnabled=false` despite desugaring dep.

No Compose BOM — versions forced via `resolutionStrategy.force` in `app/build.gradle.kts`. Room schemas: `app/schemas` (`room.schemaLocation`). Metrics: `-PenableComposeCompilerReports=true` → `build/compose_metrics`.

Inline pins not in catalog (or overriding it): `androidx.browser:1.10.0`, `lifecycle-process:2.11.0`, `compose.adaptive:1.3.0-rc01`, `material-kolor:5.0.0-alpha07`, `org.json:20240303` (catalog has `20250517` — app pins the older one), Glance `1.1.1`, Engine `v1.6.0`.

Opt-ins: `ExperimentalMaterial3Api`/`ExperimentalMaterial3ExpressiveApi` globally via `compilerOptions` (redundant per-file `@OptIn` in ~110 files — don't add/remove). Media3 `UnstableApi` per-file only (suppressed in root `lint.xml`); serialization experimental without opt-in.

## Secrets & Signing

Read from `local.properties` or env (empty = disabled): `LASTFM_API_KEY`/`LASTFM_SECRET`, `TOGETHER_BEARER_TOKEN`, `CANVAS_BEARER_TOKEN`, `EXTRACTOR_BEARER`, `DISCORD_APPLICATION_ID` (default `1165706613961789445`), `NIGHTLY_BUILD_HASH` (7-char SHA, dev only). Release: `app/keystore/release.keystore` + `STORE_PASSWORD`/`KEYSTORE_PASSWORD`/`KEY_ALIAS`/`KEY_PASSWORD` via `ilharp/sign-android-release`; without it release is unsigned. `*.jks`/`*.keystore` gitignored but `app/persistent-debug.keystore` + `Koiverse.jks` (+ `.base64`) tracked (unused) — debug falls back to `~/.android/debug.keystore` (CI generates).

## Architecture — check before UI/arch changes

- Strict UDF: stateless UI → ViewModel (sealed `Loading/Success/Empty/Error`) → UseCase → Repository. Composables use `collectAsStateWithLifecycle()`. Map DB/DTOs to UI models outside composables. See `.github/copilot-instructions.md` (tracked despite gitignore) for blocking PR checklist.
- Playback dual-engine: InnerTube (`YTPlayerUtils`/`WEB_REMIX`) + JusPlayer Engine (`NewPipeProvider.getStream` via `engine/JusPlayerEngineResolver.kt`, cached, fallback to InnerTube). `PlayerStreamClient` (`constants/PreferenceKeys.kt:266`) has 8 values (WEB_REMIX, ARCHIVETUNE_EXTRACTOR, JUSPLAYER_ENGINE + ANDROID_VR/HI_RES_LOSSLESS/IOS/TVHTML5/ANDROID_MUSIC client spoofs). `AudioQuality` (`PreferenceKeys.kt:257`) `HIGHEST` runs `MusicService.resolveMaxQualityDualEngineDataSpec()` concurrently, picks higher bitrate (tie <8 kbps) then codec rank opus 3 > aac 2 > other 1, tie → InnerTube.
- BNMV is external (not bundled): `visualizer/bnmv/` → `BngvTeeAudioProcessor` → `VisualizerHub` (shim) → `BnmvIntegrationManager` → 512 log bins 30Hz-16kHz → 768-byte UDP to BNMV 8889 (handshake 8888). `GlyphRenderer`/`FlashlightEngine`/etc are dead code (keep for R8). Presets from `app/src/main/assets/zones.config` (filesDir override first, see `VisualizerConfigLoader`) via `ACTION_SET_PRESET`; root `assets/` is marketing images, not app assets.
- Version in `app/build.gradle.kts` (`versionCode 141`, `versionName 13.8.0`) — bumping `versionName` triggers `release.yml` tag `v<versionName>` + double `clean assembleGmsMobileUniversalRelease` reproducibility check.
- "Together" endpoint fetched at runtime from `raw.githubusercontent.com/shubh72010/Greeny-Goblins/dev/ArchiveTuneKoiverseServer.txt` (6h cache); `JusPlayerKoiverseServer.txt` at root not read.
- i18n: `values/` is English (1030 strings). Fresh install defaults to `en` (`AppLanguageKey` default `"en"`, `MainActivity:502` forces `Locale("en")`) — don't revert to `SYSTEM_DEFAULT`.

## Code Quality

- Lint `abortOnError=false warningsAsErrors=false`. PR runs only `:app:lintGmsMobileUniversalDebug`. `app/lint.xml` ignores `MissingTranslation`; root `lint.xml` ignores `UnstableApi`.
- detekt minimal (unused private class/member, empty function) — not in CI. No instrumented tests; 2 unit tests only (JUnit4 + Turbine, no mocks).

## CI

- `build_pull_request.yml` (PR): `assembleGmsMobileUniversalDebug` + lint, filters `CXX5202` 32-bit warning.
- `build.yml` (push `main`/`dev`): 7 release variants (matrix) + debug, signs, Telegram notify.
- `release.yml` (push `main` touching `app/build.gradle.kts` or dispatch): checks tag, reproducibility, signs 7 variants, GitHub release with auto changelog.
- `header.yml` (manual): GPL-3.0 header via `addlicense`. Renovate base `dev`, `group:all`, blocks pre-releases except Compose.

## Instruction Files

- `AGENTS.md` gitignored (`/.gitignore` `/AGENTS.md`) — local-only, `git add -f` to commit.
- `.github/copilot-instructions.md` is tracked — enforce UDF/Compose rules before merging UI changes.
