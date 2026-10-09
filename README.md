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
- The test set is small: one message = ~1.6 points of FPR, so the two models are close. They also make
  **different mistakes**, so the app runs **both and averages them** (ensemble), then combines that with
  the rules engine and URL checker (below).

---

## How it works

```
Incoming SMS  ·  chat notification (Messenger, Viber, WhatsApp, GCash, Maya …)  ·  pasted / shared text
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
  │                  lbc, jnt, shopee, lazada …), shorteners, raw-IP links, odd TLDs,
  │                  and disguised links (gcash-verify[.]xyz, "(dot)", "bit ly/x")
  └─ ML ensemble     ML = average of RoBERTa-Tagalog (int8 ONNX) + TF-IDF/LR (ONNX),
                     run with ONNX Runtime Android (if one fails to load, the other is used)
        │
        ▼
score = 0.6·ML + 0.25·URL + 0.15·Rules
  + safety rules (see below)

   SAFE  < 0.4  ≤  SUSPICIOUS  < 0.7  ≤  SCAM      (Normal sensitivity)
        │
        ▼
Alert  ─ SCAM: heads-up notification · SUSPICIOUS: normal notification · SAFE: silent
        │
        ▼
Explainer (SUSPICIOUS / SCAM only)
  small local LLM (Gemma ~1B, MediaPipe LLM Inference) explains *why*
  in the user's language; template fallback built from the triggered flags
```

**Safety rules on top of the weighted score:**

| Rule | Why |
|---|---|
| Strong scam phrases (Rules ≥ 0.6) → at least **SCAM**; medium (≥ 0.4) → at least **SUSPICIOUS** | PH networks strip links from person-to-person SMS, so many real scam texts have **no link** — and the ML models (trained mostly on link scams) would score them low. On the dataset, **0 of 1,864 legit messages** reach the medium rule level. |
| Every link goes to an official site (gcash.com, lazada.com.ph, zoom.us …) and no strong phrases → capped at **SAFE** | The ML models treat *any* link as scam-like, which would flag real bank and shop messages. (All of google.com / facebook.com do **not** count as official — anyone can publish forms and pages there.) |
| Link on the offline blocklist → at least **0.9 (SCAM)** | Known scam domains are always flagged. |

**Sensitivity setting** shifts the SUSPICIOUS / SCAM thresholds: Low 0.5 / 0.8 · Normal 0.4 / 0.7 · High 0.3 / 0.6.

---

## Project status

| Phase | Component | Status |
|---|---|---|
| 0 | Project setup (Android + Python ML monorepo) | ✅ Done |
| 1 | Data prep + TF-IDF / LR baseline + ONNX export | ✅ Done |
| 2 | RoBERTa-Tagalog fine-tune + int8 ONNX export | ✅ Done |
| 3 | Android app skeleton and screens (English + Filipino) | ✅ Done |
| 4 | On-device detection engine (rules + URL checker + RoBERTa / baseline ensemble) | ✅ Done |
| 5 | SMS + chat screening, scam alerts, rule floors, disguised links, official-link cap | ✅ Done |
| 6 | Local LLM explainer (Gemma 3 1B via MediaPipe, GPU) with safety check + template fallback | ✅ Done |
| 7 | Room database (history, feedback, trusted contacts), masked report export, clear history | ✅ Done — the optional online blocklist update was deliberately skipped (the app has no internet permission) |
| 8 | Demo set + hidden demo mode, app review, release APK (R8), demo script, README | ✅ Done |

**What the Android app does today:**
- **Screens real messages automatically, fully offline:**
  - incoming **SMS** (`SmsReceiver`)
  - notifications from **Messenger, Messenger Lite, Viber, WhatsApp, GCash, Maya** and the default SMS app
    (`MessageNotificationListener`)
  - duplicate messages within 10 seconds are only checked once
- **Scam alerts:** SCAM → high-priority heads-up notification · SUSPICIOUS → normal notification · SAFE → silent.
  Alerts have **View details** and **Mark as safe** actions, and Android's automatic "Open link" button is turned off.
