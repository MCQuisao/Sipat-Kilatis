"""
Model input normalization, shared by training and prediction.

The ONNX model cannot run Python, so this step happens BEFORE the model:
the Android app must apply exactly the same steps (in this order) and then
feed the normalized string to baseline.onnx. Check it with models/baseline_test_vectors.json.
"""
import re

# Domains / URLs, with or without http(s):// or www.:
#   - explicit scheme or www.
#   - anything shaped like domain.tld/path  (bit.ly/abc, smrt.ph/promo)
#   - a bare domain with a known TLD        (gcash-verify.com, jnt-ph.xyz)
URL_RE = re.compile(
    r"https?://\S+|www\.\S+|"
    r"\b[\w-]+(\.[\w-]+)*\.[a-z]{2,}/\S*|"
    r"\b[\w-]+\.(com|ph|net|org|info|live|life|site|online|shop|xyz|top|im|io|me|cc|co|ly|gl|gd|to|tk|gg|win|"
    r"store|rest|pw|app|club|vip|bet|link|click|icu)\b\S*"
)
# Peso amounts: ₱500, php 1,000, p50 (prefix) or 500php, 20 pesos, 100p (suffix)
AMOUNT_RE = re.compile(r"(₱|\bphp\s?\.?|\bp)\s?\d[\d,]*(\.\d+)?|\b\d[\d,]*\s?(php|pesos?|p)\b")


def normalize(text):
    t = text.replace("<REAL NAME>", " ")   # 1. anonymization marker exists only in real PH spam
    t = t.lower()                           # 2. lowercase
    t = URL_RE.sub(" urltoken ", t)         # 3. URLs -> urltoken
    t = AMOUNT_RE.sub(" amttoken ", t)      # 4. peso amounts -> amttoken
    t = re.sub(r"\d+", " numtoken ", t)     # 5. any other number -> numtoken
    return re.sub(r"\s+", " ", t).strip()   # 6. collapse whitespace
