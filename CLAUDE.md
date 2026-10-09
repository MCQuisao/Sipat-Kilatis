# Sipat Kilatis — CLAUDE.md

On-device SMS / message scam (smishing) detector for Android, built for a local-AI hackathon.
Target users are in the Philippines: messages are in **Filipino, English, or Taglish** (code-switched).
Everything runs **fully on-device** and must work in **airplane mode**.

## Repo layout (monorepo)

This root **is** the Android Studio project (created from the Empty Activity Compose template).
Wherever instructions say `/android`, they mean **this root** (`app/`, `gradle/`, `build.gradle.kts`, ...).
Never create a separate `/android` folder or a second Android project.

```
SipatKilatis/
├── app/                      Android app module (Kotlin + Jetpack Compose)
│   └── src/main/java/com/example/sipatkilatis/
├── gradle/, build.gradle.kts, settings.gradle.kts
├── ml/                       Python: data prep, training, model export
│   ├── data/raw/             raw datasets (local_messages.csv: columns text,label; label = scam|ham)
│   ├── data/processed/       cleaned, merged datasets
│   ├── models/               trained + exported models (large files are git-ignored)
│   ├── requirements.txt
│   └── setup_venv.ps1 / setup_venv.sh
└── docs/                     notes
```

## Android facts

- Package / namespace / applicationId: **`com.example.sipatkilatis`** (use this, NOT `com.scamshield.app`).
- App name: **"Sipat Kilatis"**.
- Kotlin, Jetpack Compose, Material 3.
- Target spec: **minSdk 26, targetSdk 34**. (The template was generated with minSdk 24 / targetSdk 37 /
  compileSdk 37 — align `app/build.gradle.kts` with the spec when Android work starts.)
- Dependencies use the version catalog in `gradle/libs.versions.toml`.

## Architecture (all on-device)

1. **Input layer**
   - `BroadcastReceiver` for incoming SMS (`SMS_RECEIVED`).
   - `NotificationListenerService` for chat apps (Messenger, Viber, WhatsApp, Telegram, etc.).
   - Paste / share-to-app screen ("Check a message"), via `ACTION_SEND` text intent.
2. **Preprocessor**
   - Lowercase.
   - Normalize lookalike characters (`0->o`, `1->l`, `@->a`, `3->e`, `$->s`, ...).
   - Extract URLs, phone numbers, and money amounts (e.g. `P5,000`, `PHP 500`, `₱1k`).
3. **Detection engine** — three checks combined into one risk score (each in 0..1):
   - **Rules engine**: weighted regex patterns for scam phrases in English and Filipino
     (account locked, i-verify, claim prize / nanalo ka, delivery fee, OTP, loan approved,
     urgency words like "ngayon na", "agad", "within 24 hours").
   - **URL checker**: offline blocklist, lookalike brand domains (gcash, maya, bdo, bpi, landbank,
     metrobank, unionbank, lazada, shopee, jnt, lbc, ...), URL shorteners, raw IP URLs,
     odd TLDs (.xyz, .top, .click, .icu, ...).
   - **ML classifier**: fine-tuned `jcblaise/roberta-tagalog-base` exported to **quantized ONNX**,
     run with **ONNX Runtime Android**. Fallback: **TF-IDF + logistic regression** (exported via skl2onnx).
   - Combined score:
     ```
     score = 0.6*ML + 0.25*URL + 0.15*Rules
     if URL is in the known blocklist: score = max(score, 0.9)
     ```
   - Verdict: `score < 0.4` → **SAFE**, `0.4 <= score < 0.7` → **SUSPICIOUS**, `score >= 0.7` → **SCAM**.
4. **Explainer** (only for SUSPICIOUS or SCAM)
   - Small local LLM (Gemma ~1B via **MediaPipe LLM Inference API**) writes a short explanation
     in the user's language (Filipino / English / Taglish).
   - Fallback: template explanation built from the triggered flags (rules hit, URL reasons, ML score).
5. **Storage**
   - **Room** database: scan history, user feedback (correct / wrong verdict), trusted contacts.
   - Optional sync when online for blocklist and model updates only. The app must **never depend** on it.

## Screens

Onboarding, Home dashboard, Check a message, Result, Warning pop-up, History, Scam guide, Settings.

## ML pipeline (in `/ml`)

1. `prepare_data.py`: `data/raw/spam_ham_dataset_updated (1).xlsx` (no header; A=label, B=text)
   → `data/processed/messages.csv` (text, label, source, weight, split, raw_row).
   - The file is concatenated blocks: UCI | real PH | UCI | synthetic PH | real PH tail. Source comes from
     block boundaries (UCI first/last message anchors), not content: much real PH spam is English.
   - **Synthetic block labels are inverted in the raw file** and are flipped back in the script.
   - Test set = 20% of real_ph only. real_ph weight = 2. UCI ham down-sampled to 1,500.
2. `train_baseline.py`: TF-IDF + logistic regression → metrics per source → export to ONNX (skl2onnx).
   Its `normalize()` (strip `<REAL NAME>`, URL/amount/number tokens) must be mirrored on Android.
3. Fine-tune RoBERTa-tagalog-base → export with Optimum to ONNX → dynamic int8 quantization.
4. Copy exported models + tokenizer files into `app/src/main/assets/` for the app.
   (Model binaries are git-ignored; keep them small enough to ship in the APK.)

Setup: `cd ml; .\setup_venv.ps1` (Windows) or `cd ml && ./setup_venv.sh` (macOS/Linux).

## Rules for all code

- **Privacy first**: message content never leaves the device. No analytics on message text, no cloud APIs.
- **Offline always**: every feature works in airplane mode. Network is optional, only for updates.
- Keep code **simple and readable** (it's a hackathon). Prefer small, obvious classes over clever abstractions.
- Add short comments explaining intent.
- Weights and thresholds above are the source of truth — change them here first if they change.
