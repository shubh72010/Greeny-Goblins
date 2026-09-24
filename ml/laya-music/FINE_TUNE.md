# Fine-tuning Laya on music decisions (cell-by-cell)

Notebook: `NandhaKishorM/laya → notebooks/laya_finetune_typed_decisions_2xT4_kaggle.ipynb`
(run on Kaggle, GPU T4 x2, Internet ON, `HF_TOKEN` in Secrets).
Reference: their run = 1200 cases × 5 questions = 6000 decisions, 4 epochs,
~4–6 min. Ours = ~500 cases × 3 questions = ~1500 decisions — smaller, so read
the epoch note in cell 4.

## 0. Prepare data (this folder)

```bash
python3 validate.py laya-music-human-v1.jsonl --expect-human --min-per-mood 40
python3 to_notebook_format.py laya-music-human-v1.jsonl > music_train.json
# optional pre-train pass:
python3 to_notebook_format.py laya-music-synthetic-v1.jsonl > music_pretrain.json
python3 to_notebook_format.py eval-hard.jsonl > music_eval.json   # eval only, NEVER train
```

Upload `music_train.json` (and `music_eval.json`) to Kaggle as a dataset.
`to_notebook_format.py` emits exactly what notebook cell 3 parses per row:
`state` (dict-as-JSON-string), `questions` (= `taxonomy-v1.json` verbatim),
`gold` = per-question `{"label", "probabilities"}`. Hard labels become one-hot;
rows with a `"distributions"` map (all disagreement-log rows should have one,
e.g. `{"mood": {"CHILL": 0.6, "FOCUS": 0.4}}`) become soft targets — that is
what the proper-scoring RLCD reward actually learns from.

## 1. Cell 2 — install: unchanged

## 2. Cell 3 — data loading: REPLACE the dataset lines

Before:

```python
ds_train = load_dataset("LocalLLaMA/typed-decisions", "all", split="train")
```

After:

```python
from datasets import load_dataset
ds_train = load_dataset("json", data_files="/kaggle/input/<your-dataset>/music_train.json", split="train")
```

Everything below that line (`build_training_item`, the loop, `train_items.pt`)
works unchanged: our `gold` feeds its `choice` path
(`target = [probabilities[k] for k in criteria keys]`, normalized, argmax =
label). Two behaviours to know: rows whose option text gets truncated by the
`head_max_len` budget return `None` and are silently dropped — with our short
6/4/4 option labels this will not trigger, but check the printed item count
(≈ rows × 3). `state` tokenizes to ~100 tokens, far under `max_len` 1024.

## 3. Cell 4 (`train_ddp.py`) — one change to consider

Keep everything. Only decision: `EPOCHS = 4` was tuned for 6000 decisions; we
have ~1500. Start with **6–8 epochs** and watch the rolling checkpoint loss
printed per epoch — if loss flatlines early, stop there. `LR_ENCODER = 2.5e-5`,
`LR_HEAD = 1e-4`, micro-batch 8 × grad-accum 4 (= 64 effective) are fine as-is.
Calibration holdout is automatic (10%, seed-fixed, never trained on) and
temperature fitting per question-type is automatic — do not skip it, shipped
checkpoints are over-confident and our app gates on `confidence >= 0.85`.

## 4. Cells 6–7 — eval: point at OUR data, drop the Jev table

Replace the `load_dataset("LocalLLaMA/typed-decisions", ... test)` line with
`music_eval.json` (60 held-out boundary rows) plus a 50-row random holdout from
`music_train.json` that you exclude from training. Reuse the metric code
(choice path only — we have no `noul`/`score` questions). Report: per-question
accuracy, Brier, ECE, and **accuracy at confidence ≥ 0.85** (the app gate).
Expect base ~0.35 → tuned ~0.70+. If ECE > 0.15, refit temperatures (cell 4's
`fit_one_temp`, wider LBFGS iterations) rather than accepting the numbers.

## 5. Cell 8 — push: change the repo id

`NEW_REPO = "greeny-goblins/laya-music-v1"` (not `convaiinnovations/...`).
The cell auto-generates the model card with this run's measured numbers —
verify them before uploading. Output dir must contain `model.safetensors`,
`rl_agent_config.json` (**has the fitted `temperature` — this file is the
calibration, do not lose it**), `tokenizer/`, `encoder/`.

## 6. Deliver back (app team needs all four)

1. Hub repo id (`greeny-goblins/laya-music-v1`)
2. Metrics: per-question accuracy / Brier / ECE + accuracy @ conf ≥ 0.85
3. Confusion matrices per question (6×6, 4×4, 4×4)
4. `benchmark_report.json` (cell 9 output)
