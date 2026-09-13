"""Generates the animated, color-cycling texture for the Wellspring's water
(ModFluidTypes.WELLSPRING_WATER's still/flowing texture - see ModFluids.java).

Run from anywhere with `python tools/gen_wellspring_water_texture.py` (requires Pillow).
Produces a vertical filmstrip PNG (each 16x16 frame stacked top-to-bottom) plus the
accompanying .mcmeta - same format/approach as gen_portal_water_texture.py (seamless swirl
built from whole-tile-period sine waves, so adjacent fluid blocks read as one sheet).

The difference from the portal water texture: instead of shading between a fixed dark/bright
pair of the same hue, each frame's base color is itself interpolated between two of six
target colors - the five RuneColor wisp colors (blue, gold, purple, red, green) plus white -
in that fixed order, looping back from green to blue. The whole strip is one full lap through
every color, so playing it back is literally "this water shifts through all the colors of
chalk, plus white," as asked for.
"""
import json
import math
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "magiccircles", "textures", "block")
os.makedirs(TEX_DIR, exist_ok=True)

FRAME_COUNT = 60
SIZE = 16

# Matches RuneColor's wispColor() Vector3f values (0-1 floats there, 0-255 here), in the same
# order that ring color combos read around the color wheel, plus white appended at the end.
COLORS = [
    (140, 184, 255),  # blue   (0.55, 0.72, 1.0)
    (255, 209, 77),   # gold   (1.0, 0.82, 0.3)
    (184, 115, 255),  # purple (0.72, 0.45, 1.0)
    (255, 89, 89),    # red    (1.0, 0.35, 0.35)
    (115, 255, 128),  # green  (0.45, 1.0, 0.5)
    (255, 255, 255),  # white
]


def lerp(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def frame_base_color(i):
    t = (i / FRAME_COUNT) * len(COLORS)
    segment = int(t) % len(COLORS)
    frac = t - int(t)
    c0 = COLORS[segment]
    c1 = COLORS[(segment + 1) % len(COLORS)]
    return lerp(c0, c1, frac)


def _hash_noise(x, y, frame):
    n = math.sin(x * 12.9898 + y * 78.233 + frame * 37.719) * 43758.5453
    return n - math.floor(n)


def make_frame(i):
    base = frame_base_color(i)
    angle_offset = (i / FRAME_COUNT) * 2.0 * math.pi
    img = Image.new("RGBA", (SIZE, SIZE))
    for y in range(SIZE):
        v = y / SIZE * 2.0 * math.pi
        for x in range(SIZE):
            u = x / SIZE * 2.0 * math.pi
            # Same calm, crossing-wave-train ripple as the portal water's own texture (see that
            # script's own doc comment) instead of a busy 3-term swirl - reads as actual moving
            # water, just recolored every frame.
            wave1 = math.sin(u * 2 + v * 1 + angle_offset)
            wave2 = math.sin(u * 1 - v * 2 + angle_offset * 1.4 + 1.7)
            ripple = (wave1 + wave2) / 2.0
            # Ripple only ever brightens toward white, never darkens toward black - keeps every
            # frame reading as "this color, glowing" rather than muddying the cycling hue.
            shade = 0.5 + 0.45 * ripple
            sparkle = _hash_noise(x, y, i)
            if sparkle > 0.92:
                shade = min(1.0, shade + (sparkle - 0.92) * 6.0)
            color = tuple(int(base[c] + (255 - base[c]) * shade * 0.35) for c in range(3))
            alpha = 210
            img.putpixel((x, y), color + (alpha,))
    return img


def main():
    strip = Image.new("RGBA", (SIZE, SIZE * FRAME_COUNT))
    for i in range(FRAME_COUNT):
        frame = make_frame(i)
        strip.paste(frame, (0, i * SIZE))

    tex_path = os.path.join(TEX_DIR, "wellspring_water.png")
    strip.save(tex_path)

    mcmeta = {"animation": {"frametime": 4}}
    with open(tex_path + ".mcmeta", "w") as f:
        json.dump(mcmeta, f, indent=2)

    print(f"Wrote wellspring_water.png ({FRAME_COUNT} frames, seamless, full color cycle) and its .mcmeta")


if __name__ == "__main__":
    main()
