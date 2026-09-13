"""Generates the swirly, lighter-blue animated texture for the portal water fluid
(ModFluidTypes.PORTAL_WATER's still/flowing texture - see ModFluids.java).

Run from anywhere with `python tools/gen_portal_water_texture.py` (requires Pillow).
Produces a vertical filmstrip PNG (each 16x16 frame stacked top-to-bottom) plus the
accompanying .mcmeta that tells Minecraft to animate it - the standard vanilla format for
animated fluid/block textures (see e.g. water_still.png/.mcmeta).

Unlike an earlier version, every pixel is covered (no circular fade to transparent at the
corners) - the ripple is built from sine waves with periods that are whole multiples of the
16px tile, so frame N's left edge already matches its right edge (and same for top/bottom),
which is what makes adjacent fluid blocks connect into one continuous sheet instead of each
tile reading as its own separate, gap-edged blob.

A second earlier version used a busier 3-term "swirl" (three different sine frequencies all
folded together) that read more like a psychedelic whirlpool than actual water. This one uses
two much calmer, low-frequency wave trains crossing at an angle - closer to how vanilla's own
still_water.png reads (soft rolling bands drifting past each other) - plus a little per-frame
sparkle noise for the small bright glints real water's surface catches, instead of the smooth
swirl having no fine detail at all.
"""
import json
import math
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "magiccircles", "textures", "block")
os.makedirs(TEX_DIR, exist_ok=True)

FRAME_COUNT = 16
SIZE = 16
BASE = (110, 170, 255)
BRIGHT = (200, 230, 255)


def _hash_noise(x, y, frame):
    # A cheap, deterministic pseudo-random value in [0, 1) per (x, y, frame) - just enough
    # sparkle to break up the smooth ripple bands without needing an actual noise library.
    n = math.sin(x * 12.9898 + y * 78.233 + frame * 37.719) * 43758.5453
    return n - math.floor(n)


def make_frame(t, frame_index):
    img = Image.new("RGBA", (SIZE, SIZE))
    angle_offset = t * 2.0 * math.pi
    for y in range(SIZE):
        v = y / SIZE * 2.0 * math.pi
        for x in range(SIZE):
            u = x / SIZE * 2.0 * math.pi
            # Two calm, crossing wave trains (not three busy ones) - this is the actual "water"
            # part: broad, slow-moving bands rather than a tight whirlpool.
            wave1 = math.sin(u * 2 + v * 1 + angle_offset)
            wave2 = math.sin(u * 1 - v * 2 + angle_offset * 1.4 + 1.7)
            ripple = (wave1 + wave2) / 2.0
            shade = 0.5 + 0.45 * ripple
            sparkle = _hash_noise(x, y, frame_index)
            if sparkle > 0.92:
                shade = min(1.0, shade + (sparkle - 0.92) * 6.0)
            color = tuple(int(BASE[i] + (BRIGHT[i] - BASE[i]) * shade) for i in range(3))
            alpha = 210
            img.putpixel((x, y), color + (alpha,))
    return img


def main():
    strip = Image.new("RGBA", (SIZE, SIZE * FRAME_COUNT))
    for i in range(FRAME_COUNT):
        frame = make_frame(i / FRAME_COUNT, i)
        strip.paste(frame, (0, i * SIZE))

    tex_path = os.path.join(TEX_DIR, "fairy_portal_water.png")
    strip.save(tex_path)

    mcmeta = {"animation": {"frametime": 3}}
    with open(tex_path + ".mcmeta", "w") as f:
        json.dump(mcmeta, f, indent=2)

    print(f"Wrote fairy_portal_water.png ({FRAME_COUNT} frames, seamless) and its .mcmeta")


if __name__ == "__main__":
    main()
