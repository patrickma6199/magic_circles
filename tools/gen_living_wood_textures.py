"""Generates every Living Wood texture - the log and the whole family of wooden blocks made from it:

  block/living_wood_log.png, living_wood_log_top.png                 bark, and the cut end
  block/stripped_living_wood_log.png, stripped_living_wood_log_top.png
  block/living_wood_planks.png
  block/living_wood_door_top.png, living_wood_door_bottom.png, item/living_wood_door.png
  block/living_wood_trapdoor.png

Everything tiles seamlessly, above all the log: stacked logs are meant to read as one continuous
trunk, never a pile of separate blocks. So every pattern here is built only from waves that repeat a
whole number of times across the 16 pixels - the bark's ridges, the grain, the wander of the gold
veins that glow through it - which makes the top row flow into the bottom row, and the left edge
into the right, exactly as the middle rows flow into each other. The colours are the original log's:
deep sea-green bark and the gold light of the Living Wood's veins.
"""
import math
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/magiccircles/textures"
TAU = math.pi * 2.0
N = 16

BARK_DEEP = (46, 60, 49)
BARK_DARK = (58, 74, 60)
BARK = (78, 98, 78)
BARK_LIGHT = (100, 122, 96)
VEIN = (214, 178, 90)
VEIN_DIM = (168, 146, 86)
WOOD_DEEP = (92, 118, 90)
WOOD_DARK = (106, 134, 102)
WOOD = (126, 156, 118)
WOOD_LIGHT = (148, 178, 136)
TRANSPARENT = (0, 0, 0, 0)


def periodic(seed, terms=5, max_fx=3, max_fy=3):
    """Smooth noise in [-1, 1] that repeats exactly every 16 pixels both ways - integer frequencies only."""
    rng = random.Random(seed)
    waves = [(rng.randint(0, max_fx), rng.randint(0, max_fy), rng.uniform(0, TAU), rng.uniform(0.4, 1.0))
             for _ in range(terms)]
    waves = [w for w in waves if w[0] or w[1]] or [(1, 1, 0.0, 1.0)]
    total = sum(w[3] for w in waves)

    def noise(x, y):
        return sum(a * math.sin(TAU * (fx * x + fy * y) / N + ph) for fx, fy, ph, a in waves) / total
    return noise


def pick(value, palette):
    """Maps a value in [-1, 1] onto a short dark-to-light palette - hard steps, the way pixel art shades."""
    t = (value + 1.0) / 2.0
    return palette[min(len(palette) - 1, max(0, int(t * len(palette))))]


def image(pixels):
    img = Image.new("RGBA", (N, N))
    for y in range(N):
        for x in range(N):
            colour = pixels(x, y)
            img.putpixel((x, y), colour if len(colour) == 4 else (*colour, 255))
    return img


def vein_path(seed, count):
    """Gold veins winding up the trunk - each drifts side to side on a wave that completes a whole
    number of swings per block, so where one block's vein leaves the top the next block's picks it up."""
    rng = random.Random(seed)
    veins = []
    for _ in range(count):
        x0 = rng.uniform(0, N)
        swing = rng.uniform(0.9, 1.7)
        phase = rng.uniform(0, TAU)
        cycles = rng.choice((1, 1, 2))
        veins.append(lambda y, x0=x0, swing=swing, phase=phase, cycles=cycles:
                     int(round(x0 + swing * math.sin(TAU * cycles * y / N + phase))) % N)
    return veins


# ----------------------------------------------------------------------------------- the log

def log_side():
    ridges = periodic(11, terms=3, max_fx=3, max_fy=0)     # ridges run straight up the trunk
    texture = periodic(12, terms=5, max_fx=3, max_fy=2)    # gentle unevenness along them
    speck = periodic(13, terms=6, max_fx=3, max_fy=3)
    veins = vein_path(14, 2)
    palette = (BARK_DEEP, BARK_DARK, BARK, BARK, BARK_LIGHT)

    def pixel(x, y):
        for i, vein in enumerate(veins):
            if vein(y) == x:
                return VEIN if i == 0 else VEIN_DIM
        value = 0.65 * ridges(x, y) + 0.25 * texture(x, y) + 0.1 * speck(x, y)
        return pick(value, palette)
    return image(pixel)


def rings(x, y):
    """Distance from the centre of a cut end - a little squared off, the way vanilla's rings are."""
    dx, dy = abs(x - 7.5), abs(y - 7.5)
    return 0.55 * max(dx, dy) + 0.45 * math.hypot(dx, dy)


