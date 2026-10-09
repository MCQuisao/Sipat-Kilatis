"""
Fine-tune jcblaise/roberta-tagalog-base for scam vs ham.

Reads  data/processed/messages.csv  (from prepare_data.py; splits train / val / test)
Input text goes through the same normalize() as the baseline (text_normalize.py), so Android has
one preprocessing step for both models.

- max_length 128, 3 epochs, lr 2e-5, batch 16
- weighted loss: class weights (balanced) x per-row weight (real_ph = 2x)
- evaluates on val each epoch, keeps the best checkpoint by F1
- GPU if available (fp16), otherwise CPU

Saves  models/roberta_best/  (model + tokenizer) and models/transformer_metrics.json

Run:  .venv\\Scripts\\python.exe train_transformer.py
"""
import json
import re
import sys
from pathlib import Path

import numpy as np
import pandas as pd
import torch
from datasets import Dataset
from sklearn.metrics import confusion_matrix, precision_recall_fscore_support
from transformers import (AutoModelForSequenceClassification, AutoTokenizer, DataCollatorWithPadding, Trainer,
                          TrainingArguments, set_seed)

from text_normalize import normalize

sys.stdout.reconfigure(encoding="utf-8")

ROOT = Path(__file__).parent
DATA = ROOT / "data" / "processed" / "messages.csv"
MODELS = ROOT / "models"
OUT_DIR = MODELS / "roberta_best"
BASE_MODEL = "jcblaise/roberta-tagalog-base"
MAX_LEN = 128
EPOCHS = 3
LR = 2e-5
BATCH = 16
SEED = 42
THRESHOLD = 0.5
LABELS = {0: "ham", 1: "scam"}   # index 1 = scam class; Android reads softmax(logits)[1]

TAGALOG = {"ang", "ng", "mga", "po", "opo", "naman", "lang", "lng", "kasi", "ito", "mo", "ko", "sa", "ka",
           "kayo", "natin", "hindi", "wala", "para", "salamat", "nga", "rin", "yung", "ung", "nyo", "niyo",
           "ako", "siya", "sya", "sila", "kami", "tayo", "nang", "nmn", "talaga", "baka", "dito", "ngayon",
           "kung", "pag", "na", "pa", "ba", "din", "yan", "yun", "dun", "jan", "noh", "ano", "sige"}


class WeightedTrainer(Trainer):
    """Cross-entropy weighted by class balance and by each row's sample weight."""

    def __init__(self, *args, class_weights=None, **kwargs):
        super().__init__(*args, **kwargs)
        self.class_weights = class_weights

    def compute_loss(self, model, inputs, return_outputs=False, **kwargs):
        labels = inputs.pop("labels")
        row_w = inputs.pop("weight")
        outputs = model(**inputs)
        ce = torch.nn.functional.cross_entropy(outputs.logits, labels,
                                               weight=self.class_weights.to(outputs.logits.device),
                                               reduction="none")
        loss = (ce * row_w).sum() / row_w.sum()
        return (loss, outputs) if return_outputs else loss


def metrics_dict(y_true, y_pred):
    p, r, f, _ = precision_recall_fscore_support(y_true, y_pred, average="binary", pos_label=1, zero_division=0)
    tn, fp, fn, tp = confusion_matrix(y_true, y_pred, labels=[0, 1]).ravel()
    fpr = fp / (fp + tn) if (fp + tn) else float("nan")
    return {"n": int(len(y_true)), "precision": round(float(p), 4), "recall": round(float(r), 4),
            "f1": round(float(f), 4), "fpr_ham": round(float(fpr), 4), "tp": int(tp), "fp": int(fp),
            "fn": int(fn), "tn": int(tn)}


def print_metrics(name, m, show_matrix=False):
    print(f"  {name:<28} n={m['n']:<5} precision={m['precision']:.3f} recall={m['recall']:.3f} "
          f"F1={m['f1']:.3f} FPR={m['fpr_ham']:.3f}  [TP={m['tp']} FP={m['fp']} FN={m['fn']} TN={m['tn']}]")
    if show_matrix:
        print(f"    confusion matrix     pred ham  pred scam\n"
              f"    true ham           {m['tn']:>9}  {m['fp']:>9}\n"
              f"    true scam          {m['fn']:>9}  {m['tp']:>9}")


def compute_metrics(eval_pred):
    logits, labels = eval_pred
    pred = (softmax(logits)[:, 1] >= THRESHOLD).astype(int)
    m = metrics_dict(labels, pred)
    return {"f1": m["f1"], "precision": m["precision"], "recall": m["recall"], "fpr_ham": m["fpr_ham"]}


def softmax(logits):
    e = np.exp(logits - logits.max(axis=1, keepdims=True))
    return e / e.sum(axis=1, keepdims=True)


def is_tagalog(text):
    return len(set(re.findall(r"[a-z]+", text.lower())) & TAGALOG) >= 2


