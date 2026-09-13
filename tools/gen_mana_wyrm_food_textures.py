"""Generates the item textures for raw_mana_wyrm and cooked_mana_wyrm (16x16),
following the same silhouette vanilla raw/cooked fish items use (a simple curved
fish shape) but recolored to match ManaWyrmEntity: pale white/cyan and "glowing"
for raw, golden-brown (cooked) with the same glow kept faint around the edges.

Run from anywhere with `python tools/gen_mana_wyrm_food_textures.py` (requires Pillow).
"""
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "magiccircles", "textures", "item")
os.makedirs(TEX_DIR, exist_ok=True)

SIZE = 16

# A simple curved-fish silhouette: body ellipse + tail wedge + eye dot, in a 16x16 grid.
# 0 = empty, 1 = body, 2 = fin/tail, 3 = eye, 4 = belly highlight
SHAPE = [
    "................",
    "................",
    "................",
    "......111.......",
    "....1111112.....",
    "...311111122....",
    "..41111111122...",
    "..41111111122...",
    "...311111122....",
    "....1111112.....",
    "......111.......",
    "................",
    "................",
    "................",
    "................",
    "................",
]


def make(body, belly, fin, eye, glow):
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    for y, row in enumerate(SHAPE):
        for x, c in enumerate(row):
            if c == "1":
                color = body
            elif c == "2":
                color = fin
            elif c == "3":
                color = belly
            elif c == "4":
                color = glow
            else:
                continue
            img.putpixel((x, y), color + (255,))
    # eye pixel, always on top of the body near the head
    img.putpixel((7, 5), eye + (255,))
    return img


def main():
    # Raw: pale white/cyan, glowing - matches ManaWyrmEntity's own recolor.
    raw = make(
        body=(214, 240, 238),
        belly=(190, 225, 225),
        fin=(230, 250, 248),
        eye=(120, 200, 195),
        glow=(200, 255, 240),
    )
    raw.save(os.path.join(TEX_DIR, "raw_mana_wyrm.png"))

    # Cooked: warm golden-brown, same silhouette, glow dimmed toward amber.
    cooked = make(
        body=(196, 138, 74),
        belly=(224, 176, 108),
        fin=(158, 104, 52),
        eye=(90, 56, 30),
        glow=(232, 196, 120),
    )
    cooked.save(os.path.join(TEX_DIR, "cooked_mana_wyrm.png"))

    print("Wrote raw_mana_wyrm.png and cooked_mana_wyrm.png")


if __name__ == "__main__":
    main()
