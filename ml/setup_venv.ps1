# Creates the Python virtual environment for the ML pipeline (Windows PowerShell).
# Usage (from the ml folder):  .\setup_venv.ps1
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

# Create .venv only if it does not exist yet
if (-not (Test-Path ".venv")) {
    python -m venv .venv
}

# Install dependencies inside the venv
& .\.venv\Scripts\python.exe -m pip install --upgrade pip
& .\.venv\Scripts\python.exe -m pip install -r requirements.txt

Write-Host "Done. Activate with: .\.venv\Scripts\Activate.ps1"
