"""Validate a laya-music JSONL dataset against taxonomy-v1.json (stdlib only).

Usage:
  python3 validate.py <file.jsonl> [--expect-human] [--min-per-mood N] [--min-per-ui N]
Exit 0 = schema + enums clean (and quotas met, if requested). Exit 1 = violations.
"""
import json
import sys
from collections import Counter

STATE_KEYS = ["energy", "valence", "danceability", "acousticness",
              "instrumentalness", "brightness", "rhythmicity",
              "bass", "mids", "treble", "bpm", "key", "confidence"]
FLOAT_KEYS = [k for k in STATE_KEYS if k not in ("bpm", "key", "confidence")]
SOURCES = {"human", "human-fixed-rule", "human-label-only", "rule-v0",
           "deam-weak", "rule-v0-synthetic", "rule-v0-boundary"}


def main():
    args = sys.argv[1:]
    if not args or "-h" in args or "--help" in args:
        print(__doc__)
        return 0
    path = args[0]
    expect_human = "--expect-human" in args
    min_mood = int(args[args.index("--min-per-mood") + 1]) if "--min-per-mood" in args else 0
    min_ui = int(args[args.index("--min-per-ui") + 1]) if "--min-per-ui" in args else 0

    with open("taxonomy-v1.json") as f:
        tax = json.load(f)
    enums = {q: set(tax[q]["criteria"]) for q in ("mood", "ui_mode", "motion")}

    errors, moods, uis, seen = [], Counter(), Counter(), set()
    n = 0
    with open(path) as f:
        for ln, line in enumerate(f, 1):
            if not line.strip():
                continue
            n += 1
            try:
                r = json.loads(line)
            except json.JSONDecodeError as e:
                errors.append(f"L{ln}: bad JSON ({e})")
                continue
            if r.get("videoId") in seen:
                errors.append(f"L{ln}: duplicate videoId {r.get('videoId')}")
            seen.add(r.get("videoId"))
            s = r.get("state")
            if not isinstance(s, dict) or list(s) != STATE_KEYS:
                errors.append(f"L{ln}: state keys must be exactly {STATE_KEYS}")
                continue
            for k in FLOAT_KEYS + ["confidence"]:
                if not isinstance(s[k], (int, float)) or not 0.0 <= s[k] <= 1.0:
                    errors.append(f"L{ln}: state.{k}={s[k]!r} not in 0-1")
            if not isinstance(s["bpm"], int) or not 60 <= s["bpm"] <= 180:
                errors.append(f"L{ln}: bpm={s['bpm']!r} not int 60-180")
            if not isinstance(s["key"], str) or not s["key"]:
                errors.append(f"L{ln}: key must be non-empty string")
            lab = r.get("labels", {})
            for q, allowed in enums.items():
                if lab.get(q) not in allowed:
                    errors.append(f"L{ln}: labels.{q}={lab.get(q)!r} not in {sorted(allowed)}")
            if r.get("source") not in SOURCES:
                errors.append(f"L{ln}: source={r.get('source')!r} unknown")
            elif expect_human and not str(r["source"]).startswith("human"):
                errors.append(f"L{ln}: expected human source, got {r['source']}")
            moods[lab.get("mood")] += 1
            uis[lab.get("ui_mode")] += 1

    for m, c in sorted(moods.items()):
        if min_mood and c < min_mood:
            errors.append(f"quota: mood {m} has {c} < {min_mood}")
    for u, c in sorted(uis.items()):
        if min_ui and c < min_ui:
            errors.append(f"quota: ui_mode {u} has {c} < {min_ui}")

    print(f"rows={n} moods={dict(sorted(moods.items()))} ui={dict(sorted(uis.items()))}")
    if errors:
        print(f"FAIL ({len(errors)}):")
        for e in errors[:20]:
            print(f"  {e}")
        return 1
    print("OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
