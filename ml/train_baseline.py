"""
Baseline scam classifier: TF-IDF (word 1-2 + char 2-5 n-grams) + logistic regression.
This is the on-device fallback model.

Reads  data/processed/messages.csv  (from prepare_data.py)
Trains on split == train with sample weights (real_ph = 2x).
Reports:
  - 5-fold cross-validated metrics per source on the training rows
  - validation metrics per source
  - held-out test metrics (real_ph only) + confusion matrix
  - language-shortcut check: false positive rate on real_ph ham, Tagalog vs English
Exports:
  models/baseline.onnx               string in -> [label, probabilities]; input must be normalize()d first
  models/baseline_config.json        what the Android app needs to run it
  models/baseline_test_vectors.json  10 messages with expected scam probabilities, to check the Android port
  models/baseline_tfidf_lr.joblib    the sklearn pipeline (for Python use)

Run:  .venv\\Scripts\\python.exe train_baseline.py
"""
import json
import re
import sys
from pathlib import Path

import joblib
import numpy as np
import onnxruntime as ort
import pandas as pd
from skl2onnx import convert_sklearn
from skl2onnx.common.data_types import StringTensorType
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import confusion_matrix, precision_recall_fscore_support
from sklearn.model_selection import StratifiedKFold
from sklearn.pipeline import FeatureUnion, Pipeline

from text_normalize import normalize

sys.stdout.reconfigure(encoding="utf-8")

ROOT = Path(__file__).parent
DATA = ROOT / "data" / "processed" / "messages.csv"
MODELS = ROOT / "models"
SEED = 42
THRESHOLD = 0.5
WORD_TOKEN_RE = r"\b\w\w+\b"   # sklearn's default token pattern; passed to the ONNX tokenizer so both agree

# Tagalog function words, used only to split real_ph ham for the language-shortcut check
TAGALOG = {"ang", "ng", "mga", "po", "opo", "naman", "lang", "lng", "kasi", "ito", "mo", "ko", "sa", "ka",
           "kayo", "natin", "hindi", "wala", "para", "salamat", "nga", "rin", "yung", "ung", "nyo", "niyo",
           "ako", "siya", "sya", "sila", "kami", "tayo", "nang", "nmn", "talaga", "baka", "dito", "ngayon",
           "kung", "pag", "na", "pa", "ba", "din", "yan", "yun", "dun", "jan", "noh", "ano", "sige"}

# Messages for the Android parity check (raw text; normalize() is part of what gets checked)
VECTOR_MESSAGES = [
    "Your GCash account is locked, verify here: bit.ly/xyz",
    "BDO: Your account will be suspended. I-verify agad sa http://bdo-secure-ph.com within 24 hours",
    "Congrats! Nanalo ka ng P50,000 sa raffle. I-claim na ngayon, send your OTP to 09171234567",
    "Your parcel is on hold. Pay delivery fee of PHP 150 here: jnt-ph.xyz/pay",
    "Loan approved! Get ₱20,000 cash today, no requirements. Reply YES",
    "Hi anak, uuwi ako mamaya mga 7pm. May ulam pa ba tayo?",
    "Ok po ma'am, salamat sa update. Kita tayo bukas sa office.",
    "You have paid P215.00 of GCash to Dunkin Shell Roosevelt. Ref. No. 156419820.",
    "Your OTP is 482913. Do not share this code with anyone.",
    "Meeting moved to 3pm tomorrow, see you there",
]


def is_tagalog(text):
    words = set(re.findall(r"[a-z]+", text.lower()))
    return len(words & TAGALOG) >= 2


def build_model():
    """TF-IDF word + char n-grams -> logistic regression. Input text must already be normalize()d."""
    features = FeatureUnion([
        ("word", TfidfVectorizer(token_pattern=WORD_TOKEN_RE, ngram_range=(1, 2), min_df=2,
                                 lowercase=False, sublinear_tf=True)),
        # char n-grams catch Taglish spelling variants and misspellings (i-verify / iverify / verifyy)
        ("char", TfidfVectorizer(analyzer="char", ngram_range=(2, 5), min_df=2, max_features=30000,
                                 lowercase=False, sublinear_tf=True)),
    ])
    return Pipeline([
        ("features", features),
        ("clf", LogisticRegression(C=4.0, class_weight="balanced", max_iter=3000)),
    ])


