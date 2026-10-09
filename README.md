# Sipat Kilatis

**On-device scam (smishing) detector for Filipino SMS and chat messages.**
Works fully offline — your messages never leave your phone.

> *Sipat* (to look closely) + *Kilatis* (to scrutinize, to tell real from fake).

Built for the **AppBuildersPH Hackathon 2026 — Local AI**.

---

## The problem

Filipinos get flooded with scam texts: fake GCash / bank "account locked" alerts, parcel "delivery fee"
links, "nanalo ka" prize claims, loan and job offers. These messages arrive in **English, Filipino, and
Taglish**, and generic spam filters (built mostly on English data) miss many of them or can't explain why
a message is dangerous.

## Why it runs locally

| Reason | What it means for the user |
|---|---|
| **Private** | Reading SMS and chat notifications means seeing OTPs, bank alerts, and personal conversations. None of that is uploaded — detection happens entirely on the phone. |
| **Works offline / on prepaid** | Protection doesn't depend on mobile data. It works in airplane mode, in dead zones, and on ₱0 load. |
| **Instant** | Messages are scored the moment they arrive — no upload, no server queue. |
| **Free to run** | No per-message cloud API cost, so it can screen every message, all day. |

Network access is **optional** and used only for blocklist / model updates. The app never depends on it.

---

## How it works

```
Incoming SMS / chat notification / pasted or shared text
        │
        ▼
Preprocessor  ─ lowercase, normalize lookalikes (0→o, 1→l, @→a, $→s …),
                extract URLs, phone numbers, peso amounts
        │
        ▼
Detection engine (all on-device)
  ├─ Rules engine    weighted EN/FIL scam phrases: "account locked", "i-verify",
  │                  "nanalo ka", "delivery fee", OTP requests, urgency ("ngayon na", "agad")
  ├─ URL checker     offline blocklist, lookalike brand domains (gcash, maya, bdo, bpi,
  │                  lbc, jnt, shopee, lazada …), shorteners, raw-IP links, odd TLDs
  └─ ML classifier   TF-IDF + logistic regression (baseline) →
                     fine-tuned RoBERTa-Tagalog (quantized ONNX)
        │
        ▼
score = 0.6·ML + 0.25·URL + 0.15·Rules   (known-blocklist URL → at least 0.9)

   SAFE  < 0.4  ≤  SUSPICIOUS  < 0.7  ≤  SCAM
        │
        ▼
Explainer (SUSPICIOUS / SCAM only)
  small local LLM (Gemma ~1B, MediaPipe LLM Inference) explains *why*
  in the user's language; template fallback built from the triggered flags
```

**Planned screens:** Onboarding · Home dashboard · Check a message · Result · Warning pop-up ·
History · Scam guide · Settings.

---

## Project status

| Component | Status |
|---|---|
| Data preparation pipeline (`ml/prepare_data.py`) | ✅ Done |
| Baseline TF-IDF + LR classifier (`ml/train_baseline.py`) | ✅ Done |
| Export baseline to ONNX (skl2onnx) | 🚧 Planned |
| Fine-tune RoBERTa-Tagalog → quantized ONNX | 🚧 Planned |
| Android app (Kotlin + Jetpack Compose) | 🚧 Scaffolded (template) |
| SMS receiver / notification listener / share-to-check | 🚧 Planned |
| Rules engine + URL checker (on Android) | 🚧 Planned |
| Local LLM explainer (Gemma via MediaPipe) | 🚧 Planned |
| Room DB: history, feedback, trusted contacts | 🚧 Planned |

---

## Repository layout

```
Sipat-Kilatis/
├── app/                      Android app (Kotlin, Jetpack Compose, Material 3)
│   └── src/main/java/com/example/sipatkilatis/
├── gradle/, build.gradle.kts, settings.gradle.kts
├── ml/                       Python: data prep, training, model export
│   ├── data/raw/             raw datasets (not committed)
│   ├── data/processed/       cleaned, merged dataset (messages.csv)
│   ├── models/               trained / exported models (git-ignored)
│   ├── prepare_data.py
│   ├── train_baseline.py
│   ├── requirements.txt
│   └── setup_venv.ps1 / setup_venv.sh
├── docs/                     notes and write-ups
└── CLAUDE.md                 full architecture spec (weights and thresholds live here)
```

---

## ML pipeline

### 1. Data preparation — `prepare_data.py`

