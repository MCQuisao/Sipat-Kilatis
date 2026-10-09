"""
Data prep for Sipat Kilatis.

Reads  data/raw/spam_ham_dataset_updated (1).xlsx  (no header: A = label, B = text)
Writes data/processed/messages.csv  with columns:
    text, label (scam|ham), source (real_ph|synthetic|uci), weight, split (train|test), raw_row

Run:  .venv\\Scripts\\python.exe prepare_data.py
"""
import html
import re
import sys
from pathlib import Path

import pandas as pd
from sklearn.model_selection import train_test_split

sys.stdout.reconfigure(encoding="utf-8")  # Windows console + ₱ / Tagalog text
pd.set_option("display.width", 200)

ROOT = Path(__file__).parent
RAW = ROOT / "data" / "raw" / "spam_ham_dataset_updated (1).xlsx"
OUT = ROOT / "data" / "processed" / "messages.csv"
SEED = 42
UCI_HAM_TARGET = 1500
REAL_PH_WEIGHT = 2.0

# The synthetic PH templates in the raw file have their labels inverted
# ("Send the OTP now..." = ham, "Clinic: your appointment..." = spam). Flip them back.
FIX_INVERTED_SYNTHETIC_LABELS = True


# ---------------------------------------------------------------- helpers

def counts(df, title):
    """Print counts per source and label."""
    print(f"\n=== {title}: {len(df)} rows ===")
    if "source" in df:
        print(pd.crosstab(df["source"], df["label"], margins=True, margins_name="total"))
    else:
        print(df["label"].value_counts().to_string())


def clean_text(t):
    """Unescape HTML, turn real/literal/Excel-escaped line breaks into spaces, collapse whitespace."""
    t = html.unescape(str(t))
    for nl in ("_x000D_", "\\r\\n", "\\n", "\r\n", "\n", "\r"):
        t = t.replace(nl, " ")
    return re.sub(r"\s+", " ", t).strip()


NAME_RE = re.compile(
    r"\b(to|from|ni|kay|si|dear|hi|hello)\s+"
    r"([A-Z][a-z]+(?:\s+(?:de|dela|del|delos|de la|[A-Z][a-z]+)){1,3})"
)
CODE_RE = re.compile(r"\b(?=[A-Za-z-]*\d)(?=[\d-]*[A-Za-z])[A-Za-z0-9]+(?:-[A-Za-z0-9]+)*\b")  # PKG2201, 8255-1673-EKXG
PESO_RE = re.compile(r"(₱|\bphp\s?|\bp)\s?\d[\d,]*(\.\d+)?", re.I)


def dedupe_key(t):
    """Template key: names/codes/amounts/digits -> placeholders, lowercase, collapsed spaces."""
    t = t.replace("<REAL NAME>", " <name> ")
    t = NAME_RE.sub(r"\1 <name>", t)                # 'to Ana Lopez' -> 'to <name>'
    t = t.lower()
    t = PESO_RE.sub(" <amt> ", t)                   # ₱2,084 / PHP 500 / P180
    t = CODE_RE.sub(" <code> ", t)                  # tracking / reference codes
    t = re.sub(r"\d+", "#", t)                      # any other number
    return re.sub(r"\s+", " ", t).strip()


# ---------------------------------------------------------------- source detection
#
# The raw file is a concatenation of blocks:
#   UCI (copy 1) | real PH | UCI (copy 2) | synthetic PH | real PH (tail)
# Content-only heuristics fail here (much real PH spam is English casino spam, and UCI talks about
# "birthdays" and "smart"), so the block boundaries are found from anchor messages instead.
# The content heuristic below is kept only as a cross-check.

UCI_FIRST = "Go until jurong point"            # first message of the UCI SMS Spam Collection
UCI_LAST = "Rofl. Its true to its name"         # last message of the UCI SMS Spam Collection


def find_blocks(texts):
    """Return a source label per row, using the anchor messages to find block boundaries."""
    starts = [i for i, t in enumerate(texts) if t.startswith(UCI_FIRST)]
    ends = [i for i, t in enumerate(texts) if t == UCI_LAST]
    assert len(starts) == 2 and len(ends) == 2, f"UCI anchors not found as expected: {starts} {ends}"
    ph_start = ends[0] + 1
    ph_first_text = texts[ph_start]
    ph_repeats = [i for i in range(ends[1] + 1, len(texts)) if texts[i] == ph_first_text]
    synth_end = ph_repeats[0] if ph_repeats else len(texts)   # real PH tail starts where its 1st msg repeats

    source = ["real_ph"] * len(texts)
    for i in range(len(texts)):
        if starts[0] <= i <= ends[0] or starts[1] <= i <= ends[1]:
            source[i] = "uci"
        elif ends[1] < i < synth_end:
            source[i] = "synthetic"
    print(f"\nBlocks (Excel rows): uci 1-{ends[0]+1}, real_ph {ph_start+1}-{starts[1]}, "
          f"uci {starts[1]+1}-{ends[1]+1}, synthetic {ends[1]+2}-{synth_end}, real_ph {synth_end+1}-{len(texts)}")
    return source


