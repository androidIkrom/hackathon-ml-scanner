"""Export Ultralytics YOLO26n to TFLite (LiteRT) for the app (AGPL-3.0, like the app since 2026-10-13).

Makes app/src/main/assets/yolo26n.tflite (not in git, like the other models), input 640 high and 480 wide and prints the model's input and output
shapes and its first rows on a test picture, so the app's reading of them (core/scan/YoloOutput) can be checked.

A smaller model to try: `export.py w8a32` (INT8 weights, float work, no calibration) makes yolo26n_w8a32.tflite
(2.8 MB, but 260 ms against 144 ms on the CPU, benchmark_model on the emulator, 2026-10-15);
`export.py 8` (INT8 throughout, calibrated on coco128) makes yolo26n_int8.tflite. Full INT8 has no end-to-end head:
its output is the raw 84 x N, which needs NMS.

The LiteRT export runs on Linux or macOS only; on Windows, in WSL:
  curl -LsSf https://astral.sh/uv/install.sh | sh
  cd ~ && mkdir -p yolo26 && cd yolo26 && ~/.local/bin/uv venv -p 3.12 .venv
  ~/.local/bin/uv pip install -p .venv/bin/python torch torchvision --index-url https://download.pytorch.org/whl/cpu
  ~/.local/bin/uv pip install -p .venv/bin/python ultralytics
  PATH=~/.local/bin:$PATH .venv/bin/python /mnt/d/Ml-Scanner/tools/yolo26/export.py
"""
import glob
import os
import shutil
import sys

import numpy as np
from ultralytics import YOLO

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "..", "app", "src", "main", "assets")
WORK = os.path.expanduser("~/yolo26/out")


def main():
    quantize = sys.argv[1] if len(sys.argv) > 1 else None
    tag = {None: "", "w8a32": "_w8a32", "8": "_int8"}[quantize]
    asset = os.path.join(ASSETS, f"yolo26n{tag}.tflite")
    os.makedirs(WORK, exist_ok=True)
    os.chdir(WORK)
    model = YOLO("yolo26n.pt")
    # nms=False keeps the end-to-end head: one row per thing, no NMS in the app (left out, the raw 84 x 8400 comes out).
    # 640 high, 480 wide: Live's upright frame as it is, no grey bars and a quarter less work than 640 x 640.
    extra = {}
    if quantize == "w8a32":
        extra = {"quantize": "w8a32"}
    elif quantize == "8":
        extra = {"quantize": 8, "data": "coco128.yaml", "fraction": 1.0}
    path = str(model.export(format="litert", imgsz=[640, 480], nms=False, **extra))
    print("exported:", path)
    tflite = path if path.endswith(".tflite") else sorted(glob.glob(os.path.join(path, "*.tflite")))[0]
    shutil.copyfile(tflite, asset)
    print("asset:", os.path.abspath(asset), os.path.getsize(asset), "bytes")
    check(tflite)


def check(tflite):
    """The shapes, and the best rows on Ultralytics' bus picture (people and a bus)."""
    try:
        from ai_edge_litert.interpreter import Interpreter
    except ImportError:
        from tensorflow.lite.python.interpreter import Interpreter
    from PIL import Image
    from ultralytics.utils import ASSETS

    it = Interpreter(model_path=tflite)
    it.allocate_tensors()
    inp = it.get_input_details()[0]
    outs = it.get_output_details()
    print("input:", inp["shape"], inp["dtype"])
    for o in outs:
        print("output:", o["shape"], o["dtype"])
    nchw = inp["shape"][1] == 3
    h, w = (inp["shape"][2], inp["shape"][3]) if nchw else (inp["shape"][1], inp["shape"][2])
    img = Image.open(os.path.join(ASSETS, "bus.jpg")).convert("RGB")
    scale = min(w / img.width, h / img.height)
    nw, nh = round(img.width * scale), round(img.height * scale)
    canvas = Image.new("RGB", (w, h), (114, 114, 114))
    canvas.paste(img.resize((nw, nh)), ((w - nw) // 2, (h - nh) // 2))
    x = np.asarray(canvas, dtype=np.float32)[None] / 255.0
    if nchw:
        x = x.transpose(0, 3, 1, 2)
    it.set_tensor(inp["index"], x)
    it.invoke()
    y = it.get_tensor(outs[0]["index"])
    print("raw output shape:", y.shape)
    rows = y[0] if y.shape[-1] in (6, 7) else y[0].T
    best = rows[np.argsort(-rows[:, 4])][:6] if rows.shape[1] in (6, 7) else rows[:6]
    np.set_printoptions(precision=3, suppress=True)
    print("first rows (x1 y1 x2 y2 score class):")
    print(best)


if __name__ == "__main__":
    main()
