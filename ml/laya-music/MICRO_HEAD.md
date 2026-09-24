# Micro-head: the model that actually ships on-device

The fine-tuned teacher (`greeny-goblins/laya-music-v1`, ~840MB) never enters the
APK. It labels data; this tiny student runs on the phone.

## 1. Input (numeric vector, NOT the teacher's JSON)

11 dims, fixed order: `[energy, valence, danceability, acousticness,
instrumentalness, brightness, rhythmicity, bass, mids, treble, bpm_norm]`
where `bpm_norm = (bpm - 60) / 120`. `key` is excluded from v1 (string, needs
its own encoding — candidate for v2). `confidence` is excluded (meta, not
signal). Conversion from a dataset row is one line — keep this exact order
everywhere including the Kotlin port.

## 2. Architecture (v1, deliberately boring)

MLP `11 → 64 (ReLU) → 32 (ReLU) → 3 softmax heads (6 + 4 + 4 = 14 outputs)`.
≈ 6k params ≈ 24KB fp32, ≈ 6KB int8. Trains in seconds on CPU with plain
cross-entropy (Adam). No dependencies, no framework needed at inference.

## 3. Training data (in order)

1. Pre-train: synthetic 1200 (this folder) as numeric vectors.
2. Final: **silver data** — teacher inference over real library tracks, same
   row schema, `"source": "silver-laya-music-v1"`, keep teacher confidence per
   question; down-weight or drop rows below 0.85.
3. Eval: `eval-hard.jsonl` as numeric vectors — target: within 5pp of the
   teacher's accuracy. If the gap is larger, the student is under-capacity
   (widen to 128/64) before blaming the data.

## 4. Shipping (two options, cheapest first)

- **A. Hand-port (recommended v1):** 6k floats + ~50 lines of Kotlin matrix
  math. Zero dependencies, zero native libs, auditable. Weights live in
  `assets/` as a flat float array with the dim order from §1 in the header.
- **B. ONNX:** `torch.onnx.export` + ONNX Runtime Mobile. Justified only if v2
  outgrows hand-porting (embeddings, key encoder).

## 5. App contract (for the Android side, implemented independently)

Room `music_analysis` row stores `mood/ui_mode/motion + confidences +
modelVersion` (`"rule-v0"` now → `"micro-v1"` later; old rows stay valid).
Runtime rule: apply the head's decision only if all three confidences ≥ 0.85,
else rule baseline. The Kotlin `LayaDecider` interface must accept both
implementations without call-site changes.