Input: `ml/data/raw/spam_ham_dataset_updated (1).xlsx` (no header; column A = label, column B = text).
The file is a concatenation of blocks — **UCI | real PH | UCI | synthetic PH | real PH (tail)** — and the
script finds block boundaries from anchor messages rather than guessing from content.

Steps:
1. Map labels (`spam` → `scam`), clean HTML / line breaks, drop empty and `<<Content not supported.>>` rows.
2. Flip the **inverted labels** in the synthetic PH block.
3. **Deduplicate on a template key** (names, codes, amounts, digits → placeholders); majority label wins on conflicts.
4. Relabel pure OTP-delivery messages (code only, no ask to send/reply/share, no link) as **ham**.
5. Down-sample UCI ham to 1,500 rows; weight real PH rows **2×**.
6. Hold out a stratified **20% of real PH messages** as the test set.

Output: `ml/data/processed/messages.csv` — `text, label, source, weight, split, raw_row`.

### 2. Baseline model — `train_baseline.py`

- `TfidfVectorizer` (word 1–2-grams, `min_df=2`, sublinear TF) + `LogisticRegression` (`class_weight="balanced"`).
- Input normalization replaces URLs, peso amounts, and numbers with `urltoken` / `amttoken` / `numtoken`.
  **The Android preprocessor must mirror `normalize()` exactly.**
- Reports:
  - held-out test metrics on **real PH** messages (precision, recall, F1, false positive rate)
  - 5-fold CV metrics per source (real PH / UCI / synthetic)
  - a **language-shortcut check** — false positive rate on Tagalog/Taglish vs English ham, so the model
    doesn't learn "Tagalog = scam"
  - misclassified examples and top scam/ham features
- Saves `ml/models/baseline_tfidf_lr.joblib`.

### Results

| Model | Test set | Precision | Recall | F1 | FPR |
|---|---|---|---|---|---|
| TF-IDF + LR | real PH (held-out) | _TBD_ | _TBD_ | _TBD_ | _TBD_ |
| RoBERTa-Tagalog (int8 ONNX) | real PH (held-out) | _TBD_ | _TBD_ | _TBD_ | _TBD_ |

---

## Getting started

### ML (Python)

```powershell
# Windows
cd ml
.\setup_venv.ps1
.\.venv\Scripts\python.exe prepare_data.py
.\.venv\Scripts\python.exe train_baseline.py
```

```bash
# macOS / Linux
cd ml
./setup_venv.sh
.venv/bin/python prepare_data.py
.venv/bin/python train_baseline.py
```

Place the raw dataset at `ml/data/raw/spam_ham_dataset_updated (1).xlsx` first (raw data is not committed).

### Android

1. Open the repository root in **Android Studio**.
2. Let Gradle sync, then run the `app` configuration on a device or emulator.
3. Exported models and tokenizer files go in `app/src/main/assets/` (git-ignored; keep them small enough to ship in the APK).

- Package: `com.example.sipatkilatis`
- Kotlin · Jetpack Compose · Material 3
- Target spec: minSdk 26, targetSdk 34

---

## Disclosure (hackathon requirement)

**Models**
- `jcblaise/roberta-tagalog-base` — fine-tuned, exported to quantized ONNX *(planned)*
- Gemma ~1B via MediaPipe LLM Inference API — explanation generation *(planned)*
- TF-IDF + logistic regression (scikit-learn) — baseline / fallback classifier

**Frameworks and libraries**
- Python: scikit-learn, pandas, openpyxl, PyTorch, Hugging Face Transformers / Datasets / Optimum, ONNX, ONNX Runtime, skl2onnx
- Android: Kotlin, Jetpack Compose, Material 3, ONNX Runtime Android, MediaPipe, Room

**Datasets**
- [UCI SMS Spam Collection](https://archive.ics.uci.edu/dataset/228/sms+spam+collection) (Almeida & Hidalgo)
- Real Philippine scam / ham messages (collected, anonymized with `<REAL NAME>`)
- Synthetic Philippine message templates
- _List any other sources here (e.g. third-party scam datasets, with author credit)._

**Cloud APIs**
- None required. Optional online updates (blocklist / model) only.

**AI-assisted development**
- _List the tools used (e.g. Claude Code, Devin)._

**Pre-existing code / assets**
- _None — or list them here._

---

## Team

Jerwin Alvarez
Jenelle Salcedo
Ahl Satingin
Matthew Christian Quisao
