"""Generates the 18x18 status-effect icons for Blessed by the Wellspring and Marked by the Dark.

Minecraft draws mob-effect icons from assets/<modid>/textures/mob_effect/<effect>.png at 18x18 -
the same size vanilla's own effect sprites use. Without these the inventory shows a missing-texture
square next to the effect name.

Run from anywhere with `python tools/gen_effect_icons.py` (requires Pillow).
"""

from PIL import Image
import os

SIZE = 18
OUT_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "src", "main", "resources", "assets", "magiccircles", "textures", "mob_effect",
)


def blend(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(4))


def radial(img, center, radius, inner, outer, falloff=1.0):
    """Paints a soft round glow, blending inner->outer from the center outwards."""
    px = img.load()
    cx, cy = center
    for y in range(SIZE):
        for x in range(SIZE):
            d = ((x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2) ** 0.5
            if d > radius:
                continue
            t = min(1.0, (d / radius) ** falloff)
            existing = px[x, y]
            painted = blend(inner, outer, t)
            # simple source-over so later shapes sit on earlier ones
            alpha = painted[3] / 255
            px[x, y] = (
                round(painted[0] * alpha + existing[0] * (1 - alpha)),
                round(painted[1] * alpha + existing[1] * (1 - alpha)),
                round(painted[2] * alpha + existing[2] * (1 - alpha)),
                max(existing[3], painted[3]),
            )


def sparkle(img, x, y, color, arm=2):
    """A small four-pointed glint."""
    px = img.load()
    for i in range(-arm, arm + 1):
        fade = 1.0 - abs(i) / (arm + 1)
        c = (color[0], color[1], color[2], round(color[3] * fade))
        for (sx, sy) in ((x + i, y), (x, y + i)):
            if 0 <= sx < SIZE and 0 <= sy < SIZE:
                old = px[sx, sy]
                a = c[3] / 255
                px[sx, sy] = (
                    round(c[0] * a + old[0] * (1 - a)),
                    round(c[1] * a + old[1] * (1 - a)),
                    round(c[2] * a + old[2] * (1 - a)),
                    max(old[3], c[3]),
                )


def blessed():
    """A bright wellspring droplet - pale violet core, soft lilac halo, a couple of glints."""
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    radial(img, (9, 10), 8.0, (0xE8, 0xDD, 0xFF, 255), (0x8A, 0x6C, 0xE0, 0), 1.4)
    radial(img, (9, 10), 5.0, (0xFF, 0xFF, 0xFF, 255), (0xB8, 0x9C, 0xFF, 200), 1.2)
    # droplet tip
    px = img.load()
    for i, w in enumerate((0, 0, 1, 1)):
        y = 2 + i
        for x in range(9 - w, 9 + w + 1):
            px[x, y] = (0xEC, 0xE4, 0xFF, 255)
    sparkle(img, 14, 5, (0xFF, 0xFF, 0xFF, 230), 2)
    sparkle(img, 4, 13, (0xD8, 0xC8, 0xFF, 200), 1)
    return img


def marked():
    """A dark sigil - deep violet void with a ragged pale ring, like something looking back."""
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    radial(img, (9, 9), 8.5, (0x4A, 0x18, 0x66, 255), (0x18, 0x04, 0x28, 0), 1.6)
    radial(img, (9, 9), 5.5, (0x12, 0x02, 0x1C, 255), (0x2E, 0x0A, 0x44, 255), 1.0)
    px = img.load()
    # a broken ring of cold light around the void
    ring = [
        (9, 2), (11, 3), (13, 5), (14, 7),
        (13, 11), (11, 13), (9, 14), (7, 13),
        (5, 11), (4, 9), (5, 6), (7, 3),
    ]
    for (x, y) in ring:
        px[x, y] = (0xC8, 0x9C, 0xFF, 255)
    # two faint eyes in the dark
    px[7, 8] = (0xE8, 0xD0, 0xFF, 235)
    px[11, 8] = (0xE8, 0xD0, 0xFF, 235)
    return img


def deathsight():
    """An open eye in a purple haze - green iris and red-veined white, the split ring's own colours."""
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    radial(img, (9, 9), 8.5, (0x3A, 0x10, 0x4A, 210), (0x10, 0x04, 0x18, 0), 1.4)
    px = img.load()
    for y in range(SIZE):
        for x in range(SIZE):
            dx = (x + 0.5 - 9) / 7.5
            dy = y + 0.5 - 9
            if abs(dx) < 1 and abs(dy) <= 3.6 * (1 - dx * dx):
                px[x, y] = (0xE8, 0xE0, 0xD8, 255)
    radial(img, (9, 9), 3.2, (0x3F, 0xB8, 0x5A, 255), (0x1E, 0x6A, 0x30, 255), 1.0)
    radial(img, (9, 9), 1.4, (0x05, 0x02, 0x08, 255), (0x05, 0x02, 0x08, 255), 1.0)
    for (x, y) in ((3, 9), (4, 8), (14, 9), (13, 10), (5, 10)):
        px[x, y] = (0xB0, 0x22, 0x2A, 255)
    return img


def indebted():
    """A gold coin with a keyhole struck through it - something owed, and not yet paid."""
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    radial(img, (9, 9), 8.5, (0x5A, 0x44, 0x10, 170), (0x20, 0x16, 0x04, 0), 1.4)
    radial(img, (9, 9), 6.0, (0xFF, 0xE2, 0x8A, 255), (0xC9, 0x96, 0x2E, 255), 1.1)
    px = img.load()
    for y in range(SIZE):
        for x in range(SIZE):
            d = ((x + 0.5 - 9) ** 2 + (y + 0.5 - 9) ** 2) ** 0.5
            if 5.2 <= d <= 6.0:
                px[x, y] = (0x8A, 0x62, 0x18, 255)
    for (x, y) in ((7, 5), (8, 5), (9, 5), (10, 5), (7, 6), (8, 6), (9, 6), (10, 6),
                   (8, 7), (9, 7), (8, 8), (9, 8), (8, 9), (9, 9), (8, 10), (9, 10), (8, 11), (9, 11)):
        px[x, y] = (0x2A, 0x1C, 0x08, 255)
    sparkle(img, 13, 4, (0xFF, 0xFF, 0xE0, 230), 2)
    return img


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    for name, img in (("blessed_by_wellspring", blessed()), ("marked_by_the_dark", marked()),
                      ("deathsight", deathsight()), ("indebted", indebted())):
        path = os.path.join(OUT_DIR, f"{name}.png")
        img.save(path)
        print(f"wrote {path}")


if __name__ == "__main__":
    main()
