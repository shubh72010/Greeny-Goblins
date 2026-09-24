"""Convert laya-music JSONL rows into the exact format Laya's fine-tune notebook eats.

Notebook cell 3 does, per row:
    state     = json.loads(row["state"])        # dict
    questions = json.loads(row["questions"])    # {"mood": {"type","instructions","criteria"}, ...}
    gold      = json.loads(row["gold"])         # {"mood": {"label","probabilities"}, ...}
and per question build_training_item() reads gold_q["probabilities"]:
    choice -> {option: p} over criteria keys (normalized, argmax = label)
    (our taxonomy is choice-only, so only this path matters)

This script emits one JSON line per input row:
    {"state": "<json-dict>", "questions": "<json-dict>", "gold": "<json-dict>"}
Compatible with: load_dataset("json", data_files=...) then the notebook's loop.

Soft labels: if a row has "distributions": {"mood": {"CHILL": 0.6, "FOCUS": 0.4}},
they are used (normalized) instead of one-hot. This is the point of RLCD proper
scoring rewards — use soft labels for every disagreement-log row.
Otherwise labels become one-hot with label = argmax.

Usage:
  python3 to_notebook_format.py laya-music-human-v1.jsonl > music_train.json
  python3 to_notebook_format.py laya-music-synthetic-v1.jsonl > music_pretrain.json
  python3 to_notebook_format.py eval-hard.jsonl > music_eval.json
"""
import json
import sys

TAXONOMY_FILE = "taxonomy-v1.json"


def to_gold(labels, distributions, taxonomy):
    gold = {}
    for q, qdef in taxonomy.items():
        opts = list(qdef["criteria"].keys())
        dist = (distributions or {}).get(q)
        if dist:
            probs = {o: float(dist.get(o, 0.0)) for o in opts}
        else:
            probs = {o: 1.0 if o == labels[q] else 0.0 for o in opts}
        s = sum(probs.values())
        assert s > 0, f"empty distribution for {q}: {dist}"
        probs = {o: v / s for o, v in probs.items()}
        gold[q] = {"label": max(probs, key=probs.get), "probabilities": probs}
    return gold


def main():
    if len(sys.argv) != 2 or sys.argv[1] in ("-h", "--help"):
        print(__doc__)
        return 0
    with open(TAXONOMY_FILE) as f:
        taxonomy = json.load(f)
    n = 0
    with open(sys.argv[1]) as f:
        for line in f:
            if not line.strip():
                continue
            r = json.loads(line)
            gold = to_gold(r["labels"], r.get("distributions"), taxonomy)
            print(json.dumps({
                "videoId": r.get("videoId", ""),
                "source": r.get("source", ""),
                "state": json.dumps(r["state"]),
                "questions": json.dumps(taxonomy),
                "gold": json.dumps(gold),
            }))
            n += 1
    print(f"converted {n} rows", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
