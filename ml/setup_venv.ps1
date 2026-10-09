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

# pip installs the CPU-only torch on Windows. If an NVIDIA GPU is present, swap in the CUDA build
# (same version) so phase 2 fine-tuning runs on the GPU.
if (Get-Command nvidia-smi -ErrorAction SilentlyContinue) {
    $torchVer = (& .\.venv\Scripts\python.exe -c "import torch; print(torch.__version__.split('+')[0])")
    Write-Host "NVIDIA GPU found: installing CUDA build of torch $torchVer"
    & .\.venv\Scripts\python.exe -m pip install --force-reinstall --no-deps "torch==$torchVer" --index-url https://download.pytorch.org/whl/cu130
}

Write-Host "Done. Activate with: .\.venv\Scripts\Activate.ps1"