def main():
    set_seed(SEED)
    use_gpu = torch.cuda.is_available()
    print(f"Device: {torch.cuda.get_device_name(0) if use_gpu else 'CPU (slow; consider the Colab notebook)'}")

    df = pd.read_csv(DATA)
    df["norm"] = df["text"].map(normalize)
    df["labels"] = (df["label"] == "scam").astype(int)
    train, val, test = (df[df["split"] == s].reset_index(drop=True) for s in ["train", "val", "test"])
    print(f"train={len(train)}  val={len(val)}  test={len(test)}")

    tok = AutoTokenizer.from_pretrained(BASE_MODEL)

    def to_ds(frame):
        ds = Dataset.from_pandas(frame[["norm", "labels", "weight"]])
        return ds.map(lambda b: tok(b["norm"], truncation=True, max_length=MAX_LEN), batched=True,
                      remove_columns=["norm"])

    train_ds, val_ds, test_ds = to_ds(train), to_ds(val), to_ds(test)
    lens = [len(x) for x in train_ds["input_ids"]]
    print(f"Token lengths (train): median={int(np.median(lens))}, 95%={int(np.percentile(lens, 95))}, "
          f"truncated at {MAX_LEN}: {sum(l >= MAX_LEN for l in lens)}")

    # Balanced class weights (same idea as class_weight='balanced' in sklearn)
    counts = np.bincount(train["labels"], minlength=2)
    class_weights = torch.tensor(len(train) / (2 * counts), dtype=torch.float)
    print(f"Class weights: ham={class_weights[0]:.2f} scam={class_weights[1]:.2f}")

    model = AutoModelForSequenceClassification.from_pretrained(
        BASE_MODEL, num_labels=2, id2label=LABELS, label2id={v: k for k, v in LABELS.items()})

    args = TrainingArguments(
        output_dir=str(ROOT / "checkpoints" / "roberta"),
        num_train_epochs=EPOCHS,
        learning_rate=LR,
        per_device_train_batch_size=BATCH,
        per_device_eval_batch_size=64,
        weight_decay=0.01,
        warmup_ratio=0.1,
        eval_strategy="epoch",
        save_strategy="epoch",
        save_total_limit=1,
        load_best_model_at_end=True,
        metric_for_best_model="f1",
        greater_is_better=True,
        fp16=use_gpu,
        logging_steps=25,
        report_to=[],
        seed=SEED,
        remove_unused_columns=False,   # keep the 'weight' column for the weighted loss
        dataloader_num_workers=0,      # Windows-safe
    )
    trainer = WeightedTrainer(model=model, args=args, train_dataset=train_ds, eval_dataset=val_ds,
                              data_collator=DataCollatorWithPadding(tok), compute_metrics=compute_metrics,
                              class_weights=class_weights)
    trainer.train()
    if use_gpu:
        print(f"Peak GPU memory: {torch.cuda.max_memory_allocated() / 1e9:.2f} GB")

    # ---- Evaluate the best checkpoint
    def predict(ds):
        return softmax(trainer.predict(ds).predictions)[:, 1]

    metrics = {}
    val_prob = predict(val_ds)
    print("\nValidation (best checkpoint):")
    for src in ["all", "real_ph", "uci", "synthetic"]:
        mask = np.ones(len(val), bool) if src == "all" else (val["source"] == src).values
        metrics[f"val_{src}"] = metrics_dict(val["labels"].values[mask], (val_prob[mask] >= THRESHOLD).astype(int))
        print_metrics(f"val {src}", metrics[f"val_{src}"])

    test_prob = predict(test_ds)
    test_pred = (test_prob >= THRESHOLD).astype(int)
    print("\nHeld-out TEST (real_ph only, so this is also the local Filipino/Taglish subset):")
    metrics["test_real_ph"] = metrics_dict(test["labels"].values, test_pred)
    print_metrics("test real_ph", metrics["test_real_ph"], show_matrix=True)

    print("\nLanguage-shortcut check (false positive rate on real_ph test HAM):")
    ham = test["labels"].values == 0
    tl = test["text"].map(is_tagalog).values
    for name, mask in [("Tagalog/Taglish", ham & tl), ("English", ham & ~tl)]:
        n, fp = mask.sum(), (test_pred[mask] == 1).sum()
        print(f"  test ham, {name:<18} n={n:<4} FP={fp:<3} FPR={fp / n if n else float('nan'):.3f}")

    print("\nTest false positives (ham flagged as scam):")
    for t, p in zip(test["text"][ham & (test_pred == 1)], test_prob[ham & (test_pred == 1)]):
        print(f"  P={p:.2f} | {t[:120]}")
    print("\nTest false negatives (scam missed):")
    for t, p in zip(test["text"][~ham & (test_pred == 0)], test_prob[~ham & (test_pred == 0)]):
        print(f"  P={p:.2f} | {t[:120]}")

    # ---- Compare with the baseline (phase 1)
    base_path = MODELS / "baseline_config.json"
    if base_path.exists():
        base = json.loads(base_path.read_text(encoding="utf-8"))["metrics"]
        b = base.get("test_real_ph_onnx", base["test_real_ph"])
        r = metrics["test_real_ph"]
        print("\nTest real_ph: baseline vs RoBERTa")
        print(f"  {'model':<22} {'precision':>9} {'recall':>7} {'F1':>7} {'FPR ham':>8}")
        print(f"  {'TF-IDF + LR (onnx)':<22} {b['precision']:>9.3f} {b['recall']:>7.3f} {b['f1']:>7.3f} {b['fpr_ham']:>8.3f}")
        print(f"  {'RoBERTa-tagalog':<22} {r['precision']:>9.3f} {r['recall']:>7.3f} {r['f1']:>7.3f} {r['fpr_ham']:>8.3f}")

    # ---- Save the best model + tokenizer
    trainer.save_model(str(OUT_DIR))
    tok.save_pretrained(str(OUT_DIR))
    (MODELS / "transformer_metrics.json").write_text(json.dumps(metrics, indent=2), encoding="utf-8")
    print(f"\nSaved best model -> {OUT_DIR}\nSaved metrics -> {MODELS / 'transformer_metrics.json'}")


if __name__ == "__main__":
    main()
