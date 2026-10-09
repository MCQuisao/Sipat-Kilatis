"""
Export the fine-tuned RoBERTa (models/roberta_best/) to ONNX and quantize it to int8 for Android.

1. Export to ONNX with Hugging Face Optimum               -> models/roberta_onnx_fp32/model.onnx
2. Dynamic int8 quantization (ARM64 config, for phones)  -> models/scam_classifier_int8.onnx
3. Tokenizer files for Android                            -> models/tokenizer/
4. Check int8 vs fp32 on val + test: accuracy / F1 difference, probability gap, file sizes
5. Average CPU inference time per message
6. models/test_vectors.json: 10 messages with token ids and expected P(scam) for the Android parity test

Run:  .venv\\Scripts\\python.exe export_onnx.py
"""
import json
import shutil
import sys
import time
from pathlib import Path

import numpy as np
import pandas as pd
from optimum.onnxruntime import ORTModelForSequenceClassification, ORTQuantizer
from optimum.onnxruntime.configuration import AutoQuantizationConfig
from transformers import AutoTokenizer

import reference_inference as ref
from train_baseline import VECTOR_MESSAGES
from train_transformer import THRESHOLD, metrics_dict

sys.stdout.reconfigure(encoding="utf-8")

ROOT = Path(__file__).parent
MODELS = ROOT / "models"
BEST = MODELS / "roberta_best"
FP32_DIR = MODELS / "roberta_onnx_fp32"
INT8_FILE = MODELS / "scam_classifier_int8.onnx"
TOK_DIR = MODELS / "tokenizer"


def mb(path):
    return path.stat().st_size / 1e6


def main():
    # ---- 1. Export to ONNX (fp32)
    print("Exporting to ONNX ...")
    ort_model = ORTModelForSequenceClassification.from_pretrained(str(BEST), export=True)
    ort_model.save_pretrained(str(FP32_DIR))
    tok = AutoTokenizer.from_pretrained(str(BEST))
    fp32_file = FP32_DIR / "model.onnx"

    # ---- 2. Dynamic int8 quantization (weights int8, activations quantized at run time)
    print("Quantizing to int8 ...")
    qdir = MODELS / "_quant_tmp"
    quantizer = ORTQuantizer.from_pretrained(str(FP32_DIR))
    quantizer.quantize(save_dir=str(qdir),
                       quantization_config=AutoQuantizationConfig.arm64(is_static=False, per_channel=False))
    shutil.move(str(next(qdir.glob("*quantized*.onnx"))), INT8_FILE)
    shutil.rmtree(qdir, ignore_errors=True)

    # ---- 3. Tokenizer files the app needs
    if TOK_DIR.exists():
        shutil.rmtree(TOK_DIR)
    tok.save_pretrained(str(TOK_DIR))
    print(f"\nFiles:\n  fp32  {fp32_file.relative_to(ROOT)}  {mb(fp32_file):.0f} MB\n"
          f"  int8  {INT8_FILE.relative_to(ROOT)}  {mb(INT8_FILE):.0f} MB\n"
          f"  tokenizer -> {TOK_DIR.relative_to(ROOT)}: {', '.join(p.name for p in sorted(TOK_DIR.iterdir()))}")

    # ---- 4. int8 vs fp32 on val + test (using the reference inference path, one message at a time)
    tok_ref, sess_int8 = ref.load(INT8_FILE.name)
    _, sess_fp32 = ref.load(str(fp32_file.relative_to(MODELS)))
    df = pd.read_csv(ROOT / "data" / "processed" / "messages.csv")
    df = df[df["split"].isin(["val", "test"])].reset_index(drop=True)
    y = (df["label"] == "scam").astype(int).values

    p32, p8, times = [], [], []
    for text in df["text"]:
        p32.append(ref.scam_probability(tok_ref, sess_fp32, text))
        t = time.perf_counter()
        p8.append(ref.scam_probability(tok_ref, sess_int8, text))   # timing includes normalize + tokenize
        times.append(time.perf_counter() - t)
    p32, p8 = np.array(p32), np.array(p8)

    print("\nint8 vs fp32 (val + test, n={}):".format(len(df)))
    diff = np.abs(p8 - p32)
    print(f"  P(scam) |diff|: mean={diff.mean():.4f}, max={diff.max():.4f}; "
          f"verdict flips: {((p8 >= THRESHOLD) != (p32 >= THRESHOLD)).sum()}")
    results = {}
    for name, split in [("val all", "val"), ("test real_ph", "test")]:
        m = (df["split"] == split).values
        for model, p in [("fp32", p32), ("int8", p8)]:
            r = metrics_dict(y[m], (p[m] >= THRESHOLD).astype(int))
            acc = (r["tp"] + r["tn"]) / r["n"]
            results[f"{split}_{model}"] = {**r, "accuracy": round(acc, 4)}
            print(f"  {name:<13} {model}: accuracy={acc:.3f} F1={r['f1']:.3f} FPR={r['fpr_ham']:.3f}")
        print(f"  {name:<13} accuracy difference int8 - fp32: "
              f"{results[f'{split}_int8']['accuracy'] - results[f'{split}_fp32']['accuracy']:+.4f}")

    # ---- 5. CPU speed
    ms = np.array(times) * 1000
    print(f"\nint8 CPU time per message (this laptop, incl. tokenization): "
          f"mean={ms.mean():.1f} ms, median={np.median(ms):.1f} ms, p95={np.percentile(ms, 95):.1f} ms")
    print("  (phones are slower; expect roughly 3-10x this on a mid-range Android)")

    # ---- 6. Test vectors for the Android parity test
    vectors = []
    for text in VECTOR_MESSAGES:
        norm, ids, _ = ref.tokenize(tok_ref, text)
        vectors.append({"text": text, "normalized": norm, "input_ids": ids[0].tolist(),
                        "scam_probability": round(ref.scam_probability(tok_ref, sess_int8, text), 4)})
    (MODELS / "test_vectors.json").write_text(json.dumps(vectors, ensure_ascii=False, indent=2), encoding="utf-8")

    summary = {"model_file": INT8_FILE.name, "size_mb": round(mb(INT8_FILE), 1),
               "fp32_size_mb": round(mb(fp32_file), 1), "max_length": ref.MAX_LEN,
               "inputs": ["input_ids", "attention_mask"], "output": "logits [batch, 2]",
               "scam_class_index": ref.SCAM_INDEX, "threshold": THRESHOLD,
               "cpu_ms_per_message_laptop": round(float(ms.mean()), 1), "metrics": results}
    (MODELS / "transformer_export.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")

    print("\nTest vectors (int8):")
    for v in vectors:
        print(f"  P={v['scam_probability']:.3f} | {v['text']}")
    print(f"\nSaved {INT8_FILE.name}, tokenizer/, test_vectors.json, transformer_export.json -> {MODELS}")


if __name__ == "__main__":
    main()