PH_BRANDS = r"\b(gcash|g-cash|maya|paymaya|bdo|bpi|metrobank|landbank|unionbank|security bank|rcbc|pnb|" \
            r"chinabank|meralco|pldt|globe|dito|converge|philhealth|pag-ibig|pagibig|sss|dswd|gsis|" \
            r"shopee|lazada|lbc|j&t|jnt|ninja van|angkas|ayuda|sabong|e-sabong|pagcor|jili|peso|pesos)\b"
TAGALOG = {
    "ang", "ng", "mga", "po", "opo", "naman", "lang", "lng", "kasi", "kc", "ito", "iyong", "iyo", "mo", "ko",
    "sa", "ka", "ikaw", "kayo", "natin", "namin", "hindi", "wala", "para", "pra", "salamat", "nga", "rin",
    "yung", "ung", "nyo", "niyo", "ninyo", "kita", "ako", "siya", "sya", "sila", "kami", "tayo", "nang",
    "nmn", "tlga", "talaga", "bka", "baka", "dito", "doon", "ngayon", "agad", "lamang", "kung", "pag",
    "nanalo", "panalo", "makuha", "libre", "ba", "na", "pa", "ay", "at", "si", "ni", "kay",
}
# Short words that also occur in English/Singlish UCI texts only count with a stronger Tagalog word.
WEAK_TAGALOG = {"na", "pa", "ba", "ay", "at", "si", "ni", "ka", "mo", "ko", "sa", "kay"}
TEMPLATE_DOMAINS = r"verify-ph\.com|secure-ph\.com|claim-reward-ph\.com|parcel-release-ph\.com"


def ph_signal(t):
    """True if text looks Philippine: +63 number, peso, PH brand, or Tagalog words."""
    low = t.lower()
    if re.search(r"\+63|\b639\d{9}\b|₱|\bphp\s?\.?\s?\d|\bp\d[\d,]*\b|\b\d[\d,]*p\b", low):
        return True
    if re.search(PH_BRANDS, low):
        return True
    words = set(re.findall(r"[a-z]+", low))
    strong = (words & TAGALOG) - WEAK_TAGALOG
    return len(strong) >= 1 and len(words & TAGALOG) >= 2


def heuristic_source(row, key_count):
    """Content-only guess (cross-check only): templated short PH text = synthetic, PH signal = real_ph."""
    t = row["text"]
    templated = key_count[row["key"]] >= 5            # synthetic templates repeat ~23x
    short = len(t) <= 160 and "<REAL NAME>" not in t
    synth_style = bool(
        re.search(TEMPLATE_DOMAINS, t)
        or re.search(r"₱\d", t)                       # synthetic uses ₱1234 (no commas/spaces)
        or re.match(r"^[A-Z][\w&\-' ]{1,30}:\s", t)   # 'Meralco: ...', 'GCash Alert: ...'
    )
    if templated and short and synth_style and ph_signal(t):
        return "synthetic"
    if ph_signal(t):
        return "real_ph"
    return "uci"


# ---------------------------------------------------------------- OTP relabel rule

# The message must clearly be delivering a code ("Your OTP is 123456", "123456 is your Facebook code")
CODE_DELIVERY = (r"\b(otp|one[- ]time (pin|password|code)|verification code|security code|authentication code|"
                 r"login code|confirmation code|passcode)\b|\bis your [\w ]{0,30}code\b|\bcode is\b")
NEGATED_ASK = (r"\b(do not|don't|dont|never|huwag|wag|hindi)\s+(\w+\s+){0,3}"
               r"(share|give|send|disclose|reveal|ibigay|ibahagi|i-share|ipaalam)\b")
ASK_WORDS = (r"\b(send|sending|reply|replying|share|sharing|give|provide|enter|forward|text back|call|claim|"
             r"confirm|win|won|prize|click|tap|ibigay|ipadala|i-send|isend|ibahagi|i-reply|i-click)\b")


def delivers_code_only(t):
    """A message that just delivers a code/OTP and does not ask the user to send/reply/share it."""
    low = t.lower()
    no_amounts = PESO_RE.sub(" ", low)                 # '₱48819' is an amount, not a code
    if not (re.search(CODE_DELIVERY, low) and re.search(r"\b\d{4,8}\b", no_amounts)):
        return False
    if re.search(r"https?://|www\.|\b[\w-]+\.(com|ph|net|live|xyz|top|im|io|me|cc)\b", low):
        return False                                   # code + link = phishing pattern
    without_warnings = re.sub(NEGATED_ASK, " ", low)   # 'Do NOT share this' is a warning, not an ask
    return not re.search(ASK_WORDS, without_warnings)


