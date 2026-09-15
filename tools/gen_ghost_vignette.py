"""Generates textures/misc/ghost_vignette.png - the thin white vignette a ghost sees at the edges of
the screen (see client/GhostHud.java).

Pure white; only the alpha varies. Opaque right at the screen's edge and fading to nothing within a
thin band, with a squared falloff so most of it hugs the very edge. The texture is stretched over
the whole screen, so the band is a share of the screen's width and height rather than a pixel count.
"""
from pathlib import Path

from PIL import Image

SIZE = 256
# Share of the screen, from each edge, that the vignette reaches into.
BAND = 0.08

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/magiccircles/textures/misc/ghost_vignette.png"


def alpha_at(x: int, y: int) -> int:
    u = (x + 0.5) / SIZE
    v = (y + 0.5) / SIZE
    edge = min(u, 1.0 - u, v, 1.0 - v)
    strength = max(0.0, 1.0 - edge / BAND)
    return round(255 * strength * strength)


def main() -> None:
    image = Image.new("RGBA", (SIZE, SIZE))
    pixels = image.load()
    for y in range(SIZE):
        for x in range(SIZE):
            pixels[x, y] = (255, 255, 255, alpha_at(x, y))
    OUT.parent.mkdir(parents=True, exist_ok=True)
    image.save(OUT)
    print(f"wrote {OUT}")


if __name__ == "__main__":
    main()
