"""Generates every fairy skin (see client/FairyRenderer.java and client/FairyQueenRenderer.java), in
the standard 64x64 player-skin layout so the player model wraps them directly - slim arms for women,
wide for men:

  textures/entity/fairy/fairy_<colour>_female.png, fairy_<colour>_male.png
      for each of the Wellspring's colours - blue, gold, purple, red, green. A woman wears her colour
      in her hair, her petal skirt and the flowers of her crown, over a leaf bodice; a man wears it
      in his tunic and his wreath's flowers, with bark belt and boots and moss in his hair.
  textures/entity/fairy/fairy_queen.png
      the Fairy Queen, in the manner of Galadriel: long pale-gold hair falling to her waist, a silver
      circlet with a star at the brow, and a white gown to the ground with long flowing sleeves.

Every part is painted face by face through box(), which knows where each face of a model box lives
on the texture, so the painting code talks about "the front of the body" rather than coordinates.
The overlay layers (hat, jacket, sleeves, trouser legs) carry what stands off the body: crowns and
circlets, hair down the back, the flare of a skirt or gown, leaf pauldrons.
"""
import random
from pathlib import Path

from PIL import Image

OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/magiccircles/textures/entity/fairy"

# base, dark, light - each fairy colour as something you could wear
COLOURS = {
    "blue": ((96, 140, 220), (62, 96, 176), (160, 194, 244)),
    "gold": ((230, 186, 70), (186, 140, 44), (248, 220, 132)),
    "purple": ((164, 106, 214), (116, 66, 170), (206, 166, 242)),
    "red": ((214, 86, 92), (160, 48, 60), (242, 156, 150)),
    "green": ((104, 170, 86), (70, 128, 60), (160, 214, 130)),
}


def box(img, u, v, w, h, d, paint):
    """Paints one model box whose texture starts at (u, v) and is w wide, h tall and d deep.
    paint(face, x, y, face_width, face_height) returns a colour, or None to leave it transparent."""
    faces = {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + d + w + d, v + d, w, h),
    }
    for face, (fx, fy, fw, fh) in faces.items():
        for y in range(fh):
            for x in range(fw):
                colour = paint(face, x, y, fw, fh)
                if colour is not None:
                    img.putpixel((fx + x, fy + y), colour if len(colour) == 4 else (*colour, 255))


def from_front(face, x, width):
    """For a side face, how many pixels back from the front edge x is (0 = right beside the face)."""
    if face == "right":
        return width - 1 - x
    if face == "left":
        return x
    return 0


# Where each face of the head starts along a ring drawn around it, so a band reads as one continuous loop.
BAND_OFFSET = {"right": 0, "front": 8, "left": 16, "back": 24}


class Painter:
    def __init__(self, seed):
        self.rng = random.Random(seed)

    def vary(self, colour, amount=0.05):
        factor = 1.0 + self.rng.uniform(-amount, amount)
        return tuple(max(0, min(255, round(c * factor))) for c in colour[:3])


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


WHITE = (252, 250, 246)


# ----------------------------------------------------------------------------------- a fairy woman

