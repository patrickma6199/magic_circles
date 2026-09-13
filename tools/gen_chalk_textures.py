"""Generates the colored chalk item textures (purple/red/green), matching gold_chalk.png's
diagonal-stick shape exactly - only the two "band" pixels near each tip change color, same as
how gold_chalk itself differs from plain chalk.

Run from anywhere with `python tools/gen_chalk_textures.py` (requires Pillow).
"""
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "magiccircles", "textures", "item")

WHITE_TIP = (255, 253, 248, 255)
HIGHLIGHT = (235, 232, 225, 255)
SHADOW = (220, 216, 208, 255)
SHADOW_TIP = (200, 196, 188, 255)

# Every non-band pixel of the stick, shared by every color - copied from gold_chalk.png.
BASE_PIXELS = {
    (11, 3): WHITE_TIP,
    (10, 5): HIGHLIGHT, (9, 6): HIGHLIGHT, (10, 6): SHADOW,
    (8, 7): HIGHLIGHT, (9, 7): SHADOW,
    (7, 8): HIGHLIGHT, (8, 8): SHADOW,
    (6, 9): HIGHLIGHT, (7, 9): SHADOW,
    (5, 10): HIGHLIGHT, (6, 10): SHADOW,
    (4, 11): HIGHLIGHT, (5, 11): SHADOW,
    (3, 12): SHADOW_TIP,
}

# The two pixels that carry this chalk's color band, near the top and bottom tips.
BAND_POSITIONS = [(11, 4), (4, 12)]

COLORS = {
    "purple_chalk": (184, 115, 255, 255),
    "red_chalk": (255, 89, 89, 255),
    "green_chalk": (115, 255, 128, 255),
    "black_chalk": (60, 20, 80, 255),
}


def main():
    for name, band_color in COLORS.items():
        img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        for pos, color in BASE_PIXELS.items():
            img.putpixel(pos, color)
        for pos in BAND_POSITIONS:
            img.putpixel(pos, band_color)
        img.save(os.path.join(TEX_DIR, f"{name}.png"))
        print(f"Wrote {name}.png")


if __name__ == "__main__":
    main()