def report(name, y_true, y_pred, show_matrix=False):
    """Print precision / recall / F1 for scam and the false positive rate on ham."""
    if len(y_true) == 0:
        return
    p, r, f, _ = precision_recall_fscore_support(y_true, y_pred, average="binary", pos_label=1, zero_division=0)
    tn, fp, fn, tp = confusion_matrix(y_true, y_pred, labels=[0, 1]).ravel()
    fpr = fp / (fp + tn) if (fp + tn) else float("nan")
    print(f"  {name:<28} n={len(y_true):<5} precision={p:.3f} recall={r:.3f} F1={f:.3f} "
          f"FPR={fpr:.3f}  [TP={tp} FP={fp} FN={fn} TN={tn}]")
    if show_matrix:
        print(f"    confusion matrix     pred ham  pred scam\n"
              f"    true ham           {tn:>9}  {fp:>9}\n"
              f"    true scam          {fn:>9}  {tp:>9}")
    return {"n": int(len(y_true)), "precision": round(p, 4), "recall": round(r, 4), "f1": round(f, 4),
            "fpr_ham": round(fpr, 4)}


def export_onnx(model, check_texts):
    """Convert to ONNX, check it matches sklearn, and return the ONNX session."""
    word_vec = model.named_steps["features"].transformer_list[0][1]
    onx = convert_sklearn(
        model,
        initial_types=[("text", StringTensorType([None, 1]))],
        options={id(model.named_steps["clf"]): {"zipmap": False},   # plain float tensor, easier on Android
                 id(word_vec): {"tokenexp": WORD_TOKEN_RE}},
        target_opset=17,
    )
    path = MODELS / "baseline.onnx"
    path.write_bytes(onx.SerializeToString())
    sess = ort.InferenceSession(str(path), providers=["CPUExecutionProvider"])

    # Parity check: ONNX tokenization is not 100% identical to sklearn's, so measure the gap
    p_onnx = onnx_proba(sess, check_texts)
    p_sk = model.predict_proba(check_texts)[:, 1]
    diff = np.abs(p_onnx - p_sk)
    flips = ((p_onnx >= THRESHOLD) != (p_sk >= THRESHOLD)).sum()
    print(f"\nONNX export: {path.name} ({path.stat().st_size / 1e6:.1f} MB)")
    print(f"  ONNX vs sklearn on {len(check_texts)} val+test rows: max |diff|={diff.max():.4f}, "
          f"mean={diff.mean():.5f}, rows >0.02: {(diff > 0.02).sum()}, verdict flips: {flips}")
    return sess, path


def onnx_proba(sess, normalized_texts):
    x = np.array(normalized_texts, dtype=object).reshape(-1, 1)
    return sess.run(None, {"text": x})[1][:, 1]


