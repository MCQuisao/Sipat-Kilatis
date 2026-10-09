"""
Reference inference for the RoBERTa scam classifier: the exact steps the Android app must copy.

  1. text      -> normalize()                      (text_normalize.py, same as the baseline)
  2. normalized -> RoBERTa byte-level BPE tokens   (tokenizer.json / vocab.json + merges.txt)
                   <s> tokens </s>, truncated to 128 tokens total, no padding needed for one message
  3. input_ids, attention_mask (all 1s)  as int64 tensors of shape [1, seq_len]
  4. logits = model(...)                          shape [1, 2]
  5. P(scam) = softmax(logits)[0][1]              index 1 = scam

Usage:  .venv\\Scripts\\python.exe reference_inference.py "message text"
"""
import sys
from pathlib import Path

import numpy as np
import onnxruntime as ort
from transformers import AutoTokenizer

from text_normalize import normalize

MODELS = Path(__file__).parent / "models"
MAX_LEN = 128
SCAM_INDEX = 1


def load(model_file="scam_classifier_int8.onnx"):
    tok = AutoTokenizer.from_pretrained(str(MODELS / "tokenizer"))
    sess = ort.InferenceSession(str(MODELS / model_file), providers=["CPUExecutionProvider"])
    return tok, sess


def tokenize(tok, text):
    """Steps 1-3. Returns (normalized text, input_ids, attention_mask) as int64 arrays of shape [1, n]."""
    norm = normalize(text)
    enc = tok(norm, truncation=True, max_length=MAX_LEN)
    ids = np.array([enc["input_ids"]], dtype=np.int64)
    return norm, ids, np.ones_like(ids)


def scam_probability(tok, sess, text):
    """Steps 1-5 for one message."""
    _, ids, mask = tokenize(tok, text)
    logits = sess.run(None, {"input_ids": ids, "attention_mask": mask})[0][0]
    e = np.exp(logits - logits.max())
    return float((e / e.sum())[SCAM_INDEX])


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    msg = " ".join(sys.argv[1:]) or "Your GCash account is locked, verify here: bit.ly/xyz"
    tok, sess = load()
    print(f"P(scam) = {scam_probability(tok, sess, msg):.3f} | {msg}")
