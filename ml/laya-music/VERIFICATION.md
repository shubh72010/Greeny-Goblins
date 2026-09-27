# Dataset verification (v1 2026-09-24, v2 2026-09-27)

**v2 verdict: the state schema was wrong and is now replaced.** v1's
`energy`/`valence`/`danceability`/`acousticness`/`instrumentalness` columns
looked like Spotify's audio features and were checked against Spotify's
definitions — but nothing in the app computes them, so §1 below was verifying
fiction. v2's `state` is a partition of `MusicAnalysisEntity`: the 11 columns
`DspAnalyzer` + `FeatureAggregator` actually write. §2 (label space) and §3
(label semantics) are unaffected — those are about the taxonomy, not the
features — and §4 is re-measured on v2.

## 1. State schema: app DSP, not Spotify (v2)

| `state` key | Source | Bound |
|---|---|---|
| rms | peak-normalized window RMS | 0–1 |
| bass / mids / treble | band-energy shares (<250 / <2k / <8k Hz), divided by band total | 0–1, sum = 1 |
| brightness | spectral centroid / (rate/2) | 0–1 |
| spectralFlux | positive spectral difference × 40 | 0–1 |
| onsetDensity | onsets per second / 8 | 0–1 |
| rhythmicity | resonating comb-filter score / 2 | 0–1 |
| bpm | Essentia BeatTrackerDegara, octave-resolved against the JVM comb pass | int 60–200 |
| chromaKey / chromaScale | Krumhansl (JVM) or Essentia KeyExtractor, pitch class + mode | 0–11 / major·minor, nullable |

`generate_synthetic.py check_entity()` parses the entity and aborts the build if
these two lists drift, so this table cannot silently rot. Everything the v1 table
credited to Spotify now has to be earned from these 9 numbers: `energy` ←
rms + spectralFlux + onsetDensity, `valence` ← brightness + bass/treble balance,
`acousticness`/`instrumentalness` have **no** DSP counterpart in the app and are
simply not observable at inference time (vocals-instrumental separation needs a
source-separation model — out of scope; FOCUS vs MELANCHOLIC absorbs the loss,
they are the pair the probe confuses most).

## 2. Russell circumplex (Russell 1980; Griffiths et al. 2021 MER setup)

Valence/arousal mapped to −1…+1. All four quadrants + center covered —
the same quadrant-covering strategy MER literature uses:

| Mood | V | A | Russell region |
|---|---|---|---|
| EUPHORIC | +0.60 | +0.75 | excitement ~45° (NE) |
| ENERGETIC | +0.20 | +0.57 | arousal-dominant (N–NE) |
| CHILL | +0.23 | −0.21 | contentment/relaxation ~315° (SE) |
| MELANCHOLIC | −0.56 | −0.50 | depression ~225° (SW; cf. sad 207°) |
| DARK | −0.60 | +0.31 | distress ~135° (NW; cf. tense 93°) |
| FOCUS | −0.01 | −0.36 | neutral-valence low-arousal (S, near sleepy 270°) |

Known literature caveat (Collier 2007; Ilie & Thompson 2006): V/A alone don't
explain all affective variance — which is why `state` carries timbral and
rhythmic detail (brightness, spectralFlux, onsetDensity, rhythmicity,
bass/mids/treble, bpm) rather than collapsing to two scalars.

## 3. DEAM (Aljanaki et al. 2017, 1802 songs, static V/A on 1–9 scale)

Valence maps plausibly: EUPHORIC ≈ 7.4/9, DARK ≈ 2.6/9. DEAM notes arousal
annotates more reliably than valence — and the arousal proxy is exactly where
the app's DSP is strongest (rms, spectralFlux, onsetDensity, rhythmicity), so
the reliable axis is the well-observed one.

## 4. Internal statistics (re-measured on v2, seed 42)

- Means recover the centroids; bands sum to 1.0 on every row; no value clipped
  at a bound.
- Nearest-centroid probe (`check_separable`): **0.709** on the 1200 training
  rows, 0.350 on the 60 boundary rows. The confusion matrix is confined to the
  `ADJACENT` pairs — CHILL↔DARK, MELANCHOLIC↔FOCUS and the
  EUPHORIC↔ENERGETIC / CHILL↔FOCUS neighbours. 0.709 is the separability
  ceiling of 8 DSP scalars, not a tuning failure: those pairs are genuinely
  indistinguishable without more features, and inflating the centroids past it
  would just teach the head a world that doesn't exist.
- `check_separable` asserts train ≥ 0.65 and hard ≤ 0.6, so the corpus can't
  silently become unlearnable noise (over-wide σ) nor a toy (centroids
  stretched past real overlap).
- BPM ranges overlap across adjacent moods (MELANCHOLIC 74±5 vs CHILL 92±5.5),
  overall 61–142 — no absurd values, and the 60–200 clamp is never hit.
- Cross-feature coherence: DARK = highest bass share (0.68) + lowest brightness
  (0.14); EUPHORIC = highest brightness (0.40) + highest treble (0.20) +
  densest onsets (5.0/s). "Dark" and "bright" mean what they should.
- `chromaScale` stays only mood-*biased* (minor 0.21 EUPHORIC → 0.75
  MELANCHOLIC), never deterministic, and the probe deliberately excludes it —
  a head must not shortcut the label off key.

## 5. One judgment call, kept as-is

DARK arousal is mid (+0.31), not extreme — it covers dark-ambient to heavy
without committing to either. Human labels will pull it where the library
actually lives. Revisit only if the confusion matrix shows DARK ↔ MELANCHOLIC
or DARK ↔ ENERGETIC errors post-fine-tune.
