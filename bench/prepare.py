"""Downloads the benchmark data: a fixed 1000-image sample of COCO val2017 and the whole of LFW.

    python bench/prepare.py [data-dir]        (default D:/nungil-bench)

Then push it to the phone and run the instrumented benchmark:

    adb shell mkdir -p /sdcard/Android/data/com.nungil/files/bench
    adb push <data-dir>/coco /sdcard/Android/data/com.nungil/files/bench/coco
    adb push <data-dir>/lfw  /sdcard/Android/data/com.nungil/files/bench/lfw
    gradlew installDebug installDebugAndroidTest
    adb shell am instrument -w -e class com.nungil.bench.DeviceBench com.nungil.test/androidx.test.runner.AndroidJUnitRunner
    adb pull /sdcard/Android/data/com.nungil/files/bench/out <data-dir>/out
    python bench/analyze.py [data-dir]
"""
import json
import random
import sys
import tarfile
import urllib.request
import zipfile
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

SEED = 2026
COCO_SAMPLE = 1000
COCO_ANNOTATIONS = "http://images.cocodataset.org/annotations/annotations_trainval2017.zip"
COCO_IMAGE = "http://images.cocodataset.org/val2017/%012d.jpg"
# The figshare copies scikit-learn uses; the original UMass server is gone.
LFW_IMAGES = "https://ndownloader.figshare.com/files/5976018"
LFW_PAIRS = "https://ndownloader.figshare.com/files/5976006"


def fetch(url: str, target: Path) -> Path:
    if not target.exists():
        print("downloading", url)
        part = target.with_name(target.name + ".part")
        request = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(request) as response, open(part, "wb") as out:
            while chunk := response.read(1 << 20):
                out.write(chunk)
        part.rename(target)
    return target


def coco(data: Path) -> None:
    truth = data / "instances_val2017.json"
    if not truth.exists():
        archive = fetch(COCO_ANNOTATIONS, data / "annotations_trainval2017.zip")
        with zipfile.ZipFile(archive) as z, open(truth, "wb") as out:
            out.write(z.read("annotations/instances_val2017.json"))
    ids = sorted(image["id"] for image in json.loads(truth.read_text())["images"])
    sample = sorted(random.Random(SEED).sample(ids, COCO_SAMPLE))
    (data / "coco").mkdir(exist_ok=True)
    (data / "coco-sample.json").write_text(json.dumps(sample))
    with ThreadPoolExecutor(8) as pool:
        list(pool.map(lambda i: fetch(COCO_IMAGE % i, data / "coco" / ("%012d.jpg" % i)), sample))
    print("coco:", len(list((data / "coco").glob("*.jpg"))), "images")


def lfw(data: Path) -> None:
    fetch(LFW_PAIRS, data / "pairs.txt")
    if not (data / "lfw").exists():
        with tarfile.open(fetch(LFW_IMAGES, data / "lfw.tgz")) as archive:
            archive.extractall(data, filter="data")
    print("lfw:", len(list((data / "lfw").glob("*/*.jpg"))), "images")


if __name__ == "__main__":
    data = Path(sys.argv[1] if len(sys.argv) > 1 else "D:/nungil-bench")
    data.mkdir(parents=True, exist_ok=True)
    coco(data)
    lfw(data)
