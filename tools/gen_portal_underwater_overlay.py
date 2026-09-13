"""Generates the full-screen underwater overlay for the portal fluid (see
ModFluidTypes.PORTAL_WATER#getRenderOverlayTexture) - the same kind of screen-space tint
vanilla water paints over the camera when your eyes are submerged
(assets/minecraft/textures/misc/underwater.png), but read as pale and white with just a hint
of blue rather than vanilla's fairly saturated, murky blue.

Run from anywhere with `python tools/gen_portal_underwater_overlay.py` (requires Pillow).
Vanilla's original is a static (non-animated) 16x16 tile with a subtle two-tone noise pattern
and alpha around 135-146; this keeps the same size and the same style of noise so it tiles the
same way, but reads completely differently in practice for a reason worth spelling out: vanilla's
tint is a fairly *saturated* blue, which stands out against almost any background purely from
being a different hue - a near-white tint doesn't get that for free. Blended over an
already-bright scene (which is most of what the Fairy Realm's fixed-noon sky ever shows), white
blended with more white reads as barely-there regardless of alpha, so matching the *requested*
"whiter, slightly blue" look and having it actually be visible pulls in opposite directions -
alpha has to go well past what a saturated color would need to compensate.

Three earlier attempts - alpha 95-110 with a fairly saturated blue, then alpha 175-200 and
225-245 with this same near-white color - were all still reported as producing no visible tint
at all, not just a faint one. That turned out to have a real explanation, found by decompiling
ScreenEffectRenderer#renderFluid (which every IClientFluidTypeExtensions#getRenderOverlayTexture
override ends up calling by default via renderOverlay): it hardcodes
`RenderSystem.setShaderColor(f, f, f, 0.1F)` - a global alpha multiplier of exactly 10%, applied
on top of this texture's own alpha, that no override could reach. Pushing this file's alpha from
95 to 245 only ever moved the *real* effective alpha from about 3.7% to 9.6% - both are
essentially invisible against a bright scene for a deliberately pale, near-white color, which is
exactly the "changing the alpha does nothing" symptom that kept showing up.

Now that ModFluidTypes overrides renderOverlay directly (see
client/ModFluidOverlayRenderer.java) instead of relying on that capped default, this texture's
own alpha is finally the *only* multiplier - so it's dialed back down from the diagnostic-ceiling
values above to something that actually reads as "a pale, present tint" rather than a wall of
white: high enough to be unmistakably there, not so high it blocks the view outright the way a
flat 255 would now that nothing quietly divides it down to a tenth of that anymore.
"""
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX_DIR = os.path.join(ROOT, "src", "main", "resources", "assets", "magiccircles", "textures", "misc")
os.makedirs(TEX_DIR, exist_ok=True)

SIZE = 16
DARK = (215, 233, 250)
LIGHT = (232, 244, 255)
ALPHA_LOW = 150
ALPHA_HIGH = 180


def main():
    random.seed(20260905)
    img = Image.new("RGBA", (SIZE, SIZE))
    for y in range(SIZE):
        for x in range(SIZE):
            color = DARK if random.random() < 0.5 else LIGHT
            alpha = random.randint(ALPHA_LOW, ALPHA_HIGH)
            img.putpixel((x, y), color + (alpha,))

    tex_path = os.path.join(TEX_DIR, "portal_underwater.png")
    img.save(tex_path)
    print(f"Wrote {tex_path}")


if __name__ == "__main__":
    main()
