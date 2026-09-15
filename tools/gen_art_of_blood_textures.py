"""Generates the Art of Blood's cover texture and the bloodied athame icon.

Both are recolours of art that already exists, so they stay in step with it:
  - art_of_blood.png     - the Faye book's own entity texture, hue-shifted to dark purple and
                           darkened, for the closed book on the shelf.
  - athame_signed.png    - the athame item icon with blood worked into the blade, used by the
                           model override whenever the knife is carrying a signature.

Run from anywhere with `python tools/gen_art_of_blood_textures.py` (requires Pillow).
"""

from PIL import Image
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "magiccircles", "textures")


def tint_purple(src_path, dest_path):
    """Drags every colour toward a deep violet while keeping the original shading."""
    img = Image.open(src_path).convert("RGBA")
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            # Luminance drives the result so the cover keeps its embossing and page edges.
            lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
            lum = lum ** 1.25  # darker overall - this is not a bright book
            px[x, y] = (
                int(30 + lum * 120),
                int(8 + lum * 40),
                int(44 + lum * 130),
                a,
            )
    img.save(dest_path)
    print(f"wrote {dest_path}")


def bloody_blade(src_path, dest_path):
    """Works red into the lighter pixels of the blade, leaving the handle alone."""
    img = Image.open(src_path).convert("RGBA")
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
            # Only the bright metal takes the stain; dark handle pixels stay as they are.
            if lum > 0.45:
                strength = min(1.0, (lum - 0.45) / 0.55)
                px[x, y] = (
                    int(r * (1 - strength) + 150 * strength),
                    int(g * (1 - strength) + 16 * strength),
                    int(b * (1 - strength) + 22 * strength),
                    a,
                )
    img.save(dest_path)
    print(f"wrote {dest_path}")


def main():
    tint_purple(
        os.path.join(ASSETS, "entity", "book_of_the_faye_open.png"),
        os.path.join(ASSETS, "entity", "art_of_blood.png"),
    )
    tint_purple(
        os.path.join(ASSETS, "item", "book_of_the_faye.png"),
        os.path.join(ASSETS, "item", "art_of_blood.png"),
    )
    bloody_blade(
        os.path.join(ASSETS, "item", "athame.png"),
        os.path.join(ASSETS, "item", "athame_signed.png"),
    )


if __name__ == "__main__":
    main()
