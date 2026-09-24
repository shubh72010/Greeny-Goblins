# laya-music — dataset handoff for Laya fine-tuning

**Context.** [Laya](https://github.com/NandhaKishorM/laya) is a non-autoregressive
System-1 decision engine (typed `choice`/`score`/`noul` answers, one forward pass).
Base checkpoints score near-chance on custom decisions — all capability comes from
fine-tuning. This folder holds everything needed to fine-tune a Laya teacher that
maps **music profiles → UI decisions** for the Greeny-Goblins / JusPlayer app.

**Pipeline (do not skip steps).**

```text
SYNTHETIC (this folder, 1200 rows, source=rule-v0-synthetic)
  → pre-training only. NEVER ground truth.
        │
HUMAN (~500 rows, YOU collect, same schema, source=human)
  → ground truth → fine-tune Laya teacher (Kaggle notebook, §5)
        │
REAL LIBRARY TRACKS → teacher relabels → SILVER DATA (same schema)
  → trains the tiny on-device head (12 floats → 3 enums, ships in APK)
```

The 421M-param teacher never ships on-device. The phone gets the distilled head.

## 1. Files

| File | What |
|---|---|
| `taxonomy-v1.json` | The 3 Laya questions. Frozen — byte-identical at train and inference time |
| `laya-music-synthetic-v1.jsonl` | 1200 bootstrap rows (200/mood). Pre-train only |
| `eval-hard.jsonl` | 60 boundary rows. **Held out — never train on these** |
| `generate_synthetic.py` | Seeded (`42`) generator. Its `CENTROIDS` are rule-v0, mirrored in-app later |
| `validate.py` | Schema + enum + quota checker. Run on every dataset before training |
| `laya-music-human-v1.jsonl` | **You create this.** The 500 human rows (§4) |

## 2. Taxonomy (frozen v1)

`mood` (choice, 6): EUPHORIC (high energy, high valence, bright, driving) ·
ENERGETIC (high energy, mid valence, rhythmic) · CHILL (low-mid energy, warm,
mellow) · MELANCHOLIC (low valence, soft, slow) · DARK (low valence, heavy
bass, tense) · FOCUS (instrumental, steady, unobtrusive)

`ui_mode` (choice, 4): HEAT (high energy + bright) · GLOW (warm, mid energy) ·
MIST (low energy, quiet) · NIGHT (dark, minimal)

`motion` (choice, 4): STILL · GENTLE · MODERATE · STRONG (deliberately `choice`,
not `score` — Laya's `score` primitive is its weakest and has position bias)

Canonical pairings: EUPHORIC→HEAT/STRONG · ENERGETIC→HEAT/MODERATE ·
CHILL→GLOW/GENTLE · MELANCHOLIC→MIST/GENTLE · DARK→NIGHT/MODERATE ·
FOCUS→MIST/STILL. Keep label sets this small: accuracy collapses past ~20 options.

## 3. Row schema (all datasets, no exceptions)

```json
{"videoId": "dQw4w9WgXcQ", "title": "Artist - Title",
 "state": {"energy": 0.86, "valence": 0.72, "danceability": 0.91,
  "acousticness": 0.08, "instrumentalness": 0.15, "brightness": 0.82,
  "rhythmicity": 0.93, "bass": 0.79, "mids": 0.55, "treble": 0.81,
  "bpm": 128, "key": "F# minor", "confidence": 0.88},
 "labels": {"mood": "EUPHORIC", "ui_mode": "HEAT", "motion": "STRONG"},
 "source": "human", "annotator": "name"}
```

Rules: `state` keys in this exact order, floats 0–1 at 2 decimals, `bpm` int
60–180, `key` like `"F# minor"`. Real DSP/YAMNet features don't exist yet — for
human rows, estimate `state` by ear and set `"confidence": 0.0` with
`"source": "human-label-only"`; labels are what matter, features get
re-extracted later. `source` ∈ `human` > `human-fixed-rule` > `rule-v0` >
`deam-weak` > `rule-v0-synthetic` > `rule-v0-boundary`. Train the teacher on
`human*` only.

## 4. Labeling guide (for the 500)

- Judge the **drop/chorus** (50–80% region), not the intro.
- `HEAT` only if you'd turn it up; `NIGHT` only if you'd dim the room.
- Torn between two moods? Pick one, add `"note": "CHILL vs FOCUS?"` — these are
  the most valuable rows (future eval-hard material).
- Torn on motion? Pick the lower one (matches the app's fallback bias).
- Quotas: **500 rows, ≥40 per mood, ≥80 per ui_mode.** Playlist-cover method:
  10–15 tracks you *know* per mood → ~150 rows in ~2 hours.

## 5. Fine-tune (your job after data)

1. Fork `notebooks/laya_finetune_typed_decisions_2xT4_kaggle.ipynb` (free 2×T4).
2. Replace its workflows with `taxonomy-v1.json` (force the fine-tuned or
   `english` checkpoint — never auto-route JSON states).
3. Train (RLCD), **fit calibration temperatures per (type, option-count)**,
   evaluate on `eval-hard.jsonl` + a held-out split.
4. Expect base ~0.35 → tuned ~0.75 accuracy. If ECE > 0.15, refit temps.
5. Push to Hub as `greeny-goblins/laya-music-v1`.

## 6. Deliver back

Hub repo id + accuracy / Brier / ECE + confusion matrix. Inference gate in-app
is `confidence >= 0.85`, else rule baseline — report per-question accuracy at
that threshold too.

## 7. Validate before anything

```bash
python3 validate.py laya-music-human-v1.jsonl --expect-human --min-per-mood 40
python3 validate.py eval-hard.jsonl   # must pass, must never be trained on
```