def female(colour, seed):
    base, dark, light = colour
    p = Painter(seed)
    skin, skin_shade = (242, 211, 184), (224, 186, 158)
    hair, hair_dark, hair_light = base, dark, light
    eye = (206, 150, 60) if base == COLOURS["green"][0] else (72, 158, 96)
    white, lash = (250, 250, 246), lerp(dark, (40, 30, 40), 0.4)
    blush, lips = (240, 172, 180), (208, 104, 122)
    leaf, leaf_dark = (104, 170, 86), (70, 128, 60)
    petal, petal_light, petal_edge = light, lerp(light, WHITE, 0.45), base
    pollen = (250, 214, 90)

    def hair_px(x, y):
        return p.vary(hair_light if (x + y * 3) % 7 == 0 else hair_dark if x % 3 == 0 else hair)

    def head(face, x, y, w, h):
        if face in ("top", "back"):
            return hair_px(x, y)
        if face == "bottom":
            return skin_shade
        if face in ("right", "left"):
            return hair_px(x, y) if y <= 1 or from_front(face, x, w) >= 3 else p.vary(skin, 0.03)
        if y <= 1 or (x in (0, 7) and y <= 6):
            return hair_px(x, y)
        if y == 3 and x in (1, 2, 5, 6):
            return lash
        if y == 4:
            return {1: white, 2: eye, 5: eye, 6: white}.get(x, p.vary(skin, 0.02))
        if y == 5 and x in (1, 6):
            return blush
        if y == 6 and x in (3, 4):
            return lips
        return p.vary(skin, 0.02)

    def crown(face, x, y, w, h):
        if face == "top":
            if x in (0, 7) or y in (0, 7):
                return pollen if (x + y) % 3 == 0 else (petal if (x + y) % 3 == 1 else leaf)
            return None
        if face == "bottom":
            return None
        ring = (x + BAND_OFFSET[face]) % 4
        bloom = petal if ((x + BAND_OFFSET[face]) // 4) % 2 == 0 else WHITE
        if y == 1:
            return pollen if ring == 0 else leaf if ring == 2 else bloom
        if y in (0, 2) and ring == 0:
            return bloom
        if face == "back" and y >= 2:
            return hair_px(x, y)
        if face in ("right", "left") and y >= 3 and from_front(face, x, w) >= 4:
            return hair_px(x, y)
        return None

    def body(face, x, y, w, h):
        if face == "top":
            return p.vary(leaf)
        if face == "bottom":
            return petal_light
        if face == "front" and ((y == 0 and 2 <= x <= 5) or (y == 1 and 3 <= x <= 4)):
            return p.vary(skin, 0.02)
        if y <= 4:
            return leaf_dark if (x + y) % 3 == 0 else p.vary(leaf)
        if y == 5:
            return pollen if face == "front" and x in (3, 4) else petal_edge
        colour_ = lerp(petal, petal_light, (y - 6) / 5.0)
        if y in (8, 11) and x % 2 == 0:
            colour_ = petal_edge
        return p.vary(colour_, 0.03)

    def jacket(face, x, y, w, h):
        if face == "front" and y == 1 and x in (5, 6):
            return petal if x == 5 else pollen
        if face == "back" and y <= 6:
            return hair_px(x, y)
        if face in ("front", "back", "right", "left") and y >= 9 and (x + y) % 2 == 0:
            return petal_light if y < 11 else petal
        return None

    def arm(face, x, y, w, h):
        if face == "top" or y <= 1:
            return p.vary(petal)
        if face == "bottom":
            return skin_shade
        if y == 8:
            return leaf
        return p.vary(skin, 0.02)

    def sleeve(face, x, y, w, h):
        if face == "top":
            return leaf
        if face in ("right", "left") and y == 8 and x == 1:
            return pollen
        if face in ("right", "left") and y in (7, 9) and x == 1:
            return petal_light
        return None

    def leg(face, x, y, w, h):
        if face == "top":
            return petal_light
        if face == "bottom" or y == 11:
            return petal_edge
        if y == 10:
            return p.vary(petal)
        if 2 <= y <= 9 and (x + y) % 5 == 0:
            return leaf_dark
        return p.vary(skin, 0.02)

    def anklet(face, x, y, w, h):
        if face not in ("top", "bottom") and y == 9 and x % 2 == 0:
            return WHITE
        return None

    return build(head, crown, body, jacket, arm, sleeve, leg, anklet, arm_width=3)


# ----------------------------------------------------------------------------------- a fairy man

def male(colour, seed):
    base, dark, light = colour
    p = Painter(seed)
    skin, skin_shade = (230, 190, 152), (210, 168, 130)
    hair, hair_dark, moss = (96, 72, 46), (66, 48, 30), (92, 136, 62)
    eye, white, brow = (206, 150, 60), (248, 248, 240), (66, 48, 30)
    mouth = (176, 112, 96)
    tunic, tunic_light, tunic_dark = base, light, dark
    collar = lerp(light, WHITE, 0.3)
    bark, bark_dark, gold = (107, 74, 46), (82, 56, 34), (226, 184, 72)
    trousers, vine = (62, 94, 52), (94, 138, 70)
    leaf, leaf_light = (86, 142, 64), (118, 172, 82)
    pollen = (246, 204, 70)
    sunflower, seed_brown = (244, 196, 52), (112, 74, 40)

    def hair_px(x, y):
        if (x * 3 + y) % 5 == 0:
            return p.vary(moss)
        return p.vary(hair_dark if (x + y) % 4 == 0 else hair)

    def head(face, x, y, w, h):
        if face in ("top", "back"):
            return hair_px(x, y)
        if face == "bottom":
            return skin_shade
        if face in ("right", "left"):
            return hair_px(x, y) if y <= 2 or from_front(face, x, w) >= 4 else p.vary(skin, 0.03)
        if y == 0 or (y == 1 and x not in (3, 4)):
            return hair_px(x, y)
        if y == 3 and x in (1, 2, 5, 6):
            return brow
        if y == 4:
            return {1: white, 2: eye, 5: eye, 6: white}.get(x, p.vary(skin, 0.02))
        if y == 6 and x in (3, 4):
            return mouth
        return p.vary(skin, 0.02)

    def wreath(face, x, y, w, h):
        if face == "top":
            return p.vary(leaf_light if (x + y) % 2 else leaf) if x in (0, 7) or y in (0, 7) else None
        if face == "bottom":
            return None
        ring = (x + BAND_OFFSET[face]) % 4
        if y == 1:
            return pollen if ring == 0 else light if ring in (1, 3) else p.vary(leaf)
        if y in (0, 2):
            return light if ring == 0 else p.vary(leaf_light) if ring == 2 else None
        return None

    def body(face, x, y, w, h):
        if face == "top":
            return collar
        if face == "bottom":
            return p.vary(tunic)
        if face == "front" and y == 0 and 2 <= x <= 5:
            return collar
        if face == "front" and 2 <= y <= 3 and 2 <= x <= 3:
            return seed_brown if (x + y) % 2 == 0 else sunflower
        if face == "front" and ((y == 2 and x == 1) or (y == 3 and x == 4)):
            return sunflower
        if y == 7:
            return gold if face == "front" and x in (3, 4) else bark
        if y == 11 and x % 2 == 0:
            return p.vary(tunic_dark)
        return p.vary(tunic_light if (x * 2 + y) % 4 == 0 else tunic)

    def jacket(face, x, y, w, h):
        if face == "top":
            return p.vary(leaf_light) if (x + y) % 2 == 0 else None
        if face in ("front", "back", "right", "left"):
            if y <= 1 and (x + y) % 2 == 0:
                return p.vary(leaf_light)
            if y >= 10 and x % 2 == 0:
                return p.vary(tunic)
        return None

    def arm(face, x, y, w, h):
        if face == "top" or y <= 4:
            return p.vary(tunic_light if (x + y) % 3 == 0 else tunic)
        if face == "bottom":
            return skin_shade
        if y == 8:
            return vine
        if y == 9:
            return bark
        return p.vary(skin, 0.02)

    def sleeve(face, x, y, w, h):
        if face == "top":
            return p.vary(leaf_light)
        if face != "bottom" and y <= 2 and (x + y) % 2 == 0:
            return p.vary(leaf_light)
        return None

    def leg(face, x, y, w, h):
        if face == "top":
            return trousers
        if face == "bottom":
            return bark_dark
        if y >= 10:
            return p.vary(bark)
        if y == 9:
            return p.vary(vine)
        return vine if (x + y) % 4 == 0 else p.vary(trousers)

    def moss_tufts(face, x, y, w, h):
        if face not in ("top", "bottom") and y == 9 and x % 2 == 0:
            return p.vary(moss)
        return None

    return build(head, wreath, body, jacket, arm, sleeve, leg, moss_tufts, arm_width=4)


# ----------------------------------------------------------------------------------- the queen

def queen():
    p = Painter(7777)
    skin, skin_shade = (246, 228, 214), (228, 206, 190)
    hair, hair_dark, hair_light = (232, 206, 140), (196, 162, 94), (250, 236, 190)
    eye, white, brow = (132, 186, 226), (252, 252, 250), (196, 162, 94)
    lips = (206, 140, 140)
    silver, silver_dark = (214, 222, 232), (168, 180, 198)
    gem, gem_glow = (240, 250, 255), (150, 210, 255)
    gown, gown_shade, gown_fold = (248, 248, 246), (228, 232, 238), (206, 214, 226)

    def hair_px(x, y):
        return p.vary(hair_light if (x + y * 2) % 6 == 0 else hair_dark if x % 4 == 0 else hair, 0.04)

    def gown_px(x, y):
        return p.vary(gown_fold if x % 4 == 1 else gown_shade if (x + y) % 5 == 0 else gown, 0.015)

    def head(face, x, y, w, h):
        if face in ("top", "back"):
            return hair_px(x, y)
        if face == "bottom":
            return skin_shade
        if face in ("right", "left"):
            return hair_px(x, y) if y <= 1 or from_front(face, x, w) >= 2 else p.vary(skin, 0.02)
        if y == 0:
            return hair_dark if x in (3, 4) else hair_px(x, y)   # a centre parting
        if y == 1 or (x in (0, 7) and y <= 7):
            return hair_px(x, y)
        if y == 3 and x in (1, 2, 5, 6):
            return brow
        if y == 4:
            return {1: white, 2: eye, 5: eye, 6: white}.get(x, p.vary(skin, 0.015))
        if y == 6 and x in (3, 4):
            return lips
        return p.vary(skin, 0.015)

    def circlet(face, x, y, w, h):
        if face in ("top", "bottom"):
            return None
        if face == "front" and x in (3, 4) and y in (1, 2):
            return gem if y == 2 else gem_glow        # the star at her brow
        if y == 2:
            return silver if (x + BAND_OFFSET[face]) % 3 else silver_dark
        if face == "back" and y >= 3:
            return hair_px(x, y)
        if face in ("right", "left") and y >= 3 and from_front(face, x, w) >= 3:
            return hair_px(x, y)
        return None

    def body(face, x, y, w, h):
        if face == "top":
            return gown
        if face == "bottom":
            return gown_shade
        if face == "front" and y == 0 and 2 <= x <= 5:
            return p.vary(skin, 0.015)
        if y == 5:
            return gem_glow if face == "front" and x in (3, 4) else silver
        if face == "front" and 1 <= y <= 4 and (x + y) % 5 == 0:
            return silver                                # silver thread worked into the bodice
        return gown_px(x, y)

    def jacket(face, x, y, w, h):
        if face == "back" and y <= 9:
            return hair_px(x, y)                         # hair to her waist and past it
        if face in ("front", "back", "right", "left") and y >= 10:
            return gown_shade if x % 2 == 0 else gown    # the gown flaring at the hem
        return None

    def arm(face, x, y, w, h):
        if face == "bottom":
            return skin_shade
        if y >= 10:
            return p.vary(skin, 0.015)
        if y == 9:
            return silver
        return gown_px(x, y)

    def sleeve(face, x, y, w, h):
        if face in ("front", "back", "right", "left") and y >= 7:
            return gown_fold if x % 2 == 0 else gown     # long sleeves falling away from the wrist
        return None

    def leg(face, x, y, w, h):
        if face == "bottom" or y == 11:
            return gown_fold
        return gown_px(x, y)                             # the gown to the ground - no legs showing

    def train(face, x, y, w, h):
        if face not in ("top", "bottom") and y >= 8 and (x + y) % 2 == 0:
            return gown
        return None

    return build(head, circlet, body, jacket, arm, sleeve, leg, train, arm_width=3)


# ----------------------------------------------------------------------------------- assembly

def build(head, hat, body, jacket, arm, sleeve, leg, leg_overlay, arm_width):
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    box(img, 0, 0, 8, 8, 8, head)
    box(img, 32, 0, 8, 8, 8, hat)
    box(img, 16, 16, 8, 12, 4, body)
    box(img, 16, 32, 8, 12, 4, jacket)
    box(img, 40, 16, arm_width, 12, 4, arm)      # right arm
    box(img, 40, 32, arm_width, 12, 4, sleeve)
    box(img, 32, 48, arm_width, 12, 4, arm)      # left arm
    box(img, 48, 48, arm_width, 12, 4, sleeve)
    box(img, 0, 16, 4, 12, 4, leg)               # right leg
    box(img, 0, 32, 4, 12, 4, leg_overlay)
    box(img, 16, 48, 4, 12, 4, leg)              # left leg
    box(img, 0, 48, 4, 12, 4, leg_overlay)
    return img


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for index, (name, colour) in enumerate(COLOURS.items()):
        female(colour, 4100 + index).save(OUT / f"fairy_{name}_female.png")
        male(colour, 4200 + index).save(OUT / f"fairy_{name}_male.png")
        print(f"wrote fairy_{name}_female.png and fairy_{name}_male.png")
    queen().save(OUT / "fairy_queen.png")
    print("wrote fairy_queen.png")


if __name__ == "__main__":
    main()
