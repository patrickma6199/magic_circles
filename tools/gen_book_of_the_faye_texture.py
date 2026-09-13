"""Generates the 3D model texture for the Book of the Faye's own *placed, floating, open* form
(see block/BookOfTheFayeBlockEntity.java, client/BookOfTheFayeBlockEntityRenderer.java) - reuses
vanilla's own BookModel (the same one the Enchanting Table's floating book, and a Lectern's open
book, both already use) rather than authoring a new mesh, the same "borrow the model, swap the
texture" approach this mod already uses for Pixie/Mana Wyrm. The item form (held/in-inventory/on
the ground) looks closed instead - see gen_book_of_the_faye_closed_icon.py for that one; only the
placed block uses this texture at all.

Run from anywhere with `python tools/gen_book_of_the_faye_texture.py` (requires Pillow).
Starts from the actual vanilla enchanting_table_book.png (extracted once into this repo's own
tools/ directory as a known-good UV reference) and recolors it in place, preserving every pixel's
own luminance/shading - so the model's real cube-UV unwrap (which this script never needs to
understand) stays exactly correct, only the color changes: the cover (the texture's own top half,
y<16) becomes a deep mossy green with a brown spine rather than vanilla's plain brown wood, and
the pages (the bottom half) stay a pale cream/parchment rather than a colder white, matching this
mod's own established palette elsewhere.
"""
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOURCE = os.path.join(ROOT, "tools", "vanilla_enchanting_table_book.png")
TEX_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "magiccircles", "textures", "entity")
os.makedirs(TEX_DIR, exist_ok=True)

COVER_DARK = (28, 66, 46)
COVER_LIGHT = (58, 120, 84)
SPINE_DARK = (46, 28, 14)
SPINE_LIGHT = (92, 58, 30)
PAGE_DARK = (196, 176, 138)
PAGE_LIGHT = (238, 224, 190)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def main():
    img = Image.open(SOURCE).convert("RGBA")
    width, height = img.size
    out = Image.new("RGBA", (width, height))

    for y in range(height):
        for x in range(width):
            r, g, b, a = img.getpixel((x, y))
            luminance = (r + g + b) / (3 * 255.0)
            if r < 8 and g < 8 and b < 8:
                out.putpixel((x, y), (0, 0, 0, a))
                continue

            if y < 10:
                # The cover row (left_lid/right_lid/seam, texOffs y=0) - the seam sits at x 12-14,
                # a narrow brown spine strip between the two covers.
                if 12 <= x < 14:
                    color = lerp(SPINE_DARK, SPINE_LIGHT, luminance)
                else:
                    color = lerp(COVER_DARK, COVER_LIGHT, luminance)
            else:
                color = lerp(PAGE_DARK, PAGE_LIGHT, luminance)
            out.putpixel((x, y), color + (a,))

    tex_path = os.path.join(TEX_DIR, "book_of_the_faye_open.png")
    out.save(tex_path)
    print(f"Wrote {tex_path}")


if __name__ == "__main__":
    main()