def log_top(with_bark):
    wobble = periodic(21, terms=4)

    def pixel(x, y):
        r = rings(x, y) + 0.35 * wobble(x, y)
        if with_bark and r > 7.0:
            return BARK_DARK if (x + y) % 3 else BARK
        if r < 1.1:
            return VEIN
        if 3.6 < r < 4.4:
            return VEIN_DIM
        ring = int(r * 1.4) % 3
        return (WOOD_DARK, WOOD, WOOD_LIGHT)[ring] if with_bark or r < 7.0 else WOOD_DARK
    return image(pixel)


def stripped_side():
    grain = periodic(31, terms=3, max_fx=3, max_fy=0)
    texture = periodic(32, terms=5, max_fx=3, max_fy=2)
    veins = vein_path(33, 1)
    palette = (WOOD_DARK, WOOD, WOOD, WOOD_LIGHT)

    def pixel(x, y):
        if veins[0](y) == x:
            return VEIN_DIM
        return pick(0.7 * grain(x, y) + 0.3 * texture(x, y), palette)
    return image(pixel)


# ----------------------------------------------------------------------------------- made from it

def planks():
    """Four boards, four pixels tall, each seamed at its own place - grain running along them."""
    grain = periodic(41, terms=4, max_fx=3, max_fy=0)
    seams = (3, 11, 6, 14)
    palette = (WOOD_DARK, WOOD, WOOD, WOOD_LIGHT)

    def pixel(x, y):
        board, row = divmod(y, 4)
        if row == 3:
            return WOOD_DEEP
        if x == seams[board]:
            return WOOD_DEEP
        shade = grain(x + board * 5, board) + (0.25 if row == 0 else 0.0)
        return pick(shade, palette)
    return image(pixel)


def door(part):
    """Upright boards in a frame; the top half has a leaf-shaped window, the bottom a gold vine and a handle."""
    grain = periodic(51 if part == "top" else 52, terms=4, max_fx=0, max_fy=3)

    def leaf_window(x, y):
        # A pointed leaf, eight tall, centred - wider in the middle, tapering to both tips.
        if not 3 <= y <= 11:
            return False
        half_width = 2.6 * math.sin(math.pi * (y - 3) / 8.0)
        return abs(x - 7.5) <= half_width

    def pixel(x, y):
        edge = x in (0, 15) or (part == "top" and y == 0) or (part == "bottom" and y == 15)
        if edge:
            return WOOD_DEEP
        if part == "top":
            if leaf_window(x, y):
                return TRANSPARENT
            if leaf_window(x - 1, y) or leaf_window(x + 1, y) or leaf_window(x, y - 1) or leaf_window(x, y + 1):
                return VEIN_DIM
        else:
            if 12 <= x <= 13 and 1 <= y <= 2:
                return VEIN
            if abs((x - 2) - (y - 4) * 0.8) < 0.6 and 4 <= y <= 14:
                return VEIN_DIM
        if x % 5 == 0:
            return WOOD_DARK
        return pick(grain(x, y), (WOOD_DARK, WOOD, WOOD, WOOD_LIGHT))
    return image(pixel)


def door_item():
    top, bottom = door("top"), door("bottom")
    icon = Image.new("RGBA", (N, N), TRANSPARENT)
    # Half-width door, the full height of the icon - the two halves squeezed into one picture.
    for y in range(N):
        source = top if y < 8 else bottom
        sy = (y % 8) * 2
        for x in range(4, 12):
            icon.putpixel((x, y), source.getpixel(((x - 4) * 2, sy)))
    return icon


def trapdoor():
    grain = periodic(61, terms=4, max_fx=3, max_fy=0)
    holes = {(4, 4), (11, 4), (4, 11), (11, 11)}

    def pixel(x, y):
        if x in (0, 15) or y in (0, 15):
            return WOOD_DEEP
        for hx, hy in holes:
            if abs(x - hx) + abs(y - hy) <= 1:
                return TRANSPARENT
        if y in (5, 10):
            return WOOD_DARK
        if x == 1 and y in (3, 12):
            return VEIN
        return pick(grain(x, y), (WOOD_DARK, WOOD, WOOD, WOOD_LIGHT))
    return image(pixel)


def main():
    block = ROOT / "block"
    outputs = {
        block / "living_wood_log.png": log_side(),
        block / "living_wood_log_top.png": log_top(with_bark=True),
        block / "stripped_living_wood_log.png": stripped_side(),
        block / "stripped_living_wood_log_top.png": log_top(with_bark=False),
        block / "living_wood_planks.png": planks(),
        block / "living_wood_door_top.png": door("top"),
        block / "living_wood_door_bottom.png": door("bottom"),
        block / "living_wood_trapdoor.png": trapdoor(),
        ROOT / "item" / "living_wood_door.png": door_item(),
    }
    for path, img in outputs.items():
        img.save(path)
        print(f"wrote {path.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
