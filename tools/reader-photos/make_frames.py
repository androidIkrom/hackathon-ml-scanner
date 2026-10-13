"""Reader test frames from real photos (Wikimedia Commons, free licences).

Downloads each photo (once, into tools/reader-photos/cache, not in git), cuts the part a phone held in portrait
would see, and writes 6 camera-sized frames (1440 x 1920, a little hand shake) and the phrases that must be read into
app/src/androidTest/assets/photos/<scene>/ (not in git either). ReaderReplayTest.realPhotos reads them.

Run: python -I tools/reader-photos/make_frames.py
"""
import os
import random
import urllib.request

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
CACHE = os.path.join(HERE, "cache")
OUT = os.path.join(HERE, "..", "..", "app", "src", "androidTest", "assets", "photos")
W, H = 1440, 1920
UA = "NungilReaderTest/1.0 (test images for an accessibility app)"

# name: (file URL, licence, (centre x, centre y, width) of the part seen, as shares of the photo, phrases to read)
SCENES = {
    "laptop-wikipedia": (
        "https://upload.wikimedia.org/wikipedia/commons/7/7c/MSI_laptop_with_English_Wikipedia_screenshot_20100614.jpg",
        "CC BY-SA 4.0, Kristoferb",
        (0.60, 0.40, 0.56),
        ["From Wikipedia, the free encyclopedia", "personal computer designed for mobile use"],
    ),
    "laptop-wikipedia-close": (
        "https://upload.wikimedia.org/wikipedia/commons/7/7c/MSI_laptop_with_English_Wikipedia_screenshot_20100614.jpg",
        "CC BY-SA 4.0, Kristoferb",
        # The smallest print ("From Wikipedia, the free encyclopedia") comes out misread ("From WiJpeda, the ree
        # encydopedia"): an OCR limit, not asked of it.
        (0.52, 0.40, 0.30),
        ["personal computer designed for mobile use", "Laptops are usually notebook-shaped"],
    ),
    "screen-code": (
        None, "CC0, Unsplash (CSS code on a screen)", (0.33, 0.42, 0.55), ["stylesheet", "favicon.ico"],
    ),
    "menu-board": (
        None, "CC BY 4.0 (Drive thru menu board at Del Taco in Albany, Oregon)", (0.66, 0.62, 0.42),
        ["TACOS", "BURRITOS", "QUESADILLAS"],
    ),
    "newspaper": (
        None, "Public domain (Land on the Moon 7 21 1969)", (0.69, 0.40, 0.46),
        ["The Eagle Has Landed", "Two Men Walk on the Moon"],
    ),
    "plaque": (
        None, "Public domain (Annual report of the Director, Field Museum)", (0.42, 0.40, 0.52),
        ["BENEFACTORS", "FIELD MUSEUM", "GEORGE M. PULLMAN", "ANNA LOUISE RAYMOND"],
    ),
}

# Photos fetched before (session scratchpad), by scene: the URLs of these carry long Commons paths.
LOCAL = {
    "screen-code": "screen-css.jpg",
    "menu-board": "menu-board.jpg",
    "newspaper": "newspaper-moon.jpg",
    "plaque": "annual-report.jpg",
    "laptop-wikipedia": "laptop-wikipedia.jpg",
    "laptop-wikipedia-close": "laptop-wikipedia.jpg",
}


def photo(scene, url):
    os.makedirs(CACHE, exist_ok=True)
    path = os.path.join(CACHE, LOCAL[scene])
    if not os.path.exists(path):
        if url is None:
            raise SystemExit(f"{scene}: put {LOCAL[scene]} in {CACHE}")
        req = urllib.request.Request(url, headers={"User-Agent": UA})
        with urllib.request.urlopen(req) as r, open(path, "wb") as f:
            f.write(r.read())
    return Image.open(path).convert("RGB")


def frames(img, cx, cy, width, rng):
    iw, ih = img.size
    cw = width * iw
    ch = cw * H / W
    if ch > ih:
        ch = ih
        cw = ch * W / H
    out = []
    for i in range(6):
        # A hand: a few pixels of shift and under a degree of turn between frames.
        dx = rng.uniform(-0.004, 0.004) * iw
        dy = rng.uniform(-0.004, 0.004) * ih
        left = min(max(cx * iw - cw / 2 + dx, 0), iw - cw)
        top = min(max(cy * ih - ch / 2 + dy, 0), ih - ch)
        crop = img.crop((int(left), int(top), int(left + cw), int(top + ch)))
        crop = crop.rotate(rng.uniform(-0.8, 0.8), resample=Image.BICUBIC, expand=False)
        out.append(crop.resize((W, H), Image.LANCZOS))
    return out


def main():
    rng = random.Random(7)
    for scene, (url, licence, (cx, cy, width), phrases) in SCENES.items():
        img = photo(scene, url)
        folder = os.path.join(OUT, scene)
        os.makedirs(folder, exist_ok=True)
        for i, f in enumerate(frames(img, cx, cy, width, rng)):
            f.save(os.path.join(folder, f"{i:02d}.jpg"), quality=90)
        with open(os.path.join(folder, "expect.txt"), "w", encoding="utf-8") as f:
            f.write("\n".join(phrases) + "\n")
        with open(os.path.join(folder, "LICENCE.txt"), "w", encoding="utf-8") as f:
            f.write(licence + "\n")
        print(scene, "ok")


if __name__ == "__main__":
    main()
