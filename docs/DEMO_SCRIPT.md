# Sipat Kilatis — demo script (3 minutes) and pitch outline

## Before the demo (checklist)

- [ ] Release APK installed (`app/build/outputs/apk/release/app-release.apk`) and opened once.
- [ ] Gemma model pushed to the phone (Settings → AI explanations shows "AI model installed").
- [ ] Permissions on: Home → "All automatic checks are on." On Xiaomi: Autostart ON, Battery saver "No restrictions".
- [ ] App language set to **English** for the AI part (Gemma's English is more reliable than its Tagalog).
- [ ] History cleared (Settings → Clear history) so the counters start clean.
- [ ] A second phone ready to send the SMS below (cellular signal ON on the demo phone).
- [ ] Demo messages ready to paste: see `docs/demo_messages.md`.
- [ ] Phone screen mirrored to the projector (e.g. scrcpy or Android Studio's device mirroring).

## The 3-minute flow

| Time | What you do | What you say |
|---|---|---|
| 0:00 | Show Home. Swipe down: **turn OFF Wi-Fi and mobile data** (keep cellular for SMS). | "Every check happens on this phone. Watch: no internet at all. The app doesn't even have permission to go online." |
| 0:20 | From the second phone, send by SMS: `GCash: Naka-lock ang account mo. I-verify agad sa gcash-verify[.]xyz/login` | "This is a typical Filipino scam text. Scammers write the link like this to get past the networks' link filter." |
| 0:35 | The **"Possible scam detected"** pop-up appears. Tap **View details**. | "Caught in about a tenth of a second, offline. It found the fake GCash link, the 'account locked' threat, and the rush to verify." |
| 1:00 | On the Result screen: point at the **highlighted words**, the **risk score**, the **warning signs**, and **What to do now**. Wait for the **AI explanation** to replace the template (~10-25 s the first time). | "A small AI model, Gemma, running on the phone, explains it in plain words. If it's slow or unsure, a safe built-in explanation is shown instead, so people always get help." |
| 1:40 | Send a normal text from the second phone: `Uy, tuloy ba tayo sa Saturday? Kita tayo sa SM North mga 3pm ha.` No alert. | "Normal messages pass quietly. No false alarm." |
| 1:55 | Open **Check a message**, paste: `Your PayPal account is restricted. Click http://secure-ph.com and enter your OTP to restore access.` → Scan. | "You can also paste or share any message from Messenger or Viber to check it." |
| 2:20 | Open **History**: show both SMS, the counters on Home, and the filters. Tap **Report scam** on one. | "Everything is saved only on this phone. Reports can be exported with names and numbers hidden." |
| 2:40 | Settings → tap the version number 5 times → **Demo mode** (10 messages, 10/10, ~150 ms each). | "Ten real-world examples in Filipino, English, and Taglish: all correct, all on-device." |
| 3:00 | Done. | "Sipat Kilatis: sipat, look closely; kilatis, tell real from fake. Private, offline, in our own languages." |

**If something goes wrong:** the SMS doesn't arrive (network) → paste the same text into Check a message. The AI
explanation is slow → keep talking, the template explanation is already on screen. The app was killed by MIUI →
open it once and send again.

## 5-slide pitch outline

### 1. Problem
- Filipinos are flooded with scam texts: fake GCash / bank "account locked" alerts, parcel fees, "nanalo ka" prizes,
  loan and job offers. In English, Filipino, and Taglish.
- Generic spam filters are built on English data, need the cloud, and don't explain *why* a message is dangerous.

### 2. Solution
- Sipat Kilatis checks every SMS and chat notification (Messenger, Viber, Telegram, WhatsApp, GCash, Maya)
  automatically, and any message you paste or share.
- Clear verdict (Safe / Suspicious / Scam), the risky words highlighted, an explanation in plain words, and what to do.

### 3. How it works offline
- Three checks combined into one risk score, all on the phone:
  rules (English + Filipino scam phrases), a URL checker (blocklist, fake brand domains, short links, disguised
  links like `gcash-verify[.]xyz`), and AI (fine-tuned RoBERTa-tagalog + a TF-IDF model, ONNX Runtime).
- A local LLM (Gemma 3 1B on the phone's GPU) explains flagged messages; a safety check blocks bad answers.
- The app has **no internet permission**: Android itself blocks it from going online.

### 4. Results (our measurements)
- Trained on 3,271 de-duplicated messages, incl. 862 real Philippine messages.
- Held-out test set of 173 real PH messages (AI models only): F1 **0.978**, caught **109/109 scams**,
  5 of 64 normal messages flagged (7.8%).
- Phrase rules that lift link-free scams: flagged **0 of 1,864** normal messages in our data.
- Demo set: **10/10**. On a mid-range phone (Snapdragon 732G): **~150 ms** per message; AI explanation ~11 s.

### 5. Privacy and impact
- Messages never leave the phone; history and reports are stored only on the device; exports hide numbers and names.
- Works in airplane mode, in dead zones, on ₱0 load: protection doesn't depend on mobile data.
- Next steps: more real PH messages (especially normal ones), a Tagalog-tuned explainer, and an optional, opt-in
  blocklist update.
