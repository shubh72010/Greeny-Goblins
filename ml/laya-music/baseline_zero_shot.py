"""Zero-shot baseline: stock Laya (no fine-tune) on our taxonomy.

Proves the state->questions->answers contract works and records the numbers
fine-tuning must beat (expect per-question accuracy ~0.30-0.45, i.e. near chance).

Requires: pip install "laya" torch -- plus first-run download of
convaiinnovations/laya (~840MB) from Hugging Face. CPU ~0.2-0.5s/row.

Usage:
  python3 baseline_zero_shot.py [file.jsonl] [--n 20]
"""
import json
import sys
from collections import Counter

TAXONOMY_FILE = "taxonomy-v1.json"


def main():
    args = sys.argv[1:]
    path = args[0] if args and not args[0].startswith("--") else "laya-music-synthetic-v1.jsonl"
    n = int(args[args.index("--n") + 1]) if "--n" in args else 20

    try:
        import laya
    except ImportError:
        sys.exit("pip install laya torch, then rerun")

    with open(TAXONOMY_FILE) as f:
        taxonomy = json.load(f)
    # English checkpoint directly: our states are JSON, not language text,
    # so never use auto-routing (Router) here.
    agent = laya.load("convaiinnovations/laya")

    rows = []
    with open(path) as f:
        for line in f:
            if line.strip():
                rows.append(json.loads(line))
            if len(rows) >= n:
                break

    correct, total, conf_sum = Counter(), Counter(), Counter()
    for r in rows:
        res = agent.predict(r["state"], taxonomy)
        for q in taxonomy:
            a = res["answers"][q]
            total[q] += 1
            correct[q] += a["choice"] == r["labels"][q]
            conf_sum[q] += a["confidence"]
    print(f"rows={len(rows)} file={path}")
    for q in taxonomy:
        print(f"  {q:8s} acc={correct[q]/total[q]:.3f} mean_conf={conf_sum[q]/total[q]:.3f}")
    print("If acc is near chance, the contract works and fine-tuning has room. Good.")


if __name__ == "__main__":
    main()