# ---------------------------------------------------------------- pipeline

def main():
    df = pd.read_excel(RAW, header=None, names=["label", "text"])
    df["raw_row"] = df.index + 1                       # 1-based Excel row, for tracing back
    counts(df, "Raw file")

    # ---- Step 1: map labels, drop junk, normalize whitespace
    df["label"] = df["label"].astype(str).str.strip().str.lower().map({"spam": "scam", "ham": "ham"})
    df["text"] = df["text"].fillna("").map(clean_text)
    # Source is assigned from file blocks before any rows are dropped (needs the anchor rows).
    df["source"] = find_blocks(df["text"].tolist())
    bad = df["label"].isna() | (df["text"] == "") | df["text"].str.contains("<<Content not supported.>>", regex=False)
    df = df[~bad].copy()
    print(f"Step 1 dropped {bad.sum()} rows (empty / '<<Content not supported.>>' / unknown label)")
    df["key"] = df["text"].map(dedupe_key)
    raw_key_count = df["key"].value_counts()           # template repeat counts, for the step-3 cross-check
    counts(df, "Step 1: mapped + cleaned")

    if FIX_INVERTED_SYNTHETIC_LABELS:
        syn = df["source"] == "synthetic"
        df.loc[syn, "label"] = df.loc[syn, "label"].map({"scam": "ham", "ham": "scam"})
        counts(df, "Step 1b: inverted synthetic labels flipped")
        # Show one row per template so the flip can be eyeballed
        show = df[syn].drop_duplicates("key").sort_values(["label", "text"])
        print(f"\nSynthetic templates after flip ({len(show)} unique keys):")
        for _, r in show.iterrows():
            print(f"  {r.label:<4} | {r.text[:110]}")

    # ---- Step 2: dedupe on normalized key (majority label wins if copies disagree)
    conflicts = df.groupby("key")["label"].nunique()
    conflicts = conflicts[conflicts > 1]
    if len(conflicts):
        print(f"\nStep 2: {len(conflicts)} keys have conflicting labels (majority vote used):")
        for k in conflicts.index[:20]:
            g = df[df["key"] == k]
            print(f"  {dict(g['label'].value_counts())} | {g['text'].iloc[0][:100]}")
    majority = df.groupby("key")["label"].agg(lambda s: s.value_counts().idxmax())
    df = df.drop_duplicates("key").copy()
    df["label"] = df["key"].map(majority)
    counts(df, "Step 2: deduplicated")

    # ---- Step 3: sources (assigned from file blocks in step 1) + content-heuristic cross-check
    guess = df.apply(heuristic_source, axis=1, key_count=raw_key_count)
    print("\nCross-check, rows = file block (used), cols = content heuristic:")
    print(pd.crosstab(df["source"], guess))
    for src in ["real_ph", "synthetic", "uci"]:
        print(f"\n--- 10 examples: {src} ---")
        for _, r in df[df["source"] == src].sample(10, random_state=SEED).iterrows():
            print(f"  {r.label:<4} | {r.text[:120]}")
    counts(df, "Step 3: sources")

    # ---- Step 4: OTP-delivery messages are ham
    otp = (df["label"] == "scam") & df["text"].map(delivers_code_only)
    print(f"\nStep 4: relabeling {otp.sum()} scam -> ham (code delivered, no ask to send/reply/share):")
    for _, r in df[otp].iterrows():
        print(f"  [{r.source}] row {r.raw_row}: {r.text[:120]}")
    df.loc[otp, "label"] = "ham"
    counts(df, "Step 4: OTP relabel")

    # ---- Step 5: down-sample UCI ham, weight real PH rows
    uci_ham = df[(df["source"] == "uci") & (df["label"] == "ham")]
    drop = uci_ham.drop(uci_ham.sample(min(UCI_HAM_TARGET, len(uci_ham)), random_state=SEED).index).index
    df = df.drop(drop).copy()
    df["weight"] = (df["source"] == "real_ph").map({True: REAL_PH_WEIGHT, False: 1.0})
    counts(df, f"Step 5: UCI ham down-sampled to {UCI_HAM_TARGET} (real_ph weight={REAL_PH_WEIGHT})")

    # ---- Step 6: test = stratified 20% of real_ph only
    ph = df[df["source"] == "real_ph"]
    _, test_idx = train_test_split(ph.index, test_size=0.2, stratify=ph["label"], random_state=SEED)
    df["split"] = "train"
    df.loc[test_idx, "split"] = "test"
    for split in ["train", "test"]:
        counts(df[df["split"] == split], f"Step 6: {split} split")

    OUT.parent.mkdir(parents=True, exist_ok=True)
    df[["text", "label", "source", "weight", "split", "raw_row"]].to_csv(OUT, index=False, encoding="utf-8")
    print(f"\nSaved {len(df)} rows -> {OUT}")


if __name__ == "__main__":
    main()
