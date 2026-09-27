# Micro-head: the model that actually ships on-device

The fine-tuned teacher (`greeny-goblins/laya-music-v1`, ~840MB) never enters the
APK. It labels data; this tiny student runs on the phone.

## 1. Input (numeric vector, NOT the teacher's JSON)

9 dims, fixed order: `[rms, bass, mids, treble, brightness, spectralFlux,
onsetDensity, rhythmicity, bpm_norm]` where `bpm_norm = (bpm - 60) / 140`.
`chromaKey`/`chromaScale` are excluded from v1 (pitch class is nominal — 0..11
as a number is meaningless — and both are nullable on real rows; candidate for
v2 with a 12-slot one-hot). `confidence` is excluded (meta, not signal). This is
the numeric core of the dataset's `state` (§3 of README), so conversion is a
key lookup plus `bpm_norm`. Keep this exact order everywhere including the
Kotlin port; the bands are shares summing to 1, so two of the three are
redundant — keep all three, the head is 6k params.

State keys come from the app's DSP (`MusicAnalysisEntity`); v1 of this head fed
on `energy`/`valence`/`danceability`/… which the app never produces — retired
in favour of the schema above.

## 2. Architecture (v1, deliberately boring)

MLP `9 → 64 (ReLU) → 32 (ReLU) → 3 softmax heads (6 + 4 + 4 = 14 outputs)`.
≈ 5.6k params ≈ 22KB fp32, ≈ 6KB int8. Trains in seconds on CPU with plain
cross-entropy (Adam). No dependencies, no framework needed at inference.

## 3. Training data (in order)

1. Pre-train: synthetic 1200 (this folder) as numeric vectors.
2. Final: **silver data** — teacher inference over real library tracks, same
   row schema, `"source": "silver-laya-music-v1"`, keep teacher confidence per
   question; down-weight or drop rows below 0.85.
3. Eval: `eval-hard-v2.jsonl` as numeric vectors — target: within 5pp of the
   teacher's accuracy. If the gap is larger, the student is under-capacity
   (widen to 128/64) before blaming the data.

## 4. Shipping (two options, cheapest first)

- **A. Hand-port (recommended v1):** 5.6k floats + ~50 lines of Kotlin matrix
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
