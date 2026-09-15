"""Generates the entity texture for the Fairy Wings (see client/FairyWingsLayer, FairyFlightManager)
granted by Blessed by the Wellspring.

Run from anywhere with `python tools/gen_fairy_wings_texture.py` (requires Pillow). Starts from the
real vanilla elytra entity texture (extracted once into tools/vanilla_elytra_entity.png) so the
model's UV-mapped wing panels line up exactly, then:

  1. recolours the membrane pale and faintly warm, keeping every pixel's relative shading;
  2. grows branching roots through it - thick near the wing root, splitting and thinning outward,
     like the World Tree's own roots showing through a leaf;
  3. rims every panel in the soil browns of the Fairy Realm's own ground.

The root and soil colours are sampled from vanilla's dirt, rooted_dirt and hanging_roots textures,
which is what the Fairy Realm's topsoil is actually made of - so the wings match the ground under
the World Tree rather than an invented brown.

Deterministic: a fixed seed, so re-running produces the identical texture.
"""
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ENTITY_SOURCE = os.path.join(ROOT, "tools", "vanilla_elytra_entity.png")
ENTITY_TEX_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "magiccircles", "textures", "entity")

MEMBRANE_DARK = (214, 220, 212)
MEMBRANE_LIGHT = (252, 252, 246)

# Sampled from vanilla dirt / rooted_dirt / hanging_roots.
SOIL_DEEP = (0x59, 0x3D, 0x29)
SOIL = (0x79, 0x55, 0x3A)
SOIL_LIGHT = (0x96, 0x6C, 0x4A)
ROOT_BARK = (0x90, 0x57, 0x40)
ROOT_PALE = (0xAD, 0x7D, 0x65)

SEED = 0x5EED_F00D
# The main wing panel is only about ten pixels across, so roots have to stay sparse and thin to
# read as veins through a pale membrane rather than turning the whole wing brown.
ROOT_COUNT = 2
MAX_BRANCH_DEPTH = 2
BRANCH_CHANCE = 0.14
# A column must hold at least this many opaque pixels in a row to count as the main wing panel.
MIN_PANEL_RUN = 10


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def opaque(img, x, y):
    w, h = img.size
    return 0 <= x < w and 0 <= y < h and img.getpixel((x, y))[3] > 0


def grow_root(canvas, mask, rng, x, y, dx, length, depth):
    """A root as a wandering line: mostly downward, drifting sideways, thinning as it goes."""
    fx = float(x)
    for step in range(length):
        fx += dx + rng.uniform(-0.45, 0.45)
        y += 1
        ix = int(round(fx))
        if not opaque(mask, ix, y):
            return
        progress = step / max(1, length - 1)
        colour = lerp(SOIL_DEEP, ROOT_BARK, progress * 0.8)
        canvas.putpixel((ix, y), colour + (255,))
        # Only the trunk near the wing's base gets a pale highlight beside it, which reads as a
        # rounded root; everything further out stays a single pixel so the membrane shows around it.
        if depth == 0 and progress < 0.3 and opaque(mask, ix + 1, y):
            canvas.putpixel((ix + 1, y), ROOT_PALE + (255,))

        if depth < MAX_BRANCH_DEPTH and step > 2 and rng.random() < BRANCH_CHANCE:
            branch_dx = dx + rng.choice((-0.9, 0.9)) * rng.uniform(0.5, 1.0)
            grow_root(canvas, mask, rng, ix, y, branch_dx, max(3, (length - step) // 2), depth + 1)


def rim_panels(canvas, mask):
    """Soil-brown edging wherever an opaque pixel borders transparency."""
    w, h = mask.size
    for y in range(h):
        for x in range(w):
            if not opaque(mask, x, y):
                continue
            neighbours = [(x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)]
            if any(not opaque(mask, nx, ny) for nx, ny in neighbours):
                shade = SOIL if (x + y) % 3 else SOIL_LIGHT
                canvas.putpixel((x, y), shade + (255,))


def main():
    source = Image.open(ENTITY_SOURCE).convert("RGBA")
    width, height = source.size
    canvas = Image.new("RGBA", (width, height))

    for y in range(height):
        for x in range(width):
            r, g, b, a = source.getpixel((x, y))
            if a == 0:
                continue
            luminance = (r + g + b) / (3 * 255.0)
            canvas.putpixel((x, y), lerp(MEMBRANE_DARK, MEMBRANE_LIGHT, luminance) + (a,))

    # Roots only read as roots on the wing's broad main panel. The elytra texture also carries a few
    # one-pixel side faces, and a root seeded there ends after a single pixel - so seeds go only on
    # columns with a long unbroken vertical run, spread evenly along the top where the wing attaches.
    panel_columns = []
    for x in range(width):
        run = best = 0
        start = best_start = None
        for y in range(height):
            if opaque(source, x, y):
                if run == 0:
                    start = y
                run += 1
                if run > best:
                    best, best_start = run, start
            else:
                run = 0
        if best >= MIN_PANEL_RUN:
            panel_columns.append((x, best_start))

    rng = random.Random(SEED)
    for i in range(ROOT_COUNT):
        x, top = panel_columns[int((i + 0.5) * len(panel_columns) / ROOT_COUNT)]
        grow_root(canvas, source, rng, x, top, rng.uniform(-0.3, 0.3), height, 0)

    rim_panels(canvas, source)

    os.makedirs(ENTITY_TEX_DIR, exist_ok=True)
    dest = os.path.join(ENTITY_TEX_DIR, "fairy_wings.png")
    canvas.save(dest)
    print(f"Wrote {dest}")


if __name__ == "__main__":
    main()
