#!/usr/bin/env bash
# Creates the Python virtual environment for the ML pipeline (macOS/Linux).
# Usage (from the ml folder):  ./setup_venv.sh
set -e
cd "$(dirname "$0")"

# Create .venv only if it does not exist yet
[ -d .venv ] || python3 -m venv .venv

# Install dependencies inside the venv
.venv/bin/python -m pip install --upgrade pip
.venv/bin/python -m pip install -r requirements.txt

echo "Done. Activate with: source .venv/bin/activate"
