"""Generates the Athame item icon (see item/AthameItem) - a double-edged ritual dagger, blade
pointing both up and down from a central guard/handle (unlike a normal one-way dagger).

Run from anywhere with `python tools/gen_athame_texture.py` (requires Pillow).
"""
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "magiccircles", "textures", "item")
os.makedirs(TEX_DIR, exist_ok=True)

BLADE_LIGHT = (220, 222, 228, 255)
BLADE_DARK = (150, 153, 162, 255)
GUARD = (90, 70, 40, 255)
HANDLE = (60, 45, 28, 255)
GEM = (140, 30, 190, 255)


def main():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()

    # Upper blade (points up), tapering toward the tip at the top.
    upper = {
        7: [(7, 1), (8, 1)],
        6: [(7, 2), (8, 2)],
        5: [(7, 3), (8, 3)],
        4: [(6, 4), (7, 4), (8, 4), (9, 4)],
        3: [(6, 5), (7, 5), (8, 5), (9, 5)],
        2: [(6, 6), (7, 6), (8, 6), (9, 6)],
    }
    for y in (1, 2, 3):
        px[7, y] = BLADE_LIGHT
        px[8, y] = BLADE_DARK
    for y in (4, 5, 6):
        px[6, y] = BLADE_DARK
        px[7, y] = BLADE_LIGHT
        px[8, y] = BLADE_LIGHT
        px[9, y] = BLADE_DARK

    # Guard, crossing the blade horizontally.
    for x in range(4, 12):
        px[x, 7] = GUARD

    # Handle, short, then a gem, then the lower blade mirrors the upper one.
    for y in (8, 9):
        px[7, y] = HANDLE
        px[8, y] = HANDLE
    px[7, 10] = GEM
    px[8, 10] = GEM

    for y in (11, 12, 13):
        px[6, y] = BLADE_DARK
        px[7, y] = BLADE_LIGHT
        px[8, y] = BLADE_LIGHT
        px[9, y] = BLADE_DARK
    for y in (14,):
        px[7, y] = BLADE_LIGHT
        px[8, y] = BLADE_DARK

    dest = os.path.join(TEX_DIR, "athame.png")
    img.save(dest)
    print(f"Wrote {dest}")


if __name__ == "__main__":
    main()
