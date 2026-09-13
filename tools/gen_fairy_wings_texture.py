"""Generates the entity texture for the Fairy Wings (see client/FairyWingsLayer, FairyFlightManager)
granted by Blessed by the Wellspring.

Run from anywhere with `python tools/gen_fairy_wings_texture.py` (requires Pillow). Starts from the
real vanilla elytra entity texture (extracted once into this repo's own tools/ directory, same as
every other recolored asset in this mod) and recolors it white/off-white with a soft blue-tinted
shading, preserving every pixel's own shape/UV layout and relative luminance - so the model's real
UV-mapped wing panels line up exactly like vanilla's, just white instead of vanilla's leather-brown.

There's no item texture here - Fairy Wings aren't an item at all (see FairyFlightManager's own doc
comment for why), just a rendering effect layered over whatever the player is actually wearing.
"""
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ENTITY_SOURCE = os.path.join(ROOT, "tools", "vanilla_elytra_entity.png")
ENTITY_TEX_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "magiccircles", "textures", "entity")
os.makedirs(ENTITY_TEX_DIR, exist_ok=True)

FEATHER_DARK = (200, 210, 230)
FEATHER_LIGHT = (255, 255, 255)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def main():
    img = Image.open(ENTITY_SOURCE).convert("RGBA")
    width, height = img.size
    out = Image.new("RGBA", (width, height))

    for y in range(height):
        for x in range(width):
            r, g, b, a = img.getpixel((x, y))
            if a == 0:
                continue
            luminance = (r + g + b) / (3 * 255.0)
            color = lerp(FEATHER_DARK, FEATHER_LIGHT, luminance)
            out.putpixel((x, y), color + (a,))

    dest = os.path.join(ENTITY_TEX_DIR, "fairy_wings.png")
    out.save(dest)
    print(f"Wrote {dest}")


if __name__ == "__main__":
    main()
