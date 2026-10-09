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
| **Instant** | Messages are scored the moment they arrive — no upload, no server queue (~25 ms per message for the transformer on a laptop CPU). |
| **Free to run** | No per-message cloud API cost, so it can screen every message, all day. |

Network access is **optional** and used only for blocklist / model updates. The app never depends on it —
it currently ships **without the `INTERNET` permission** at all.

---

## Results so far

Both models are tested on a **held-out set of 173 real Philippine messages** (109 scam, 64 legit) that
were never used for training.

| Model | Size | Precision | Recall | F1 | FPR |
|---|---|---|---|---|---|
| TF-IDF + LR (ONNX) | small | 0.964 | **0.991** | **0.977** | **0.063** |
| RoBERTa-Tagalog (fp32) | 437 MB | 0.947 | 0.982 | 0.964 | 0.094 |
| RoBERTa-Tagalog (**int8 ONNX**, on-device) | **110 MB** | 0.955 | 0.982 | 0.968 | 0.078 |

**In plain words (TF-IDF + LR):** catches **108 of 109 scams**, and wrongly flags **4 of 64** legit messages.

- **Precision**: when the app says *scam*, how often it's right.
- **Recall**: out of all real scams, how many it catches.
- **F1**: one score balancing precision and recall.
- **FPR** (false positive rate): how often a *legit* message is wrongly flagged. **Lower is better.**

Notes:
- int8 quantization shrank RoBERTa **4× (437 → 110 MB) with no loss in accuracy**, and runs at
  **~25 ms per message** on a laptop CPU.
- The test set is small: one message = ~1.6 points of FPR, so the two models are close. The final app
  combines the ML score with the rules engine and URL checker (below) to cut false positives further.

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
  └─ ML classifier   fine-tuned RoBERTa-Tagalog (int8 ONNX, ONNX Runtime Android)
                     fallback: TF-IDF + logistic regression (ONNX)
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

---

## Project status

| Phase | Component | Status |
|---|---|---|
| 0 | Project setup (Android + Python ML monorepo) | ✅ Done |
| 1 | Data prep + TF-IDF / LR baseline + ONNX export | ✅ Done |
| 2 | RoBERTa-Tagalog fine-tune + int8 ONNX export | ✅ Done |
| 3 | Android app skeleton and screens (English + Filipino) | ✅ Done |
| 4 | Real detection engine on Android (rules + URL checker + ONNX model) | 🚧 Next — app uses a keyword placeholder for now |
| 5 | SMS receiver, notification listener, warning pop-up | 🚧 Planned |
| 6 | Local LLM explainer (Gemma via MediaPipe) | 🚧 Planned |
| 7 | Room database (history, feedback, trusted contacts) + optional online updates | 🚧 Planned — in-memory sample data for now |

**Android app so far (phase 3):**
- Screens: Onboarding · Home dashboard · Check a message · Result · History · Scam guide · Settings
- Full **English and Filipino** UI, switchable per app
- **Share → Sipat Kilatis**: share any text from another app to check it
- Result screen highlights the suspicious parts of the message and lists the reasons

---

## Repository layout

