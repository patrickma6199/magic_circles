"""Generates textures/entity/fairy_queen_wings.png - the Fairy Queen's own wings (see
client/QueenWingsModel.java for the two panels each wing is made of, and the UV layout below).

Where a fairy's wings are a pale membrane with the World Tree's roots showing through, hers are
regal: an ivory membrane that cools to violet toward the tips, gold veins fanning from the root with
finer cross-veins between them, a gold rim all the way round, and a scatter of white sparks. Each
panel is a hanging leaf shape - wide where it joins the body, narrowing to a point - drawn
symmetrically, so the mirrored right wing reads the same. Deterministic: a fixed seed.

UV layout (64x64), per Minecraft's cube unwrapping for a box W wide, H tall and 1 deep at (u, v):
  forewing 16x26x1 at (0, 0):   front face u 1..16, v 1..26;  back face u 18..33, v 1..26
  tail     10x16x1 at (0, 28):  front face u 1..10, v 29..44; back face u 12..21, v 29..44
The one-pixel edge faces are left transparent - the rim is painted inside the shape instead.
"""
import math
import random
from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/magiccircles/textures/entity/fairy_queen_wings.png"
SIZE = 64
SEED = 0xFAE_0A11

IVORY = (255, 248, 228)
VIOLET = (222, 206, 255)
GOLD = (214, 176, 72)
GOLD_DEEP = (168, 128, 40)
SPARK = (255, 255, 255)


def lerp(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def half_width(y, h, w, tail):
    """The leaf's half-width at row y: full at the top, a rounded belly, then a point at the foot."""
    t = y / (h - 1)
    if tail:
        shape = math.sin(math.pi * (0.15 + 0.85 * t)) ** 0.8 if t > 0.05 else 0.35 + t * 4
        shape = min(shape, 1.0) * (1.0 - t * 0.55)
    else:
        shape = (1.0 - t ** 1.7) * (0.75 + 0.25 * math.sin(math.pi * min(1.0, t * 1.4)))
    return max(0.6, shape * w / 2)


def paint_panel(img, rng, u0, v0, w, h, tail, mirror):
    cx = (w - 1) / 2
    inside = [[False] * w for _ in range(h)]
    for y in range(h):
        hw = half_width(y, h, w, tail)
        for x in range(w):
            inside[y][x] = abs(x - cx) <= hw

    def rim(x, y):
        if not inside[y][x]:
            return False
        for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
            if not (0 <= nx < w and 0 <= ny < h) or not inside[ny][nx]:
                return True
        return False

    for y in range(h):
        for x in range(w):
            if not inside[y][x]:
                continue
            t = y / (h - 1)
            colour = lerp(IVORY, VIOLET, t ** 1.3)
            # A soft sheen down the middle.
            sheen = max(0.0, 1.0 - abs(x - cx) / (w / 2)) * 0.12
            colour = lerp(colour, (255, 255, 255), sheen)
            if rim(x, y):
                colour = GOLD if (x + y) % 2 else GOLD_DEEP
            px = u0 + (w - 1 - x if mirror else x)
            img.putpixel((px, v0 + y), colour + (255,))

    # Gold veins fanning out from the root at the top centre, each wandering a little.
    vein_count = 3 if tail else 4
    for i in range(vein_count):
        spread = (i / (vein_count - 1) - 0.5) * (0.9 if tail else 1.1)
        fx = cx
        for y in range(1, h - 1):
            fx += spread * (w / h) * (1.0 - y / h) + rng.uniform(-0.25, 0.25)
            x = int(round(fx))
            if 0 <= x < w and inside[y][x] and not rim(x, y):
                shade = GOLD if y % 3 else GOLD_DEEP
                px = u0 + (w - 1 - x if mirror else x)
                img.putpixel((px, v0 + y), shade + (255,))
    # Finer cross-veins.
    for y in range(5, h - 3, 7 if tail else 8):
        hw = int(half_width(y, h, w, tail))
        for x in range(int(cx - hw) + 1, int(cx + hw)):
            if inside[y][x] and not rim(x, y) and rng.random() < 0.3:
                px = u0 + (w - 1 - x if mirror else x)
                if img.getpixel((px, v0 + y))[:3] != GOLD:
                    img.putpixel((px, v0 + y), lerp(GOLD, IVORY, 0.65) + (255,))
    # Sparks.
    for _ in range(5 if tail else 9):
        x, y = rng.randrange(w), rng.randrange(h)
        if inside[y][x] and not rim(x, y):
            px = u0 + (w - 1 - x if mirror else x)
            img.putpixel((px, v0 + y), SPARK + (255,))


def main():
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    rng = random.Random(SEED)
    # The forewing: front face, then the back face as its mirror so the veins line up through the panel.
    paint_panel(img, random.Random(SEED), 1, 1, 16, 26, tail=False, mirror=False)
    paint_panel(img, random.Random(SEED), 18, 1, 16, 26, tail=False, mirror=True)
    paint_panel(img, random.Random(SEED + 1), 1, 29, 10, 16, tail=True, mirror=False)
    paint_panel(img, random.Random(SEED + 1), 12, 29, 10, 16, tail=True, mirror=True)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    img.save(OUT)
    print(f"Wrote {OUT}")


if __name__ == "__main__":
    main()
