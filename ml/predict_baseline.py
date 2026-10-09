"""
Classify one message with the exported baseline ONNX model.

Usage:  .venv\\Scripts\\python.exe predict_baseline.py "Your GCash account is locked, verify here: bit.ly/xyz"
"""
import json
import sys
from pathlib import Path

import numpy as np
import onnxruntime as ort

from text_normalize import normalize

sys.stdout.reconfigure(encoding="utf-8")
MODELS = Path(__file__).parent / "models"


def main():
    if len(sys.argv) < 2:
        sys.exit('Usage: python predict_baseline.py "message text"')
    text = " ".join(sys.argv[1:])
    config = json.loads((MODELS / "baseline_config.json").read_text(encoding="utf-8"))
    sess = ort.InferenceSession(str(MODELS / config["model_file"]), providers=["CPUExecutionProvider"])

    norm = normalize(text)  # same step the Android app must do before calling the model
    probs = sess.run(None, {"text": np.array([[norm]], dtype=object)})[1]
    p_scam = float(probs[0][config["scam_class_index"]])

    print(f"Message:    {text}")
    print(f"Normalized: {norm}")
    print(f"P(scam):    {p_scam:.3f}  ->  {'SCAM' if p_scam >= config['threshold'] else 'HAM'} "
          f"(model only; the app combines this with the rules and URL checks)")


if __name__ == "__main__":
    main()
