"""Synthetic bootstrap for the Laya fine-tune + micro-head pre-train (stdlib only).

`state` is the Laya teacher's input and the micro-head's feature source, so its
keys are a partition of MusicAnalysisEntity
(app/src/main/kotlin/moe/rukamori/archivetune/db/entities/MusicAnalysisEntity.kt):
every key written here is a column the DSP pipeline actually produces, so the
teacher, the head and real Room rows all read one schema. check_entity() fails
the run the moment the two drift apart (skipped when this folder is checked out
without the app tree, e.g. the ml-only branch).

Flow: rule-v0 centroids (below) -> synthetic states -> JSONL. Human labels (same
schema) augment these rows, then the Laya teacher relabels the real library
(silver data) which trains the tiny on-device head (state -> mood/ui_mode/
motion). Label ontology + criteria text lives in taxonomy-v1.json.

Key name is `state`, not `features`: to_notebook_format.py and the fine-tune
notebook both read row["state"], and the teacher treats it as an opaque state
dict. Single source of truth for rule-v0 thresholds; mirror in Kotlin LayaDecider.
Usage: python3 generate_synthetic.py  (seeded, deterministic)
Outputs: laya-music-synthetic-v2.jsonl, eval-hard-v2.jsonl
"""
import json
import pathlib
import random
import re

SEED = 42
N_PER_MOOD = 200
OUT_TRAIN = "laya-music-synthetic-v2.jsonl"
OUT_HARD = "eval-hard-v2.jsonl"

ROOT = pathlib.Path(__file__).resolve().parents[2]
ENTITY_GLOB = "app/src/main/kotlin/**/MusicAnalysisEntity.kt"

# Model inputs, in MusicAnalysisEntity column order. Everything else the entity
# stores is row metadata, not state: videoId joins, confidence is the sample
# weight FeatureAggregator computes, the rest is bookkeeping.
FEATURES = [
    "rms", "bass", "mids", "treble", "brightness", "spectralFlux",
    "onsetDensity", "rhythmicity", "bpm", "chromaKey", "chromaScale",
]
NON_FEATURE = {
    "videoId", "confidence", "sampleCount", "analysisVersion", "analyzedAt",
    "analysisSource", "analysisError",
}
BOUNDED = [f for f in FEATURES if f not in ("bpm", "chromaKey", "chromaScale")]

# mood: (rms, [bass, mids, treble] shares, brightness, spectralFlux,
#        onsets per second, rhythmicity, bpm, P(minor key))
# ponytail: hand-set centroids in the real feature space, replace with measured
# cluster means once a real-track dump exists (dump Room rows, fit 6 means).
# Invariants held by construction below: bands sum to 1 (DspAnalyzer divides by
# bandTot), onsetDensity = onsets/8, everything else is coerceIn(0, 1)-bounded.
# SIGMA is within-mood spread, set so the corpus stays learnable: check_separable
# asserts a nearest-centroid probe reads >= 0.65 on train and <= 0.6 on eval-hard
# (adjacent moods overlap on purpose, boundary labels are coin flips). ~0.71 is the
# separability ceiling of this feature space — all errors land on the ADJACENT
# pairs, and CHILL/DARK + MELANCHOLIC/FOCUS collide because 8 DSP scalars cannot
# tell them apart. Widening centroids past that would be fiction; the fix for real
# accuracy is more features, not a cleaner synthetic set.
CENTROIDS = {
    "EUPHORIC":    (0.34, [0.45, 0.35, 0.20], 0.40, 0.70, 5.0, 0.70, 128, 0.25, "HEAT", "STRONG"),
    "ENERGETIC":   (0.32, [0.48, 0.33, 0.19], 0.32, 0.62, 4.4, 0.72, 122, 0.35, "HEAT", "MODERATE"),
    "CHILL":       (0.24, [0.55, 0.32, 0.13], 0.22, 0.34, 2.6, 0.45, 92, 0.40, "GLOW", "GENTLE"),
    "MELANCHOLIC": (0.18, [0.50, 0.36, 0.14], 0.15, 0.22, 1.8, 0.32, 74, 0.75, "MIST", "GENTLE"),
    "DARK":        (0.28, [0.68, 0.24, 0.08], 0.14, 0.45, 3.2, 0.58, 100, 0.60, "NIGHT", "MODERATE"),
    "FOCUS":       (0.20, [0.50, 0.34, 0.16], 0.26, 0.18, 1.6, 0.40, 84, 0.45, "MIST", "STILL"),
}
ADJACENT = [("EUPHORIC", "ENERGETIC"), ("ENERGETIC", "CHILL"), ("CHILL", "FOCUS"),
            ("CHILL", "DARK"), ("MELANCHOLIC", "FOCUS"), ("MELANCHOLIC", "DARK")]