```
Sipat-Kilatis/
├── app/                          Android app (Kotlin, Jetpack Compose, Material 3)
│   └── src/main/java/com/example/sipatkilatis/
│       ├── ui/                   screens, components, theme, AppNavHost, MainViewModel
│       ├── data/                 scan repository, app preferences
│       ├── detection/            detector interface (+ temporary placeholder)
│       └── model/                data classes
├── gradle/, build.gradle.kts, settings.gradle.kts
├── ml/                           Python: data prep, training, model export
│   ├── data/raw/                 raw dataset (not committed — contains real messages)
│   ├── data/processed/           cleaned, merged dataset (messages.csv, not committed)
│   ├── models/                   configs, metrics, test vectors (model binaries git-ignored)
│   ├── text_normalize.py         shared normalize() — must be mirrored on Android
│   ├── prepare_data.py           raw xlsx → messages.csv
│   ├── train_baseline.py         TF-IDF + LR → baseline.onnx
│   ├── predict_baseline.py       run the baseline ONNX model from the command line
│   ├── train_transformer.py      fine-tune RoBERTa-Tagalog
│   ├── train_transformer_colab.ipynb   same, on a free Colab GPU
│   ├── export_onnx.py            RoBERTa → ONNX → int8
│   ├── reference_inference.py    exact inference steps the Android app must copy
│   ├── requirements.txt
│   └── setup_venv.ps1 / setup_venv.sh
├── docs/                         notes and write-ups
└── CLAUDE.md                     full architecture spec (weights and thresholds live here)
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
6. Split: **test = stratified 20% of real PH messages only**; **val = 10%** of the remaining rows (all sources).

Output: `ml/data/processed/messages.csv` — `text, label, source, weight, split, raw_row`.

### 2. Shared normalization — `text_normalize.py`

`normalize()` strips the `<REAL NAME>` anonymization marker, lowercases, and replaces URLs, peso amounts,
and numbers with `urltoken` / `amttoken` / `numtoken`. It runs **before both models**, and the Android
preprocessor must match it exactly (checked against the test vectors in `ml/models/`).

### 3. Baseline — `train_baseline.py`

- TF-IDF on **word 1–2-grams + character 2–5-grams** (catches misspellings like "G-Cash", "acc0unt")
  + `LogisticRegression` (`class_weight="balanced"`).
- Reports test metrics on real PH messages, CV metrics per source, and a **language-shortcut check**
  (false positive rate on Tagalog/Taglish vs English legit messages, so the model doesn't learn "Tagalog = scam").
- Exports `models/baseline.onnx` (skl2onnx, string input) + `baseline_config.json` + `baseline_test_vectors.json`.
  Uses the `com.microsoft` Tokenizer op, so it needs the full `onnxruntime-android` package.

### 4. Transformer — `train_transformer.py` + `export_onnx.py`

- Fine-tunes `jcblaise/roberta-tagalog-base` (max length 128, best epoch picked by validation F1).
  No NVIDIA GPU? Use `train_transformer_colab.ipynb` on a free Colab T4.
- `export_onnx.py`: Optimum ONNX export → dynamic **int8** quantization →
  `models/scam_classifier_int8.onnx` (~110 MB) + `models/tokenizer/` + `models/test_vectors.json`.
- Inputs `input_ids` + `attention_mask`; output logits, softmax index 1 = P(scam).
  `reference_inference.py` shows the exact steps for Android.

Metrics are saved in `ml/models/baseline_config.json`, `transformer_metrics.json`, and `transformer_export.json`.

---

## Getting started

### ML (Python)

Place the raw dataset at `ml/data/raw/spam_ham_dataset_updated (1).xlsx` first (raw data is not committed).

```powershell
# Windows
cd ml
.\setup_venv.ps1                                  # installs CUDA PyTorch if an NVIDIA GPU is present
.\.venv\Scripts\python.exe prepare_data.py
.\.venv\Scripts\python.exe train_baseline.py
.\.venv\Scripts\python.exe predict_baseline.py "GCash: account locked, i-verify agad sa gcash-ph.xyz"
.\.venv\Scripts\python.exe train_transformer.py
.\.venv\Scripts\python.exe export_onnx.py
```

```bash
# macOS / Linux
cd ml
./setup_venv.sh
.venv/bin/python prepare_data.py
.venv/bin/python train_baseline.py
.venv/bin/python train_transformer.py
.venv/bin/python export_onnx.py
```

### Android

1. Open the repository root in **Android Studio** and let Gradle sync.
2. Run the `app` configuration on a device or emulator.
3. Or build from a terminal:
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat assembleDebug
   ```
4. Exported models and tokenizer files go in `app/src/main/assets/` (git-ignored).

- Package: `com.example.sipatkilatis`
- Kotlin · Jetpack Compose · Material 3 · AGP 9 · KSP (Room)
- minSdk 26 · targetSdk 34 · compileSdk 37

---

## Disclosure (hackathon requirement)

**Models**
- `jcblaise/roberta-tagalog-base` — fine-tuned on our data, exported to int8 ONNX
- TF-IDF + logistic regression (scikit-learn) — baseline / fallback classifier, exported to ONNX
- Gemma ~1B via MediaPipe LLM Inference API — explanation generation *(planned)*

**Frameworks and libraries**
- Python: scikit-learn, pandas, openpyxl, PyTorch, Hugging Face Transformers / Datasets / Optimum / Accelerate / Evaluate, ONNX, ONNX Runtime, skl2onnx
- Android: Kotlin, Jetpack Compose, Material 3, Navigation Compose, AppCompat, Kotlin Coroutines, Room, ONNX Runtime Android, MediaPipe Tasks GenAI

**Datasets**
- [UCI SMS Spam Collection](https://archive.ics.uci.edu/dataset/228/sms+spam+collection) (Almeida & Hidalgo)
- Real Philippine scam / ham messages (collected, anonymized with `<REAL NAME>`; not published)
- Synthetic Philippine message templates

**Cloud APIs**
- None. Google Colab is an optional training environment only; the app itself never calls a cloud service.

**AI-assisted development**
- Claude Code

---

## Team
- Ahl B. Satingin
- Jerwin L. Alvarez
- Jenelle G. Salcedo
- Matthew Christian A. Quisao
