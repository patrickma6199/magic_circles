"""Generates the item icon for Thelia Dust (see registry/ModItems#THELIA_DUST) - a green reskin
of vanilla's own redstone dust pile icon, smelted from a Living Wood Log and crafted with plain
Chalk into Green Chalk (see data/magiccircles/recipes/thelia_dust_smelting.json and
green_chalk_from_thelia_dust.json).

Run from anywhere with `python tools/gen_thelia_dust_texture.py` (requires Pillow). Starts from
the actual vanilla redstone.png icon (extracted once into this repo's own tools/ directory) and
recolors it, preserving every pixel's own luminance/shading, the same approach every other
recolored asset in this mod already uses.
"""
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCE = os.path.join(ROOT, "tools", "vanilla_redstone_dust_icon.png")
TEX_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "magiccircles", "textures", "item")
os.makedirs(TEX_DIR, exist_ok=True)

DUST_DARK = (30, 90, 40)
DUST_LIGHT = (90, 210, 110)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def main():
    img = Image.open(SOURCE).convert("RGBA")
    width, height = img.size
    out = Image.new("RGBA", (width, height))

    for y in range(height):
        for x in range(width):
            r, g, b, a = img.getpixel((x, y))
            if a == 0:
                continue
            luminance = (r + g + b) / (3 * 255.0)
            color = lerp(DUST_DARK, DUST_LIGHT, luminance)
            out.putpixel((x, y), color + (a,))

    tex_path = os.path.join(TEX_DIR, "thelia_dust.png")
    out.save(tex_path)
    print(f"Wrote {tex_path}")


if __name__ == "__main__":
    main()
