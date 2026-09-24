# Dataset verification (2026-09-24)

Checked `laya-music-synthetic-v1.jsonl` against three public references plus
internal statistics. Verdict: **no changes needed.**

## 1. Spotify Audio Features (developer.spotify.com)

| Our usage | Spotify definition | Match |
|---|---|---|
| energy = intensity/activity, paired with bpm + rhythmicity | "fast, loud, noisy" = high; Bach prelude = low | ✅ |
| valence high = EUPHORIC (0.80) | high valence "e.g. happy, cheerful, **euphoric**" — their word | ✅ |
| danceability ↔ tempo + rhythmicity + beat strength | tempo, rhythm stability, beat strength, regularity | ✅ |
| acousticness 1.0 = acoustic (MELANCHOLIC 0.80, EUPHORIC 0.08) | confidence 1.0 = acoustic | ✅ |
| instrumentalness > 0.5 = instrumental (FOCUS 0.88; rest ≤ 0.45) | documented > 0.5 threshold | ✅ |

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
explain all affective variance — which is why our state carries 7 extra
features (brightness, rhythmicity, bass/mids/treble, bpm, key) beyond V/A.

## 3. DEAM (Aljanaki et al. 2017, 1802 songs, static V/A on 1–9 scale)

Our valence maps plausibly: EUPHORIC 0.80 ≈ 7.4/9, DARK 0.20 ≈ 2.6/9.
DEAM notes arousal annotates more reliably than valence — our energy
(arousal proxy) spans 0.25–0.88 with clean separation, so the reliable axis
carries the most signal.

## 4. Internal statistics (measured, seed 42)

- Means recover centroids within 0.01; std ≈ 0.07 as designed.
- Clipping at 0.0/1.0 bounds: 119/12000 ≈ 1% (extreme centroids only). Fine.
- Min inter-centroid distance 0.33 (EUPHORIC–ENERGETIC) vs noise
  displacement σ√d ≈ 0.22: separable but adjacent — realistic, and the
  boundary is covered by `eval-hard.jsonl`.
- BPM ranges overlap across adjacent moods (e.g. CHILL ≤111, ENERGETIC ≥106):
  realistic, not a bug. No absurd values (60–153 overall).
- Cross-feature coherence: DARK = lowest brightness (0.30) + highest bass
  (0.85); EUPHORIC = highest brightness (0.84) + treble (0.82). "Dark" and
  "bright" mean what they should.

## 5. One judgment call, kept as-is

DARK arousal is mid (+0.31), not extreme — it covers dark-ambient to heavy
without committing to either. Human labels will pull it where the library
actually lives. Revisit only if the confusion matrix shows DARK ↔ MELANCHOLIC
or DARK ↔ ENERGETIC errors post-fine-tune.