# Bounds the DSP actually enforces (DspAnalyzer): MIN_BPM/MAX_BPM, centroid/(rate/2),
# flux*40, onsets/sec/8, resonance/2, 12 pitch classes.
BPM_MIN, BPM_MAX = 60, 200
ONSETS_DIVISOR = 8.0
SIGMA = {"rms": 0.03, "bands": 0.02, "brightness": 0.03, "spectralFlux": 0.045,
         "onsets": 0.35, "rhythmicity": 0.06, "bpm": 5.0}
# Probe inputs: the categorical key is excluded on purpose (it is mood-biased, not
# mood-defining — see sample_state).
PROBE = ["rms", "bass", "mids", "treble", "brightness", "spectralFlux",
         "onsetDensity", "rhythmicity", "bpm"]


def check_entity():
    """The dataset is only correct while it is a partition of the Room columns."""
    found = sorted(ROOT.glob(ENTITY_GLOB))
    if not found:
        print(f"note: {ENTITY_GLOB} absent (ml-only checkout) — drift check skipped")
        return
    cols = set(re.findall(r"val (\w+):", found[0].read_text()))
    assert cols == set(FEATURES) | NON_FEATURE, (
        f"drift: entity-only={cols - set(FEATURES) - NON_FEATURE} "
        f"dataset-only={set(FEATURES) - cols}"
    )


def clamp01(x):
    return round(min(1.0, max(0.0, x)), 3)


def sample_state(rms, bands, brightness, flux, onsets, rhythm, bpm, minor_p, rng):
    noisy = [max(1e-3, b + rng.gauss(0, SIGMA["bands"])) for b in bands]
    total = sum(noisy)
    return {
        "rms": clamp01(rms + rng.gauss(0, SIGMA["rms"])),
        "bass": round(noisy[0] / total, 3),
        "mids": round(noisy[1] / total, 3),
        "treble": round(noisy[2] / total, 3),
        "brightness": clamp01(brightness + rng.gauss(0, SIGMA["brightness"])),
        "spectralFlux": clamp01(flux + rng.gauss(0, SIGMA["spectralFlux"])),
        "onsetDensity": clamp01((onsets + rng.gauss(0, SIGMA["onsets"])) / ONSETS_DIVISOR),
        "rhythmicity": clamp01(rhythm + rng.gauss(0, SIGMA["rhythmicity"])),
        "bpm": int(max(BPM_MIN, min(BPM_MAX, bpm + rng.gauss(0, SIGMA["bpm"])))),
        # Key stays weakly informative on purpose: scale is mood-biased but never
        # deterministic, so the head cannot shortcut the label off chromaScale.
        # Runtime leaves both null when the JVM estimator wins; the trainer
        # imputes, the bootstrap has no missing-data case to model.
        "chromaKey": rng.randrange(12),
        "chromaScale": "minor" if rng.random() < minor_p else "major",
    }


def validate(state):
    assert list(state) == FEATURES, list(state)
    for f in BOUNDED:
        assert 0.0 <= state[f] <= 1.0, (f, state[f])
    assert BPM_MIN <= state["bpm"] <= BPM_MAX, state["bpm"]
    assert 0 <= state["chromaKey"] <= 11, state["chromaKey"]
    assert state["chromaScale"] in ("major", "minor"), state["chromaScale"]
    bands = state["bass"] + state["mids"] + state["treble"]
    assert abs(bands - 1.0) < 0.01, bands