- **Manual check:** paste a message, or **Share → Sipat Kilatis** from any app.
- **Result screen** highlights the suspicious parts of the message and lists the reasons.
- **AI explanation on the phone:** for SUSPICIOUS / SCAM, Gemma 3 1B (on the phone's GPU) writes a short explanation.
  It is shown only if it finishes within 20 s and passes a safety check; otherwise a built-in template explanation
  is shown. Measured on a Snapdragon 732G: ~10 s to load (once), ~11 s per explanation.
- **History saved on the phone** (Room): survives restarts, with search and verdict filters. **Mark as safe** /
  **Report scam** on every result; **Export my reports** saves a CSV with numbers, emails, and names masked, which
  you share yourself. **Clear history** in Settings.
- **Hidden demo mode:** Settings → tap the version number 5 times. Runs the 10 demo messages
  (`docs/demo_messages.md`) through the detector: 10/10 as expected, ~150 ms each on a mid-range phone.
- Screens: Onboarding · Home dashboard · Check a message · Result · History · Scam guide · Settings
  (sensitivity, protection on/off, permissions).
- Full **English and Filipino** UI, switchable per app.
- Permissions used: `RECEIVE_SMS`, notification access, `POST_NOTIFICATIONS`. **No `INTERNET` permission.**

---

## Repository layout

```
Sipat-Kilatis/
├── app/                          Android app (Kotlin, Jetpack Compose, Material 3)
│   ├── src/main/java/com/example/sipatkilatis/
│   │   ├── SipatApp.kt           app-wide singletons (detector, repo, prefs, alerts, screener)
│   │   ├── capture/              SmsReceiver, MessageNotificationListener, MessageScreener, ScamAlerts
│   │   ├── detection/            OnDeviceScamDetector, Preprocessor, RegexRulesEngine, OfflineUrlChecker,
│   │   │                         OnnxClassifiers (ensemble), RobertaTokenizer, TextNormalizer, RiskScorer
│   │   ├── ui/                   screens, components, theme, AppNavHost, MainViewModel
│   │   ├── data/                 scan repository, app preferences
│   │   └── model/                data classes
│   └── src/main/assets/          blocklist.txt, tokenizer/ (+ ONNX models, git-ignored)
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
4. Copy the trained models from `ml/models/` into `app/src/main/assets/` (`baseline.onnx`,
   `scam_classifier_int8.onnx`; git-ignored). The tokenizer files and `blocklist.txt` are already there.
   If a model is missing, the app falls back to the other model, or to rules + URL checks only.
5. *(Optional, for AI explanations)* Download `gemma3-1b-it-int4.task` (555 MB) from
   [litert-community/Gemma3-1B-IT](https://huggingface.co/litert-community/Gemma3-1B-IT) (accept the Gemma license),
   install the app once, then push the model to the phone:
   ```powershell
   adb push "$env:USERPROFILE\Downloads\gemma3-1b-it-int4.task" /sdcard/Android/data/com.example.sipatkilatis/files/llm/model.task
   ```
   Uninstalling the app deletes this file. Without it, the app uses template explanations.

**Install the release APK** (R8-shrunk, ~235 MB, signed with the debug key for sideloading — not for the Play Store):

```powershell
.\gradlew.bat assembleRelease
adb install -r app\build\outputs\apk\release\app-release.apk
```

On Xiaomi / Redmi / POCO phones, also turn on **Autostart** and set **Battery saver → No restrictions** for the app,
otherwise MIUI may stop SMS screening in the background. On Android 13+, a sideloaded APK (not installed through
adb / Android Studio) needs App info → ⋮ → **Allow restricted settings** before chat-app screening can be enabled.

**Tests**

```powershell
# Unit tests: normalizer + tokenizer parity with Python, rule / URL messages, risk scorer
.\gradlew.bat testDebugUnitTest

# On an emulator: both ONNX models vs the Python test vectors (within 0.02), end-to-end timing
$env:ANDROID_SERIAL = "emulator-5554"; .\gradlew.bat connectedDebugAndroidTest

# On a phone with the Gemma model: explanation speed, safety check, overlapping requests (no crash).
# Use adb directly: Gradle's connected tests uninstall the app, which deletes the pushed model.
.\gradlew.bat assembleDebug assembleDebugAndroidTest
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb shell am instrument -w -e class com.example.sipatkilatis.detection.LlmOnDeviceTest com.example.sipatkilatis.test/androidx.test.runner.AndroidJUnitRunner

# Run demo mode from a computer and read the results from the log
adb shell am start -n com.example.sipatkilatis/.MainActivity --ez open_demo true
adb logcat -s SipatKilatis | findstr DEMO
```

**Demo:** see `docs/DEMO_SCRIPT.md` (3-minute flow + 5-slide pitch outline) and `docs/demo_messages.md`.

**Try a scam SMS on the emulator** (open the app once first, then press Home):

```powershell
adb -s emulator-5554 emu sms send 09171234567 "GCash: Na-lock ang account mo. I-verify agad sa gcash-verify.xyz"
```

- Package: `com.example.sipatkilatis`
- Kotlin · Jetpack Compose · Material 3 · AGP 9 · KSP (Room)
- minSdk 26 · targetSdk 34 · compileSdk 37

---

## Disclosure (hackathon requirement)

**Models**
- `jcblaise/roberta-tagalog-base` — fine-tuned on our data, exported to int8 ONNX
- TF-IDF + logistic regression (scikit-learn) — exported to ONNX; averaged with RoBERTa on-device (ensemble)
- Gemma ~1B via MediaPipe LLM Inference API — explanation generation *(in progress)*

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

## Team: LFInternship
- Ahl B. Satingin
- Jerwin L. Alvarez
- Jenelle G. Salcedo
- Matthew Christian A. Quisao
