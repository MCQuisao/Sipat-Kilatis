"""
Baseline scam classifier: TF-IDF + logistic regression (the on-device fallback model).

Reads  data/processed/messages.csv  (from prepare_data.py)
Trains on split == train with sample weights (real_ph = 2x).
Reports:
  - held-out test metrics (real_ph only)
  - 5-fold cross-validated metrics per source on the training rows (real_ph / uci / synthetic)
  - language-shortcut check: false positive rate on real_ph ham, Tagalog vs English
Saves  models/baseline_tfidf_lr.joblib

Run:  .venv\\Scripts\\python.exe train_baseline.py
"""
import re
import sys
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import confusion_matrix, precision_recall_fscore_support
from sklearn.model_selection import StratifiedKFold, cross_val_predict
from sklearn.pipeline import Pipeline

sys.stdout.reconfigure(encoding="utf-8")

ROOT = Path(__file__).parent
DATA = ROOT / "data" / "processed" / "messages.csv"
MODEL_OUT = ROOT / "models" / "baseline_tfidf_lr.joblib"
SEED = 42
THRESHOLD = 0.5

# Tagalog function words, used only to split real_ph ham for the language-shortcut check
TAGALOG = {"ang", "ng", "mga", "po", "opo", "naman", "lang", "lng", "kasi", "ito", "mo", "ko", "sa", "ka",
           "kayo", "natin", "hindi", "wala", "para", "salamat", "nga", "rin", "yung", "ung", "nyo", "niyo",
           "ako", "siya", "sya", "sila", "kami", "tayo", "nang", "nmn", "talaga", "baka", "dito", "ngayon",
           "kung", "pag", "na", "pa", "ba", "din", "yan", "yun", "dun", "jan", "noh", "ano", "sige"}


def normalize(text):
    """Model input normalization. The Android preprocessor must do the same steps."""
    t = text.replace("<REAL NAME>", " ")           # anonymization marker exists only in real PH spam
    t = t.lower()
    t = re.sub(r"https?://\S+|www\.\S+|\b[\w-]+\.(com|ph|net|org|live|life|site|xyz|top|im|io|me|cc|win|store|"
               r"rest|pw|app|club|vip|bet|link|click|icu)\b\S*", " urltoken ", t)
    t = re.sub(r"(₱|\bphp\s?\.?|\bp)\s?\d[\d,]*(\.\d+)?|\b\d[\d,]*\s?(php|pesos?|p)\b", " amttoken ", t)
    t = re.sub(r"\d+", " numtoken ", t)
    return re.sub(r"\s+", " ", t).strip()


def is_tagalog(text):
    words = set(re.findall(r"[a-z]+", text.lower()))
    return len(words & TAGALOG) >= 2


def build_model():
    return Pipeline([
        ("tfidf", TfidfVectorizer(preprocessor=normalize, ngram_range=(1, 2), min_df=2, sublinear_tf=True)),
        ("clf", LogisticRegression(C=4.0, class_weight="balanced", max_iter=2000)),
    ])


def report(name, y_true, y_pred):
    """Print precision / recall / F1 for scam and the false positive rate on ham."""
    if len(y_true) == 0:
        return
    p, r, f, _ = precision_recall_fscore_support(y_true, y_pred, average="binary", pos_label=1, zero_division=0)
    tn, fp, fn, tp = confusion_matrix(y_true, y_pred, labels=[0, 1]).ravel()
    fpr = fp / (fp + tn) if (fp + tn) else float("nan")
    print(f"  {name:<28} n={len(y_true):<5} precision={p:.3f} recall={r:.3f} F1={f:.3f} "
          f"FPR={fpr:.3f}  [TP={tp} FP={fp} FN={fn} TN={tn}]")


def main():
    df = pd.read_csv(DATA)
    df["y"] = (df["label"] == "scam").astype(int)
    train, test = df[df["split"] == "train"], df[df["split"] == "test"]
    print(f"train={len(train)}  test={len(test)} (real_ph only)")

    # ---- Cross-validated predictions on the training rows, reported per source
    cv = StratifiedKFold(n_splits=5, shuffle=True, random_state=SEED)
    strat = train["source"] + "_" + train["label"]       # keep source mix stable across folds
    cv_prob = np.zeros(len(train))
    for tr, va in cv.split(train, strat):
        m = build_model()
        m.fit(train["text"].iloc[tr], train["y"].iloc[tr], clf__sample_weight=train["weight"].iloc[tr].values)
        cv_prob[va] = m.predict_proba(train["text"].iloc[va])[:, 1]
    cv_pred = (cv_prob >= THRESHOLD).astype(int)
    print(f"\n5-fold CV on training rows (threshold {THRESHOLD}):")
    for src in ["real_ph", "uci", "synthetic"]:
        mask = (train["source"] == src).values
        report(f"cv {src}", train["y"].values[mask], cv_pred[mask])

    # ---- Final model on all training rows, evaluated on held-out real_ph
    model = build_model()
    model.fit(train["text"], train["y"], clf__sample_weight=train["weight"].values)
    prob = model.predict_proba(test["text"])[:, 1]
    pred = (prob >= THRESHOLD).astype(int)
    print("\nHeld-out TEST (real_ph only):")
    report("test real_ph", test["y"].values, pred)
    print("  (uci has no held-out test rows by design; see its CV numbers above)")

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
    vocab = model.named_steps["tfidf"].get_feature_names_out()
    coef = model.named_steps["clf"].coef_[0]
    order = np.argsort(coef)
    print("\nTop scam features:", ", ".join(vocab[order[-25:]][::-1]))
    print("Top ham features: ", ", ".join(vocab[order[:25]]))

    MODEL_OUT.parent.mkdir(parents=True, exist_ok=True)
    joblib.dump(model, MODEL_OUT)
    print(f"\nSaved model -> {MODEL_OUT}")


if __name__ == "__main__":
    main()