def row(video_id, state, mood, ui, motion, source, confidence):
    validate(state)
    return {
        "videoId": video_id,
        "state": state,
        "labels": {"mood": mood, "ui_mode": ui, "motion": motion},
        # FeatureAggregator's overallConfidence: sample weight for the trainer.
        "confidence": confidence,
        "source": source,
        "annotator": "generator",
    }


def midpoint(a, b):
    (rms_a, bands_a, br_a, fx_a, on_a, rh_a, bpm_a, _, _, _), \
    (rms_b, bands_b, br_b, fx_b, on_b, rh_b, bpm_b, _, _, _) = a, b
    return ((rms_a + rms_b) / 2, [(x + y) / 2 for x, y in zip(bands_a, bands_b)],
            (br_a + br_b) / 2, (fx_a + fx_b) / 2, (on_a + on_b) / 2,
            (rh_a + rh_b) / 2, (bpm_a + bpm_b) // 2, (a[7] + b[7]) / 2, None, None)


def check_separable(rows, hard):
    """Nearest-centroid probe: the corpus must be learnable, and its hard split
    must stay genuinely ambiguous. Catches over-wide SIGMA (label noise) and
    over-tight centroids (a toy that teaches nothing about real overlap)."""
    means = {}
    for mood in CENTROIDS:
        vecs = [[r["state"][f] for f in PROBE] for r in rows if r["labels"]["mood"] == mood]
        means[mood] = [sum(v[i] for v in vecs) / len(vecs) for i in range(len(PROBE))]

    def acc(sample):
        hits = 0
        for r in sample:
            x = [r["state"][f] for f in PROBE]
            best = min(means, key=lambda m: sum((a - b) ** 2 for a, b in zip(means[m], x)))
            hits += best == r["labels"]["mood"]
        return hits / len(sample)

    train_acc, hard_acc = acc(rows), acc(hard)
    assert train_acc >= 0.65, f"corpus not learnable: train {train_acc:.3f}"
    assert hard_acc <= 0.6, f"boundary split too easy: hard {hard_acc:.3f}"
    return train_acc, hard_acc


def main():
    check_entity()
    rng = random.Random(SEED)
    rows = []
    for mood, (rms, bands, br, fx, on, rh, bpm, minor_p, ui, motion) in CENTROIDS.items():
        for i in range(N_PER_MOOD):
            state = sample_state(rms, bands, br, fx, on, rh, bpm, minor_p, rng)
            rows.append(row(f"syn-{mood.lower()}-{i:04d}", state, mood, ui, motion,
                            "rule-v0-synthetic", 1.0))
    rng.shuffle(rows)
    with open(OUT_TRAIN, "w") as f:
        for r in rows:
            f.write(json.dumps(r) + "\n")

    hard = []
    for a, b in ADJACENT:
        mid = midpoint(CENTROIDS[a], CENTROIDS[b])
        for i in range(10):
            # ambiguous by construction: label = coin flip, model must earn it
            winner = a if i % 2 == 0 else b
            state = sample_state(*mid[:8], rng)
            hard.append(row(f"hard-{a.lower()}-{b.lower()}-{i:02d}", state, winner,
                            CENTROIDS[winner][8], CENTROIDS[winner][9],
                            "rule-v0-boundary", 0.5))
            hard[-1]["note"] = f"midpoint {a}/{b}, weak label"
    with open(OUT_HARD, "w") as f:
        for r in hard:
            f.write(json.dumps(r) + "\n")

    moods = {}
    for r in rows:
        moods[r["labels"]["mood"]] = moods.get(r["labels"]["mood"], 0) + 1
    assert all(v == N_PER_MOOD for v in moods.values()), moods
    train_acc, hard_acc = check_separable(rows, hard)
    print(f"train={len(rows)} {moods} -> {OUT_TRAIN}")
    print(f"hard={len(hard)} -> {OUT_HARD}")
    print(f"probe: train={train_acc:.3f} hard={hard_acc:.3f}")


if __name__ == "__main__":
    main()
