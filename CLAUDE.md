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
- **minSdk 26, targetSdk 34, compileSdk 37**. AGP 9 (built-in Kotlin), KSP for Room.
- Dependencies use the version catalog in `gradle/libs.versions.toml`.
- Code layout (`com.example.sipatkilatis`): `ui/` (screens, components, theme, `AppNavHost`, `MainViewModel`),
  `data/` (repository + preferences), `detection/` (engine interfaces), `model/` (data classes).
  Single `MainActivity` (AppCompatActivity, needed for per-app language) + one shared `MainViewModel`.
- Detection engine (`detection/`): `OnDeviceScamDetector` → `Preprocessor`, `RegexRulesEngine`, `OfflineUrlChecker`
  (`assets/blocklist.txt`), `EnsembleClassifier` (`TextNormalizer` + `RobertaTokenizer` + ONNX), `RiskScorer`.
  Models live in `app/src/main/assets/` (git-ignored: copy from `ml/models/` after training).
  Android regex (ICU) is Unicode-aware by default and rejects `UNICODE_CHARACTER_CLASS`; use `TextNormalizer.UNICODE_FLAG`.
- Tests: `.\gradlew.bat testDebugUnitTest` (normalizer + tokenizer parity with Python, 15 rule/URL messages, scorer);
  `$env:ANDROID_SERIAL="emulator-5554"; .\gradlew.bat connectedDebugAndroidTest` (both ONNX models vs Python
  test vectors within 0.02, end-to-end timing). Set ANDROID_SERIAL so tests never run on a personal phone.
- ABIs limited to arm64-v8a + x86_64 (native libs are huge).
- App-wide singletons in `SipatApp.AppGraph` (detector, repo, prefs, alerts, screener), shared by the UI and
  background capture (`capture/`): `SmsReceiver` (manifest, goAsync), `MessageNotificationListener` (watched apps +
  default SMS app), `MessageScreener` (protection check, 10 s duplicate window), `ScamAlerts` (SCAM = high-importance
  heads-up, SUSPICIOUS = default, SAFE = none; "View details" / "Mark as safe"; system "Open link" smart actions OFF).
- Test SMS on the emulator: `adb -s emulator-5554 emu sms send 09171234567 "message"`. A force-stopped app gets no
  SMS broadcasts until it is opened again (Android rule), so test by opening the app once, then pressing Home.
- Placeholders to replace: `FakeScanRepository` → Room (phase 7; history resets when the process dies),
  online update button (phase 7).
- Strings: English in `res/values/`, Filipino in `res/values-fil/` (tag `fil`). Every user-facing string goes in both.
- Build from a terminal: `$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat assembleDebug`.

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
   - **ML classifier**: ensemble of two ONNX models run with **ONNX Runtime Android**:
     fine-tuned `jcblaise/roberta-tagalog-base` (**quantized int8 ONNX**) and **TF-IDF + logistic regression**
     (skl2onnx). `ML = (RoBERTa + baseline) / 2`. If one model fails to load, ML = the other one.
     (Chosen after phase 2: the two make different mistakes; the average caught every scam in the test set.)
   - Combined score:
     ```
     score = 0.6*ML + 0.25*URL + 0.15*Rules
     if no ML model loaded:  score = (0.25*URL + 0.15*Rules) / 0.4
     if ALL links are official (brand sites, zoom.us, meet.google.com, teams) and Rules < 0.4:
         score = min(score, 0.25)                  # SAFE: the ML models treat any link as scam-like
     if Rules >= 0.6: score = max(score, 0.7)      # strong phrase rules -> SCAM
     elif Rules >= 0.4: score = max(score, 0.4)    # medium phrase rules -> at least SUSPICIOUS
     if URL is in the known blocklist: score = max(score, 0.9)
     ```
     Why the rule floors (added in phase 5): PH networks strip links from person-to-person SMS, so most scam
     texts that reach phones have no link, and the ML models (trained mostly on link scams) score them low.
     Measured on the dataset: 0 of 1,864 ham messages reach Rules >= 0.4 (RuleFloorReportTest).
     Official-link cap: whole google.com / facebook.com are NOT official (anyone can publish forms / pages there).
   - URL checker also recognises disguised links (`gcash-verify[.]xyz`, `(.)`, `(dot)`, ` dot `, ` . `, `bit ly/x`)
     and rebuilds them as normal domains for the blocklist / lookalike checks.
   - Sensitivity shifts the thresholds (SUSPICIOUS / SCAM): Low 0.5 / 0.8, Normal 0.4 / 0.7, High 0.3 / 0.6.
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
   - Test set = 20% of real_ph only. Val = 10% of the remaining rows (all sources).
     real_ph weight = 2. UCI ham down-sampled to 1,500.
2. `train_baseline.py`: TF-IDF (word 1-2 + char 2-5 n-grams) + logistic regression → metrics per source
   → `models/baseline.onnx` (skl2onnx; string input; uses the com.microsoft Tokenizer op, so needs the
   full onnxruntime-android package) + `baseline_config.json` + `baseline_test_vectors.json`.
   `predict_baseline.py "msg"` runs the ONNX model from the command line.
   - `normalize()` in `text_normalize.py` (strip `<REAL NAME>`, lowercase, URL/amount/number → tokens)
     runs BEFORE the model and must be mirrored exactly on Android; check against the test vectors.
3. `train_transformer.py`: fine-tune RoBERTa-tagalog-base (same `normalize()` input, max_length 128,
   best epoch by val F1) → `models/roberta_best/`. Colab backup: `train_transformer_colab.ipynb`.
   `export_onnx.py`: Optimum ONNX export → dynamic int8 (ARM64) → `models/scam_classifier_int8.onnx` (~110 MB)
   + `models/tokenizer/` + `models/test_vectors.json` (token ids + expected P(scam)).
   `reference_inference.py` = exact steps Android must copy (inputs input_ids + attention_mask, softmax index 1).
4. Copy exported models + tokenizer files into `app/src/main/assets/` for the app.
   (Model binaries are git-ignored; keep them small enough to ship in the APK.)

Setup: `cd ml; .\setup_venv.ps1` (Windows) or `cd ml && ./setup_venv.sh` (macOS/Linux).

## Rules for all code

- **Privacy first**: message content never leaves the device. No analytics on message text, no cloud APIs.
- **Offline always**: every feature works in airplane mode. Network is optional, only for updates.
- Keep code **simple and readable** (it's a hackathon). Prefer small, obvious classes over clever abstractions.
- Add short comments explaining intent.
- Weights and thresholds above are the source of truth — change them here first if they change.
