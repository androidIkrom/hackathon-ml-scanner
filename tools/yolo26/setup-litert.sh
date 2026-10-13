#!/bin/bash
# litert-torch with the CPU build of torch (its default pulls CUDA wheels that fill WSL's /tmp).
cd ~/yolo26 || exit 1
mkdir -p ~/tmp
export TMPDIR=~/tmp PATH="$HOME/.local/bin:$PATH"
df -h /tmp ~ | cat
uv pip install -p .venv/bin/python litert-torch "torch==2.13.*" torchvision \
  --index-url https://download.pytorch.org/whl/cpu --extra-index-url https://pypi.org/simple \
  --index-strategy unsafe-best-match > pip-litert.log 2>&1
echo "exit $?"
grep -iE "installed|error|torch" pip-litert.log | head -20
.venv/bin/python -c "import torch, litert_torch; print(torch.__version__, 'litert_torch ok')"
