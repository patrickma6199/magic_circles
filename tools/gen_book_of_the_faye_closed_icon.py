"""Generates the closed-book 2D inventory icon for the Book of the Faye item (see
registry/ModBlocks#BOOK_OF_THE_FAYE_BLOCK's own BlockItem) - held/in-inventory/on-the-ground, the
book is meant to look closed; only once placed does it become the open, floating 3D book (see
gen_book_of_the_faye_texture.py for that one).

Run from anywhere with `python tools/gen_book_of_the_faye_closed_icon.py` (requires Pillow).
Starts from vanilla's own closed book icon (extracted once into this repo as
tools/vanilla_book_icon.png) rather than authoring new pixel art - inspecting it directly showed
only two real regions: a brown cover (with a darker brown outline right at its own border) and a
diagonal grey/white page-edge stripe. Recolors the cover's interior green and keeps that darker
border brown - "green cover, brown spine" - the page-edge stripe is left alone since it already
reads as pages.
"""
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCE = os.path.join(ROOT, "tools", "vanilla_book_icon.png")
TEX_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "magiccircles", "textures", "item")
os.makedirs(TEX_DIR, exist_ok=True)

COVER_DARK = (26, 74, 48)
COVER_LIGHT = (54, 128, 84)
SPINE_DARK = (40, 24, 12)
SPINE_LIGHT = (74, 46, 24)
# The darkest cover-outline shades in the source icon (the border right at the cover's own edge) -
# recolored brown ("the spine") rather than green, everything else brown-ish becomes the cover.
SPINE_SOURCE_SHADES = {(0x31, 0x21, 0x04), (0x16, 0x10, 0x05)}


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
            is_grey = abs(r - g) < 10 and abs(g - b) < 10 and r > 0x40
            if is_grey:
                # The page-edge stripe - leave untouched, it already reads as pages.
                out.putpixel((x, y), (r, g, b, a))
                continue
            if (r, g, b) in SPINE_SOURCE_SHADES:
                color = lerp(SPINE_DARK, SPINE_LIGHT, luminance)
            else:
                color = lerp(COVER_DARK, COVER_LIGHT, luminance)
            out.putpixel((x, y), color + (a,))

    tex_path = os.path.join(TEX_DIR, "book_of_the_faye.png")
    out.save(tex_path)
    print(f"Wrote {tex_path}")


if __name__ == "__main__":
    main()
