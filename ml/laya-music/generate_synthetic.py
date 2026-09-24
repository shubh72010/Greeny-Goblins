"""Synthetic bootstrap for laya-music-v1 + micro-model pre-train (stdlib only).

Flow: rule-v0 centroids (below) -> synthetic states -> JSONL.
Human labels (same schema) replace/augment these rows, then the fine-tuned
Laya teacher relabels the real library (silver data) which trains the tiny
on-device head (12-dim vector -> mood/ui_mode/motion).

Single source of truth for rule-v0 thresholds; mirror in Kotlin LayaDecider.
Usage: python3 generate_synthetic.py  (seeded, deterministic)
Outputs: laya-music-synthetic-v1.jsonl, eval-hard.jsonl
"""
import json
import random

SEED = 42
N_PER_MOOD = 200
NOISE = 0.07
OUT_TRAIN = "laya-music-synthetic-v1.jsonl"
OUT_HARD = "eval-hard.jsonl"

# centroid: [energy, valence, danceability, acousticness, instrumentalness,
#            brightness, rhythmicity, bass, mids, treble, bpm]
# ponytail: hand-set centroids, replace with measured cluster means once real DSP lands
CENTROIDS = {
    "EUPHORIC":    ([0.88, 0.80, 0.90, 0.08, 0.12, 0.84, 0.92, 0.80, 0.60, 0.82], 130, "HEAT", "STRONG"),
    "ENERGETIC":   ([0.78, 0.60, 0.82, 0.15, 0.15, 0.68, 0.85, 0.72, 0.58, 0.70], 122, "HEAT", "MODERATE"),
    "CHILL":       ([0.40, 0.62, 0.50, 0.62, 0.45, 0.52, 0.45, 0.45, 0.55, 0.50], 90, "GLOW", "GENTLE"),
    "MELANCHOLIC": ([0.25, 0.22, 0.35, 0.80, 0.45, 0.30, 0.28, 0.35, 0.50, 0.32], 75, "MIST", "GENTLE"),
    "DARK":        ([0.65, 0.20, 0.55, 0.20, 0.35, 0.30, 0.60, 0.85, 0.50, 0.35], 100, "NIGHT", "MODERATE"),
    "FOCUS":       ([0.32, 0.50, 0.25, 0.55, 0.88, 0.48, 0.30, 0.35, 0.55, 0.45], 82, "MIST", "STILL"),
}
KEYS = ["C major", "G major", "D major", "A minor", "E minor", "F# minor"]
ADJACENT = [("EUPHORIC", "ENERGETIC"), ("ENERGETIC", "CHILL"), ("CHILL", "FOCUS"),
            ("CHILL", "MELANCHOLIC"), ("MELANCHOLIC", "DARK"), ("DARK", "ENERGETIC")]


def clamp01(x):
    return round(min(1.0, max(0.0, x)), 2)


def sample_state(feats, bpm, rng):
    vals = [clamp01(v + rng.gauss(0, NOISE)) for v in feats]
    return {
        "energy": vals[0], "valence": vals[1], "danceability": vals[2],
        "acousticness": vals[3], "instrumentalness": vals[4], "brightness": vals[5],
        "rhythmicity": vals[6], "bass": vals[7], "mids": vals[8], "treble": vals[9],
        "bpm": int(max(60, min(180, bpm + rng.gauss(0, 8)))),
        "key": rng.choice(KEYS), "confidence": 1.0,
    }


def main():
    rng = random.Random(SEED)
    rows = []
    for mood, (feats, bpm, ui, motion) in CENTROIDS.items():
        for i in range(N_PER_MOOD):
            rows.append({
                "videoId": f"syn-{mood.lower()}-{i:04d}",
                "state": sample_state(feats, bpm, rng),
                "labels": {"mood": mood, "ui_mode": ui, "motion": motion},
                "source": "rule-v0-synthetic", "annotator": "generator",
            })
    rng.shuffle(rows)
    with open(OUT_TRAIN, "w") as f:
        for r in rows:
            f.write(json.dumps(r) + "\n")

    hard = []
    for a, b in ADJACENT:
        fa, bpm_a, _, _ = CENTROIDS[a]
        fb, bpm_b, _, _ = CENTROIDS[b]
        mid = [(x + y) / 2 for x, y in zip(fa, fb)]
        for i in range(10):
            # ambiguous by construction: label = coin flip, model must earn it
            winner = a if i % 2 == 0 else b
            _, _, ui_w, mo_w = CENTROIDS[winner]
            hard.append({
                "videoId": f"hard-{a.lower()}-{b.lower()}-{i:02d}",
                "state": sample_state(mid, (bpm_a + bpm_b) // 2, rng),
                "labels": {"mood": winner, "ui_mode": ui_w, "motion": mo_w},
                "source": "rule-v0-boundary", "annotator": "generator",
                "note": f"midpoint {a}/{b}, weak label",
            })
    with open(OUT_HARD, "w") as f:
        for r in hard:
            f.write(json.dumps(r) + "\n")

    # validate
    moods = {}
    for r in rows:
        m = r["labels"]["mood"]
        moods[m] = moods.get(m, 0) + 1
        s = r["state"]
        assert len(s) == 13 and all(0.0 <= s[k] <= 1.0 for k in s if k not in ("bpm", "key", "confidence")), s
    assert all(v == N_PER_MOOD for v in moods.values()), moods
    print(f"train={len(rows)} {moods} -> {OUT_TRAIN}")
    print(f"hard={len(hard)} -> {OUT_HARD}")


if __name__ == "__main__":
    main()
