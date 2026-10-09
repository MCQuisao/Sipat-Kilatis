# Demo messages

10 realistic messages (6 scams, 4 normal) in Filipino, English, and Taglish, with the verdict the app gives.
The same list is built into the app's hidden **Demo mode** (Settings → tap the version number 5 times), which runs
all 10 through the real on-device detector and shows verdict, score, and timing.

Senders are fictional. **Links point to fake domains: never open them.**

| # | Type | Language | Message | Expected |
|---|---|---|---|---|
| 1 | Fake GCash alert, lookalike link | Filipino | GCash: Pansamantalang naka-lock ang account mo dahil sa kahina-hinalang login. I-verify agad sa gcash-help-ph.com/verify sa loob ng 24 oras. | **SCAM** |
| 2 | Parcel delivery fee | Taglish | J&T Express: Naka-hold ang parcel mo dahil sa unpaid delivery fee na P89. Bayaran dito para ma-release: jnt-ph.xyz/pay | **SCAM** |
| 3 | Loan offer | English | Congratulations! Your loan of P30,000 is APPROVED. No requirements needed. Pay the P500 processing fee today to release it. | **SCAM** |
| 4 | High-pay job | Taglish | HIRING! Kumita ng P2,000 kada araw sa pag-like lang ng videos. Walang experience needed. Mag-message na agad sa Telegram para mag-start. | SUSPICIOUS |
| 5 | Prize claim | Filipino | Congrats! Nanalo ang number mo ng P50,000 sa GCash Anniversary Raffle. I-claim agad sa bit.ly/gcash-premyo bago mag-expire. | **SCAM** |
| 6 | OTP request | English | BDO Security: We detected a suspicious login. Reply with the 6-digit OTP we sent to keep your account active within 24 hours. | SUSPICIOUS |
| 7 | Family | Filipino | Anak, nakauwi ka na ba? Bumili ako ng pancit, kain ka pag-uwi mo. | SAFE |
| 8 | Friend | Taglish | Uy, tuloy ba tayo sa Saturday? Kita tayo sa SM North mga 3pm ha. | SAFE |
| 9 | Bank notification | English | BDO: You have successfully paid PHP 1,250.00 to MERALCO on 10/09/2026. Ref No. 4829137765. Thank you for banking with BDO. | SAFE |
| 10 | Real delivery update | English | Your Shopee order is out for delivery today. You can track it in the Shopee app. | SAFE |

**Measured** (release build, Redmi Note 10 Pro, airplane-mode capable): 10/10 as expected, ~150 ms per message.
On-device AI explanation for message 1: model load ~10 s (once), explanation ~11 s.

Notes
- SUSPICIOUS = a quiet notification; SCAM = a heads-up pop-up alert.
- Message 4 (job) and 6 (OTP) are SUSPICIOUS, not SCAM: the wording is scam-like but there is no link, and the
  ML models (trained mostly on link scams) are less sure. A real reply asking for the OTP should still never be sent.
- For a **live SMS** demo, Philippine networks strip links from person-to-person SMS. Use the link-free or disguised
  versions, e.g. `GCash: Naka-lock ang account mo. I-verify agad sa gcash-verify[.]xyz/login` (SCAM), or send the
  linked ones through Messenger / Viber / Telegram, or paste them in **Check a message**.
