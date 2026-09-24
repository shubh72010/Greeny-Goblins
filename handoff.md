# Handoff — JusPlayer perf pass (ChatGPT plan, no profiling)

## Context
- Source plan: shared ChatGPT thread "Optimization Priorities Comparison".
  Final order (profiling removed): off-thread extraction → Coil sizing → single
  Media3 player → stream cache+expiry → Compose lists → Room/Paging (conditional)
  → memory trim → buffering → Baseline Profiles → 1-track preload → test → ship.
- Locked decisions: do P1→P3→P5, P4 (Paging/FTS/WorkManager) stays out unless a
  demonstrated need appears. No new preload mechanism — queue windowing +
  crossfade prepare + single-item fast-start already cover it. No runtime
  profiler work; Baseline Profiles kept (ship-time, not profiling).
- Follow-up (same session): phone-tier awareness — `isLowRamDevice` alone misses
  3–8GB budget phones (Oppo/Redmi on heavy skins), so tiers are RAM-based now.

## Variant commands (always qualified — unqualified builds all 20 or fail)
```bash
./gradlew assembleGmsMobileUniversalDebug
./gradlew :app:lintGmsMobileUniversalDebug
./gradlew :app:testGmsMobileUniversalDebugUnitTest
./gradlew :app:testGmsMobileUniversalDebugUnitTest --tests "*.DeviceTierTest"
```

## Changes (all uncommitted working-tree; see `git status`)
| File | Change |
|---|---|
| `ui/component/Items.kt` | `ItemThumbnail` URL resize uses measured px capped by tier (was hardcoded 544); dropped dead outer `resize(200,200)` in `SongListItem`; `ArtistListItem/GridItem` use sized `ImageRequest`; `AlbumListItem/GridItem` badges: nested `LaunchedEffect` collectors → single `collectAsState` + `remember(songs, downloads)`; 6 dialog lists gained `key=` |
| `ui/player/MiniPlayerComponents.kt` | 37dp art requests 168px `ImageRequest` (was full-res decode) |
| `ui/player/OfflineArtworkImageRequest.kt` | decode cap now tier-derived 720/864/1080 (was fixed 1080) |
| `ui/player/Player.kt` | progress loop 100ms → 200ms (~5Hz) |
| `ui/player/Thumbnail.kt` | canvas video gated on `isPlayerExpanded` (1-line) |
| `App.kt` | `onTrimMemory` clears Coil memory cache on background pressure (was no-op); Coil memory 12/18/25% + disk 128/256/512MB by tier; user-set disk size still wins |
| `playback/MusicService.kt` | primary buffers from tier (10/20 → 12/24 → 15/30, playback 1s/rebuffer 2s); stream-URL maps capped at 256 via `putBoundedCache` (were unbounded); crossfade control untouched |
| `utils/DeviceTier.kt` (new) | pure `classifyDeviceTier(totalMem, lowRamFlag)` + `TierBudgets`; CONSTRAINED ≤3GB or flag, REDUCED ≤6GB, else NORMAL |
| `utils/DevicePerformance.kt` | added `Context.deviceTier()` (totalMem-based; null-AM falls back to CONSTRAINED) |
| `utils/DeviceTierTest.kt` (new test) | 5 tests: 2GB→constrained, flag→constrained, 4GB→reduced, 12GB→normal, budgets monotonic |
| `app/build.gradle.kts`, `gradle/libs.versions.toml` | `profileinstaller:1.4.1` dep |
| `app/src/main/baselineProfiles/baseline-prof.txt` (new) | hand-written startup rules (App/MainActivity/MusicService); expand via Macrobenchmark later |
| `ui/player/PlayerComponents.kt` | NOT part of the pass — fixed 2 pre-existing `onSurface` refs (no such symbol; intent was `onScrim`) that blocked compilation of uncommitted V10 feature code |

Deliberately not done: Paging/FTS/WorkManager rescan, AndroidX Startup (init already
split critical/deferred), any new preload path, compose BOM / dep changes.

## Verification state
```text
Build:   PASS (assembleGmsMobileUniversalDebug)
Install: PASS (Nothing Phone 2, 12GB → NORMAL tier; fresh-start PSS ~397MB debug)
Tests:   30/31 + 5/5 new DeviceTierTest green
Lint:    PASS (exit 0; 765 issues repo-wide, none on changed lines — all pre-existing)
Device:  PSS flat across 8 flings (+0.2%, heap alloc down); zero OOM/FATAL for our pid
```

## Known pre-existing issues (not mine, not fixed beyond unblocking)
- `StreamChunkResolverTest.returnsNullWhenChunkingCannotProducePositiveLength` fails
  on base; test + resolver untouched by this pass.
- Working tree contains unrelated uncommitted feature work (V10/carousel player,
  `MainActivity`, `PreferenceKeys`, glass/backdrop components, `AUDIT_REPORT.md`,
  `moriextractor`/`morideobfuscator` untracked dirs). Don't mix into this pass's diff.

## Revert
Each file above reverts independently via `git checkout -- <file>`. New files to
delete: `utils/DeviceTier.kt`, `utils/DeviceTierTest.kt`,
`app/src/main/baselineProfiles/`.

## Suggested next steps (in order, only if needed)
1. Real-device run on a 2–4GB phone (install debug APK; note model/RAM + which
   screen stutters: scroll, playback start, background kill) → tune that tier's
   numbers in `DeviceTier.budgets()`.
2. True A/B: build pre-change APK, compare PSS/scroll on the same device.
3. Macrobenchmark-generated Baseline rules to replace/extend the hand-written file.
4. Paging/FTS only if large-library scroll or search proves slow with measurements.
