package com.example.sipatkilatis.ui.demo

import com.example.sipatkilatis.model.Verdict

/** One demo message with the verdict we expect (also documented in docs/demo_messages.md). */
data class DemoMessage(val label: String, val sender: String?, val text: String, val expected: Verdict)

/**
 * 10 realistic messages for the demo (6 scams, 4 normal) in Filipino, English, and Taglish.
 * Senders are fictional; links point to fake domains (never open them).
 */
val DEMO_MESSAGES = listOf(
    // ---- scams
    DemoMessage("GCash lookalike link (Filipino)", "09171234501",
        "GCash: Pansamantalang naka-lock ang account mo dahil sa kahina-hinalang login. I-verify agad sa " +
            "gcash-help-ph.com/verify sa loob ng 24 oras.", Verdict.SCAM),
    DemoMessage("Parcel delivery fee (Taglish)", "09171234502",
        "J&T Express: Naka-hold ang parcel mo dahil sa unpaid delivery fee na P89. Bayaran dito para ma-release: " +
            "jnt-ph.xyz/pay", Verdict.SCAM),
    DemoMessage("Loan offer (English)", "09171234503",
        "Congratulations! Your loan of P30,000 is APPROVED. No requirements needed. Pay the P500 processing fee " +
            "today to release it.", Verdict.SCAM),
    DemoMessage("High-pay job (Taglish)", "09171234504",
        "HIRING! Kumita ng P2,000 kada araw sa pag-like lang ng videos. Walang experience needed. " +
            "Mag-message na agad sa Telegram para mag-start.", Verdict.SUSPICIOUS),
    DemoMessage("Prize claim (Filipino)", "09171234505",
        "Congrats! Nanalo ang number mo ng P50,000 sa GCash Anniversary Raffle. I-claim agad sa " +
            "bit.ly/gcash-premyo bago mag-expire.", Verdict.SCAM),
    DemoMessage("OTP request (English)", "BDO-Security",
        "BDO Security: We detected a suspicious login. Reply with the 6-digit OTP we sent to keep your account " +
            "active within 24 hours.", Verdict.SUSPICIOUS),
    // ---- normal
    DemoMessage("Family (Filipino)", "Mama",
        "Anak, nakauwi ka na ba? Bumili ako ng pancit, kain ka pag-uwi mo.", Verdict.SAFE),
    DemoMessage("Friend (Taglish)", "Bea",
        "Uy, tuloy ba tayo sa Saturday? Kita tayo sa SM North mga 3pm ha.", Verdict.SAFE),
    DemoMessage("Bank notification (English)", "BDO",
        "BDO: You have successfully paid PHP 1,250.00 to MERALCO on 10/09/2026. Ref No. 4829137765. " +
            "Thank you for banking with BDO.", Verdict.SAFE),
    DemoMessage("Real delivery update (English)", "Shopee",
        "Your Shopee order is out for delivery today. You can track it in the Shopee app.", Verdict.SAFE),
)
