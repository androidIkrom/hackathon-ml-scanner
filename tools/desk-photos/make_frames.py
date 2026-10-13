"""Desk photos for the detector (live-quality plan, Task 5: "a gray bed" said at a desk).

Downloads free photos of desks (Wikimedia Commons) into tools/desk-photos/cache (not in git) and cuts three
portrait views of each, as Live's camera sees them (480 x 640), into app/src/androidTest/assets/desks/ (not in git).
androidTest DetectorPhotosTest runs the detector over them on the emulator.

Run: python -I tools/desk-photos/make_frames.py
"""
import os
import urllib.request

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
CACHE = os.path.join(HERE, "cache")
OUT = os.path.join(HERE, "..", "..", "app", "src", "androidTest", "assets", "desks")
UA = "NungilReaderTest/1.0 (test images for an accessibility app)"
W, H = 480, 640

PHOTOS = {
    "cluttered-writer-s-table": ("https://thumb.wikimedia.org/wikipedia/commons/thumb/1/1c/Cluttered_writer%27s_table.jpeg/1280px-Cluttered_writer%27s_table.jpeg?utm_source=commons.wikimedia.org&utm_campaign=imageinfo&utm_content=thumbnail", "CC BY-SA 4.0"),
    "gfp-office-desk-area": ("https://thumb.wikimedia.org/wikipedia/commons/thumb/d/d4/Gfp-office-desk-area.jpg/1280px-Gfp-office-desk-area.jpg?utm_source=commons.wikimedia.org&utm_campaign=imageinfo&utm_content=thumbnail", "Public Domain"),
    "books-pencils-laptop-and-iphone": ("https://thumb.wikimedia.org/wikipedia/commons/thumb/3/3e/Books%2C_pencils%2C_laptop%2C_and_iphone_on_a_desk_%28Unsplash%29.jpg/1280px-Books%2C_pencils%2C_laptop%2C_and_iphone_on_a_desk_%28Unsplash%29.jpg?utm_source=commons.wikimedia.org&utm_campaign=imageinfo&utm_content=thumbnail", "CC0"),
    "computer-1149148": ("https://thumb.wikimedia.org/wikipedia/commons/thumb/6/65/Computer-1149148_1920.jpg/1280px-Computer-1149148_1920.jpg?utm_source=commons.wikimedia.org&utm_campaign=imageinfo&utm_content=thumbnail", "CC0"),
    "big-desk-home-office": ("https://upload.wikimedia.org/wikipedia/commons/7/77/Big_Desk_Home_Office_Carrollton_New_Orleans.jpg?utm_source=commons.wikimedia.org&utm_campaign=imageinfo&utm_content=thumbnail_unscaled", "CC BY 2.0"),
    "anchorage-laptop-and-cola": ("https://thumb.wikimedia.org/wikipedia/commons/thumb/8/8e/Anchorage_laptop_and_cola_on_desk_%2841893905942%29.jpg/1280px-Anchorage_laptop_and_cola_on_desk_%2841893905942%29.jpg?utm_source=commons.wikimedia.org&utm_campaign=imageinfo&utm_content=thumbnail", "CC BY 2.0"),
    "desk-laptop-notebook-pen": ("https://thumb.wikimedia.org/wikipedia/commons/thumb/e/ef/Desk-laptop-notebook-pen_%2823958719819%29.jpg/1280px-Desk-laptop-notebook-pen_%2823958719819%29.jpg?utm_source=commons.wikimedia.org&utm_campaign=imageinfo&utm_content=thumbnail", "CC0"),
    "desk-laptop-notebook-table": ("https://thumb.wikimedia.org/wikipedia/commons/thumb/4/4e/Desk-laptop-notebook-table_%2823697249344%29.jpg/1280px-Desk-laptop-notebook-table_%2823697249344%29.jpg?utm_source=commons.wikimedia.org&utm_campaign=imageinfo&utm_content=thumbnail", "CC0"),
    "desk-laptop-working-technology": ("https://thumb.wikimedia.org/wikipedia/commons/thumb/0/02/Desk-laptop-working-technology_%2824299511976%29.jpg/1280px-Desk-laptop-working-technology_%2824299511976%29.jpg?utm_source=commons.wikimedia.org&utm_campaign=imageinfo&utm_content=thumbnail", "CC0"),
    "laptop-on-a-desk-in-shadow": ("https://thumb.wikimedia.org/wikipedia/commons/thumb/6/67/Laptop_on_a_desk_in_shadow.jpg/1280px-Laptop_on_a_desk_in_shadow.jpg?utm_source=commons.wikimedia.org&utm_campaign=imageinfo&utm_content=thumbnail", "CC BY 2.0"),
    "laptop-on-a-green-thumb": ("https://thumb.wikimedia.org/wikipedia/commons/thumb/7/72/Laptop_on_a_green_thumb%27s_desk_%28Unsplash%29.jpg/1280px-Laptop_on_a_green_thumb%27s_desk_%28Unsplash%29.jpg?utm_source=commons.wikimedia.org&utm_campaign=imageinfo&utm_content=thumbnail", "CC0"),
    "laptop-on-a-neat-desk": ("https://thumb.wikimedia.org/wikipedia/commons/thumb/8/8b/Laptop_on_a_neat_desk_%28Unsplash%29.jpg/1280px-Laptop_on_a_neat_desk_%28Unsplash%29.jpg?utm_source=commons.wikimedia.org&utm_campaign=imageinfo&utm_content=thumbnail", "CC0"),
    "laptop-on-desk-book-stacks": ("https://thumb.wikimedia.org/wikipedia/commons/thumb/c/c0/Laptop_on_desk_book_stacks_%28Unsplash%29.jpg/1280px-Laptop_on_desk_book_stacks_%28Unsplash%29.jpg?utm_source=commons.wikimedia.org&utm_campaign=imageinfo&utm_content=thumbnail", "CC0"),
    "ruler-and-laptop-on-a-desk": ("https://thumb.wikimedia.org/wikipedia/commons/thumb/d/d4/Ruler_and_laptop_on_a_desk_%28Unsplash%29.jpg/1280px-Ruler_and_laptop_on_a_desk_%28Unsplash%29.jpg?utm_source=commons.wikimedia.org&utm_campaign=imageinfo&utm_content=thumbnail", "CC0"),
}


def main():
    os.makedirs(CACHE, exist_ok=True)
    os.makedirs(OUT, exist_ok=True)
    for name, (url, licence) in PHOTOS.items():
        path = os.path.join(CACHE, name + ".jpg")
        if not os.path.exists(path):
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req) as r, open(path, "wb") as f:
                f.write(r.read())
        img = Image.open(path).convert("RGB")
        iw, ih = img.size
        ch = ih
        cw = min(iw, ch * W // H)
        if cw < ch * W // H:
            ch = cw * H // W
        for i, share in enumerate((0.0, 0.5, 1.0)):
            left = int((iw - cw) * share)
            top = (ih - ch) // 2
            img.crop((left, top, left + cw, top + ch)).resize((W, H), Image.LANCZOS).save(
                os.path.join(OUT, f"{name}-{i}.jpg"), quality=90)
        print(name, licence)


if __name__ == "__main__":
    main()
