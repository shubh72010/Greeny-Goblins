# Design Language — JusPlayer

Material 3 on Jetpack Compose (`material3 1.5.0-alpha23`, `material-kolor 5.0.0-alpha07` for dynamic color, `compose 1.12.0-beta02`). No Compose BOM. `ExperimentalMaterial3Api`/`ExperimentalMaterial3ExpressiveApi` globally opted-in via `compilerOptions` in `app/build.gradle.kts:425-426`.

## Principles

- **Material You**: album-art-driven dynamic colors via `material-kolor`; palette `1.0.0` for extraction.
- **Adaptive**: `androidx.compose.material3.adaptive:adaptive:1.3.0-rc01` for phone/tablet/foldable layouts; `app/src/{mobile,tv}` flavor sources; TV uses Compose for TV patterns.
- **Edge-to-edge**: responsive layouts, gesture customization, animation/layout tuning in settings.

## Surface System

- **Player**: 9 player styles × 8 background styles (settings-tunable). Variants in `archivetune/ui/player` and `archivetune/player/`.
- **Browsing**: home/artist/album/library screens with Lazy grids, `key`+`contentType`, shimmer (`compose-shimmer 1.5.0`) + `SquigglySlider 1.0.0` for seek.
- **Icons**: `material-icons-extended 1.7.8` + generated IconPack (`IconPack/svg` → Batik PNG via `GenerateIconPackTask`, `androidsvg 1.4`). Branding `assets/logo.svg`.

## Typography & Motion

- Material 3 type scale; `androidsvg` for vector handling. Compose animation (`animation-graphics`) for transitions. Fast startup, lightweight; avoid allocations in recomposition hot paths (`remember` for objects/lambdas), `derivedStateOf` only for scroll/gesture.

## Components

- `androidx.glance:glance{,-appwidget,-material3}:1.1.1` — 8 home widgets.
- `coil3 3.5.0` (compose/gif/okhttp) for artwork at display size, not full-res.
- `aboutlibraries 15.0.3` for OSS attribution screen; `markwon 4.6.2` (+ strikethrough/tables/tasklist/html/linkify) for markdown.
- `palette` + `material-kolor` for dynamic theming options.

## Navigation & Layout

- `navigation-compose 2.9.8` + `hilt-navigation-compose 1.4.0`; `lifecycle-runtime-compose 2.11.0`. UDF navigation (stateless UI, ViewModel owns state).
- Home uses `reorderable 3.1.0` for drag reorder.

## Accessibility

- Decorative icons `contentDescription = null`; meaningful icons reuse existing `stringResource` (see `.github/copilot-instructions.md` strings rules). No hardcoded user strings in composables.

## Assets & i18n

- Screenshots `fastlane/metadata/android/en-US/images/phoneScreenshots/`; marketing `assets/badge_github.png`. i18n `values/` English (1030 strings) is source; forced `Locale("en")` on fresh install (`MainActivity:502`).
