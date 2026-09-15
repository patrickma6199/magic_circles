"""Generates the two 16x16 poltergeist ability icons shown on the ghost HUD (see client/GhostHud.java):

  textures/gui/poltergeist_rain.png      - a storm cloud shedding rain (Call the Rain)
  textures/gui/poltergeist_skeleton.png  - a bleached skull (Raise a Skeleton)

Drawn from hand-laid pixel grids rather than procedurally, so each reads cleanly at 16px.
"""
from pathlib import Path

from PIL import Image

OUT_DIR = Path(__file__).resolve().parent.parent / "src/main/resources/assets/magiccircles/textures/gui"

RAIN = [
    "................",
    "......wwww......",
    "....wwwwwwww....",
    "..wwwwwwwwwwww..",
    ".wwwwwwwwwwwwww.",
    "wwwwwwwwwwwwwwww",
    "wwwwwwwwwwwwwwww",
    "gwwwwwwwwwwwwwwg",
    ".gggggggggggggg.",
    "..dddddddddddd..",
    "................",
    "...B...B...B....",
    "..b...b...b...B.",
    ".....B...B...b..",
    "....b...b...b...",
    "................",
]
RAIN_PALETTE = {
    "w": (232, 238, 244, 255),
    "g": (184, 194, 204, 255),
    "d": (122, 134, 148, 255),
    "b": (79, 163, 232, 255),
    "B": (159, 211, 255, 255),
}

SKULL = [
    "................",
    ".....oooooo.....",
    "...oowwwwwwoo...",
    "..owwwwwwwwwwo..",
    ".owwwwwwwwwwwwo.",
    ".owwwwwwwwwwwso.",
    ".owkkkwwwwkkkso.",
    ".owkkkkwwkkkkso.",
    ".owkkkwwwwkkkso.",
    ".owwwwwkkwwwwso.",
    "..owwwwkkwwwso..",
    "...owwwwwwwso...",
    "...owkwkwkwso...",
    "...owwwwwwwso...",
    "....ooooooooo...",
    "................",
]
SKULL_PALETTE = {
    "w": (230, 225, 211, 255),
    "s": (185, 179, 162, 255),
    "k": (43, 38, 48, 255),
    "o": (110, 104, 88, 255),
}


def draw(grid: list[str], palette: dict[str, tuple[int, int, int, int]], name: str) -> None:
    assert len(grid) == 16 and all(len(row) == 16 for row in grid), f"{name}: grid must be 16x16"
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(grid):
        for x, cell in enumerate(row):
            if cell != ".":
                image.putpixel((x, y), palette[cell])
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    path = OUT_DIR / name
    image.save(path)
    print(f"wrote {path}")


if __name__ == "__main__":
    draw(RAIN, RAIN_PALETTE, "poltergeist_rain.png")
    draw(SKULL, SKULL_PALETTE, "poltergeist_skeleton.png")
