"""Generates the textures for Glitter Weed / Glitter Weed Sac (see block/GlitterWeedBlock,
block/GlitterWeedPlantBlock, registry/ModBlocks#GLITTER_WEED_SAC) - a red reskin of vanilla Kelp
(tip + plant body) and Shroomlight, planted along worldgen/WellspringOcean's own seabed.

Run from anywhere with `python tools/gen_glitter_weed_texture.py` (requires Pillow). Starts from
the real vanilla textures (extracted once into this repo's own tools/ directory, same as every
other recolored asset in this mod) and recolors them, preserving every pixel's own shape/UV layout
and relative luminance.

vanilla_kelp.png/vanilla_kelp_plant.png are each a 16x320 *animation strip* (20 stacked 16x16
frames, one per sway phase) - vanilla's own kelp swaying is driven by that strip plus a matching
.mcmeta (just {"animation": {"frametime": 2}} - extracted alongside the PNGs into this same tools/
directory). An earlier version of this script cropped down to a single static frame instead of
also copying that .mcmeta, on the theory that the missing .mcmeta was itself the bug - it wasn't;
per an explicit follow-up request, Glitter Weed is *meant* to sway just like real Kelp, so this
version keeps the full strip and writes the matching .mcmeta next to each recolored texture too.
"""
import json
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "magiccircles", "textures", "block")
os.makedirs(TEX_DIR, exist_ok=True)

WEED_DARK = (90, 10, 10)
WEED_LIGHT = (220, 40, 30)

SAC_DARK = (140, 20, 15)
SAC_LIGHT = (255, 210, 150)

ANIMATION_MCMETA = {"animation": {"frametime": 2}}


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def recolor(source_path, dest_path, dark, light, animated=False):
    img = Image.open(source_path).convert("RGBA")
    width, height = img.size
    out = Image.new("RGBA", (width, height))

    for y in range(height):
        for x in range(width):
            r, g, b, a = img.getpixel((x, y))
            if a == 0:
                continue
            luminance = (r + g + b) / (3 * 255.0)
            color = lerp(dark, light, luminance)
            out.putpixel((x, y), color + (a,))

    out.save(dest_path)
    print(f"Wrote {dest_path}")

    if animated:
        mcmeta_path = dest_path + ".mcmeta"
        with open(mcmeta_path, "w") as f:
            json.dump(ANIMATION_MCMETA, f, indent=2)
        print(f"Wrote {mcmeta_path}")


def main():
    recolor(os.path.join(ROOT, "tools", "vanilla_kelp.png"), os.path.join(TEX_DIR, "glitter_weed.png"), WEED_DARK, WEED_LIGHT, animated=True)
    recolor(os.path.join(ROOT, "tools", "vanilla_kelp_plant.png"), os.path.join(TEX_DIR, "glitter_weed_plant.png"), WEED_DARK, WEED_LIGHT, animated=True)
    recolor(os.path.join(ROOT, "tools", "vanilla_shroomlight.png"), os.path.join(TEX_DIR, "glitter_weed_sac.png"), SAC_DARK, SAC_LIGHT)


if __name__ == "__main__":
    main()
