#!/bin/bash
# Runs export.py in WSL (see its docstring for the setup).
cd ~/yolo26 || exit 1
export TMPDIR=~/tmp PATH="$HOME/.local/bin:$PATH"
.venv/bin/python /mnt/d/Ml-Scanner/tools/yolo26/export.py > export.log 2>&1
echo "exit $?"
grep -vE 'Downloading|━|^\s*$' export.log | head -80
