"""Regenerates the 100 magic-circle rune textures/models/blockstate (20 symbols x 5 colors).

Run from anywhere with `python tools/gen_runes.py` (requires Pillow: `pip install pillow`).
Edit POINTS/BRANCH_POOL below to change the "alphabet" the runes are drawn from, or
VARIANT_COUNT to add more - just remember to keep MagicCircleBlock.VARIANT_COUNT in sync.
Colors here must match RuneColor.java's serialized names and wisp colors.
"""
import json
import os
import random

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "magiccircles")
TEX_DIR = os.path.join(ASSETS, "textures", "block")
MODEL_DIR = os.path.join(ASSETS, "models", "block")
BLOCKSTATE_DIR = os.path.join(ASSETS, "blockstates")
os.makedirs(TEX_DIR, exist_ok=True)
os.makedirs(MODEL_DIR, exist_ok=True)
os.makedirs(BLOCKSTATE_DIR, exist_ok=True)

VARIANT_COUNT = 20

# A small "rune lattice" - every symbol is a vertical stave plus a random subset of these
# branch strokes, which is roughly how real runic alphabets (Futhark, etc.) are built.
POINTS = {
    "top": (8, 2),
    "upperMid": (8, 6),
    "mid": (8, 8),
    "lowerMid": (8, 10),
    "bottom": (8, 13),
    "topLeft": (3, 3),
    "topRight": (13, 3),
    "midLeft": (3, 8),
    "midRight": (13, 8),
    "botLeft": (4, 13),
    "botRight": (12, 13),
}

BRANCH_POOL = [
    ("top", "topLeft"), ("top", "topRight"),
    ("upperMid", "topLeft"), ("upperMid", "topRight"),
    ("mid", "midLeft"), ("mid", "midRight"),
    ("lowerMid", "botLeft"), ("lowerMid", "botRight"),
    ("bottom", "botLeft"), ("bottom", "botRight"),
    ("midLeft", "midRight"),
]

# One palette per RuneColor - keyed by the block-state value name (RuneColor.getSerializedName()),
# with a texture-filename prefix ("" for blue, the default/first color, to keep old filenames stable).
PALETTES = {
    "blue": {"prefix": "", "glow": (140, 170, 255, 90), "core": (245, 248, 255, 255), "tip": (255, 255, 255, 255)},
    "gold": {"prefix": "gold_", "glow": (255, 195, 60, 110), "core": (255, 245, 210, 255), "tip": (255, 230, 150, 255)},
    "purple": {"prefix": "purple_", "glow": (170, 90, 255, 100), "core": (235, 210, 255, 255), "tip": (210, 150, 255, 255)},
    "red": {"prefix": "red_", "glow": (255, 80, 80, 100), "core": (255, 220, 220, 255), "tip": (255, 130, 130, 255)},
    "green": {"prefix": "green_", "glow": (90, 255, 110, 100), "core": (220, 255, 225, 255), "tip": (150, 255, 165, 255)},
    "black": {"prefix": "black_", "glow": (90, 15, 130, 130), "core": (60, 10, 90, 255), "tip": (140, 40, 190, 255)},
}


def draw_line(d, a, b, glow, core):
    pa, pb = POINTS[a], POINTS[b]
    d.line([pa, pb], fill=glow, width=3)
    d.line([pa, pb], fill=core, width=1)


def gen_rune(index, palette):
    # Seeded per index (not per palette) so both colors of a given symbol share one shape.
    rng = random.Random(1000 + index)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    glow, core, tip_color = palette["glow"], palette["core"], palette["tip"]

    draw_line(d, "top", "bottom", glow, core)

    branch_count = rng.randint(2, 4)
    branches = rng.sample(BRANCH_POOL, branch_count)
    for a, b in branches:
        draw_line(d, a, b, glow, core)

    if rng.random() < 0.35:
        cx, cy = POINTS["mid"]
        r = 1
        d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=core, width=1)

    # Every variant marks the same anchor point as its "tip" - MagicCircleBlockEntity
    # spawns particles there regardless of which symbol got picked.
    tip = POINTS["top"]
    d.point(tip, fill=tip_color)
    d.point((tip[0], tip[1] + 1), fill=tip_color[:3] + (200,))

    return img


def main():
    blockstate = {"variants": {}}

    for color_name, palette in PALETTES.items():
        prefix = palette["prefix"]
        for i in range(VARIANT_COUNT):
            img = gen_rune(i, palette)
            tex_name = f"magic_circle_{prefix}{i}"
            img.save(os.path.join(TEX_DIR, f"{tex_name}.png"))

            model = {
                "ambientocclusion": False,
                "textures": {
                    "particle": f"magiccircles:block/{tex_name}",
                    "rune": f"magiccircles:block/{tex_name}",
                },
                "elements": [
                    {
                        "from": [0, 0, 0],
                        "to": [16, 0.5, 16],
                        "faces": {
                            "up": {"uv": [0, 0, 16, 16], "texture": "#rune"}
                        },
                    }
                ],
            }
            with open(os.path.join(MODEL_DIR, f"{tex_name}.json"), "w") as f:
                json.dump(model, f, indent=2)

            blockstate["variants"][f"color={color_name},variant={i}"] = {"model": f"magiccircles:block/{tex_name}"}

    with open(os.path.join(BLOCKSTATE_DIR, "magic_circle.json"), "w") as f:
        json.dump(blockstate, f, indent=2)

    print(f"Wrote {VARIANT_COUNT * len(PALETTES)} rune textures/models and the blockstate.")


if __name__ == "__main__":
    main()