def main():
    df = pd.read_csv(DATA)
    df["norm"] = df["text"].map(normalize)
    df["y"] = (df["label"] == "scam").astype(int)
    train, val, test = (df[df["split"] == s] for s in ["train", "val", "test"])
    print(f"train={len(train)}  val={len(val)}  test={len(test)} (test = real_ph only)")

    # ---- Cross-validated predictions on the training rows, reported per source
    cv = StratifiedKFold(n_splits=5, shuffle=True, random_state=SEED)
    strat = train["source"] + "_" + train["label"]       # keep source mix stable across folds
    cv_prob = np.zeros(len(train))
    for tr, va in cv.split(train, strat):
        m = build_model()
        m.fit(train["norm"].iloc[tr], train["y"].iloc[tr], clf__sample_weight=train["weight"].iloc[tr].values)
        cv_prob[va] = m.predict_proba(train["norm"].iloc[va])[:, 1]
    cv_pred = (cv_prob >= THRESHOLD).astype(int)
    print(f"\n5-fold CV on training rows (threshold {THRESHOLD}):")
    for src in ["real_ph", "uci", "synthetic"]:
        mask = (train["source"] == src).values
        report(f"cv {src}", train["y"].values[mask], cv_pred[mask])

    # ---- Final model on all training rows
    model = build_model()
    model.fit(train["norm"], train["y"], clf__sample_weight=train["weight"].values)

    print("\nValidation (all sources):")
    val_pred = (model.predict_proba(val["norm"])[:, 1] >= THRESHOLD).astype(int)
    metrics = {"val_all": report("val all", val["y"].values, val_pred)}
    for src in ["real_ph", "uci", "synthetic"]:
        mask = (val["source"] == src).values
        metrics[f"val_{src}"] = report(f"val {src}", val["y"].values[mask], val_pred[mask])

    prob = model.predict_proba(test["norm"])[:, 1]
    pred = (prob >= THRESHOLD).astype(int)
    print("\nHeld-out TEST (real_ph only, so this is also the local Filipino/Taglish subset):")
    metrics["test_real_ph"] = report("test real_ph", test["y"].values, pred, show_matrix=True)

    # ---- Language shortcut check: does the model flag Tagalog/Taglish ham as scam?
    print("\nLanguage-shortcut check (false positive rate on real_ph HAM):")
    tl_test = test["text"].map(is_tagalog).values
    ham_test = test["y"].values == 0
    for name, mask in [("test ham, Tagalog/Taglish", ham_test & tl_test), ("test ham, English", ham_test & ~tl_test)]:
        n, fp = mask.sum(), (pred[mask] == 1).sum()
        print(f"  {name:<28} n={n:<4} FP={fp:<3} FPR={fp / n if n else float('nan'):.3f}  "
              f"mean P(scam)={prob[mask].mean() if n else float('nan'):.3f}")
    ph_tr = (train["source"] == "real_ph").values
    tl_tr = train["text"].map(is_tagalog).values
    ham_tr = train["y"].values == 0
    for name, mask in [("cv ham, Tagalog/Taglish", ph_tr & ham_tr & tl_tr), ("cv ham, English", ph_tr & ham_tr & ~tl_tr)]:
        n, fp = mask.sum(), (cv_pred[mask] == 1).sum()
        print(f"  {name:<28} n={n:<4} FP={fp:<3} FPR={fp / n if n else float('nan'):.3f}  "
              f"mean P(scam)={cv_prob[mask].mean() if n else float('nan'):.3f}")
    uci_ham = ((train["source"] == "uci") & (train["y"] == 0)).values
    print(f"  {'(compare) cv uci ham':<28} n={uci_ham.sum():<4} FPR={(cv_pred[uci_ham] == 1).mean():.3f}")

    # Show the real_ph mistakes so they can be inspected by hand
    print("\nTest false positives (ham flagged as scam):")
    for t, p in zip(test["text"][ham_test & (pred == 1)], prob[ham_test & (pred == 1)]):
        print(f"  P={p:.2f} | {t[:120]}")
    print("\nTest false negatives (scam missed):")
    miss = (~ham_test) & (pred == 0)
    for t, p in zip(test["text"][miss], prob[miss]):
        print(f"  P={p:.2f} | {t[:120]}")

    # Top features, to spot shortcuts
    names = model.named_steps["features"].get_feature_names_out()
    coef = model.named_steps["clf"].coef_[0]
    order = np.argsort(coef)
    print("\nTop scam features:", ", ".join(names[order[-25:]][::-1]))
    print("Top ham features: ", ", ".join(names[order[:25]]))

    # ---- Export
    MODELS.mkdir(parents=True, exist_ok=True)
    joblib.dump(model, MODELS / "baseline_tfidf_lr.joblib")
    sess, onnx_path = export_onnx(model, pd.concat([val, test])["norm"].tolist())
    # The app runs the ONNX model, so its numbers are the official ones
    print("\nHeld-out TEST with the exported ONNX model (what ships in the app):")
    onnx_pred = (onnx_proba(sess, test["norm"].tolist()) >= THRESHOLD).astype(int)
    metrics["test_real_ph_onnx"] = report("test real_ph (onnx)", test["y"].values, onnx_pred)

    vectors = []
    for msg in VECTOR_MESSAGES:
        norm = normalize(msg)
        vectors.append({"text": msg, "normalized": norm, "scam_probability": round(float(onnx_proba(sess, [norm])[0]), 4)})
    (MODELS / "baseline_test_vectors.json").write_text(json.dumps(vectors, ensure_ascii=False, indent=2), encoding="utf-8")

    config = {
        "model_file": onnx_path.name,
        "input": {"name": "text", "type": "string tensor, shape [batch, 1]",
                  "preprocessing": "apply normalize() from ml/text_normalize.py first (see its docstring)"},
        "outputs": {"label": "int64 [batch], 1 = scam", "probabilities": "float [batch, 2], column 1 = P(scam)"},
        "scam_class_index": 1,
        "threshold": THRESHOLD,
        "features": {"word": {"ngram_range": [1, 2], "token_pattern": WORD_TOKEN_RE},
                     "char": {"ngram_range": [2, 5], "max_features": 30000}},
        "onnx_runtime_note": "uses the com.microsoft Tokenizer op: needs the full onnxruntime-android package",
        "metrics": metrics,
    }
    (MODELS / "baseline_config.json").write_text(json.dumps(config, indent=2), encoding="utf-8")
    print(f"Saved {onnx_path.name}, baseline_config.json, baseline_test_vectors.json, baseline_tfidf_lr.joblib "
          f"-> {MODELS}")
    print("\nTest vectors:")
    for v in vectors:
        print(f"  P={v['scam_probability']:.3f} | {v['text']}")


if __name__ == "__main__":
    main()
