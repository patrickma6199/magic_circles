# Magic Circles

A Minecraft Forge mod for **1.20.1**. Draw magic circles with chalk, power them with
arcane materials, and (eventually) cast your own spells — a spiritual cousin of
Witchery/Bewitchment, but with your own twist on materials and magic.

This document is both a project README and a running "how modding works" guide, written
for a first-time Forge modder. Skim the parts you already understand.

## What's already set up

- **Forge 1.20.1 / 47.4.10** (the "recommended" build), scaffolded from the official MDK.
- **JDK 17** installed separately from your default JDK (Forge 1.20.1 requires exactly 17;
  your system default is newer). The project is pinned to it in two places:
  - `build.gradle` → `java.toolchain.languageVersion = JavaLanguageVersion.of(17)`
  - `.vscode/settings.json` → points VS Code's Java tooling at the Temurin 17 install
- A working vertical slice of the mod (see below) that compiles and runs.

## How to run it

Open a terminal in this folder (VS Code's integrated terminal is fine) and run:

```
./gradlew runClient
```

The **first** run of any `gradlew` command downloads Minecraft + Forge's patch data and
decompiles/recompiles a chunk of the game — this can take several minutes and is normal.
Subsequent runs are fast (seconds). This launches a real Minecraft client in a dev
environment, logged in with an offline "Dev" account, with the mod loaded.

Other useful tasks:

- `./gradlew runServer` — launch a dedicated server with the mod (for testing multiplayer-only logic).
- `./gradlew build` — compile and package the mod into `build/libs/magiccircles-<version>.jar`,
  the file you'd actually distribute or drop into a normal (non-dev) Minecraft instance.
- `./gradlew runData` — run Forge's data generators, once you start using them (see Roadmap).

A note on VS Code: the Java extension and Gradle extension will index the project in the
background the first time you open it, which includes decompiling vanilla Minecraft for
jump-to-definition support. That's a separate, one-time process from the actual build —
if you see `java.exe` using CPU for a while after opening the folder with no visible
progress, that's most likely this, not something stuck.

## How a Forge mod is put together

A few concepts that explain almost everything you'll see in `src/main`:

**Mod lifecycle.** Forge finds your mod via the `@Mod("magiccircles")` annotation on
[MagicCircles.java](src/main/java/com/patrickma/magiccircles/MagicCircles.java) (the ID
must match `mods.toml` and `gradle.properties`). Forge constructs that class once, at
startup, and hands its constructor a **mod event bus** — a queue of lifecycle events
(register blocks, register items, client setup, etc.) that only your mod's own code
listens to. There's a second bus, the **Forge event bus**, for game events that happen at
runtime (a block breaking, a player interacting, a server starting) — you'll reach for
that once you add actual spellcasting behavior.

**Registries.** You never `new` up a `Block` and expect Minecraft to know about it —
everything (blocks, items, block entities, creative tabs, sounds, ...) goes through a
registry, so the game and other mods can look it up by a stable ID like
`magiccircles:magic_circle`. `DeferredRegister` is Forge's helper for this: you declare
what you want registered as static fields, and it fires the actual registration at the
right point in the mod lifecycle. See
[registry/ModBlocks.java](src/main/java/com/patrickma/magiccircles/registry/ModBlocks.java) and
[registry/ModItems.java](src/main/java/com/patrickma/magiccircles/registry/ModItems.java).

**Blocks vs. Block Entities.** A `Block` describes *shared* behavior for every block of
that type in the world (its shape, hardness, what happens when you right-click it). It
holds no per-placement state. When a block needs to remember something *per placed
instance* — a timer, an inventory, "which spell is this circle set to" — it gets a
**Block Entity**, a small object Minecraft keeps attached to that specific position. Ours
is [MagicCircleBlockEntity.java](src/main/java/com/patrickma/magiccircles/block/entity/MagicCircleBlockEntity.java);
it currently just tracks an animation timer, but this is exactly where "this circle's
active ritual" state will live later.

**Assets (client) vs. data (server).** `src/main/resources/assets/magiccircles/...` is
everything only the client needs to *render* your content: textures, block/item models,
blockstates (which model to show for which block state), and language files. `src/main/
resources/data/magiccircles/...` is everything the *game logic* needs, which the server
also loads: recipes, loot tables, tags. This split matters because a dedicated server
never loads the `assets` folder at all — if game logic depended on something in there,
it'd break multiplayer.

## What's in the mod right now

1. **Arcane Dust** (`item/arcane_dust`) — a crafting reagent. Craft it from 1 Redstone +
   1 Glowstone Dust (shapeless recipe:
   [recipes/arcane_dust.json](src/main/resources/data/magiccircles/recipes/arcane_dust.json)).
2. **Chalk**, in five colors - regular, Gold, Purple, Red, and Green
   (`item/chalk`, `item/gold_chalk`, `item/purple_chalk`, `item/red_chalk`, `item/green_chalk`;
   [ChalkItem.java](src/main/java/com/patrickma/magiccircles/item/ChalkItem.java) /
   [ColoredChalkItem.java](src/main/java/com/patrickma/magiccircles/item/ColoredChalkItem.java)) —
   Chalk is crafted from 1 Arcane Dust + 1 Bone Meal
   ([recipes/chalk.json](src/main/resources/data/magiccircles/recipes/chalk.json)); Gold
   Chalk from 1 Arcane Dust + 1 Gold Nugget
   ([recipes/gold_chalk.json](src/main/resources/data/magiccircles/recipes/gold_chalk.json)).
   Purple, Red, and Green Chalk don't have a recipe yet - how you'd craft them (very likely a
   normal Chalk plus some Fairy Realm material, once that side of the mod exists) is
   deliberately undecided for now, so they're creative-inventory-only.
   All five are `BlockItem`s that place a Magic Circle rune, but unlike a normal block item
   they have real **durability** (200) instead of a stack count — drawing a rune costs 1
   durability point rather than consuming the item, so one stick draws up to 200 runes
   before it wears out (`ColoredChalkItem` is just `ChalkItem` with one field, its
   [RuneColor.java](src/main/java/com/patrickma/magiccircles/block/RuneColor.java), a single
   enum shared by the block, the chalks, the wisps, and ritual detection so a color only has
   to be defined in one place). (Breaking a placed rune drops nothing at all now, matching a
   Heart Core - durability chalk is meant to be spent, not endlessly refunded.)

   `ChalkItem` overrides `getDescriptionId()` to bypass `BlockItem`'s default (which always
   defers to the *block's* name, "Magic Circle", regardless of which chalk placed it) - without
   this override every colored chalk would show "Magic Circle" in its tooltip no matter what
   its lang entry said, since the block's own name wins first.
3. **Magic Circle** rune
   ([block/MagicCircleBlock.java](src/main/java/com/patrickma/magiccircles/block/MagicCircleBlock.java)) —
   flat and walkable like redstone dust (1-pixel-tall outline shape, zero collision, no
   occlusion, rendered on the `cutout` layer so the transparent parts of the texture actually
   show the ground through them instead of drawing black — see `ClientSetup#onClientSetup`).
   Each placement randomly picks one of **20 different rune symbols**
   (`MagicCircleBlock.VARIANT`, an `IntegerProperty` set in `getStateForPlacement`) so a
   field of drawn circles doesn't look identical, independently of a second property,
   `MagicCircleBlock.COLOR` (an `EnumProperty<RuneColor>`), which tracks which chalk drew it -
   a different glow/border per color, same 20 symbols regardless - 100 texture/model
   combinations in total (20 symbols x 5 colors), all generated by
   [tools/gen_runes.py](tools/gen_runes.py) rather than drawn by hand — rerun it
   (`pip install pillow` then `python tools/gen_runes.py`) after editing the point
   lattice/branch pool or `PALETTES` in that script to change the "alphabet" or colors.

   A lone rune (not part of a working circle) glows quietly on its own: a drift of colored
   `DustParticleOptions` particles directly above its center, colored by `RuneColor.wispColor()`
   (see `MagicCircleBlockEntity#spawnStationaryGlow`). A **complete circle** is a specific
   13-rune footprint -
   [ritual/MagicCircleRitual.java](src/main/java/com/patrickma/magiccircles/ritual/MagicCircleRitual.java)
   has the exact shape, but bird's-eye it's a rounded ring with a mandatory center:
   ```
   0 1 1 1 0
   1 0 0 0 1
   1 0 X 0 1
   1 0 0 0 1
   0 1 1 1 0
   ```
   Once a rune finds itself part of one of these (whether it's the center or one of the 12
   ring positions - `MagicCircleRitual.isPartOfCompleteCircle` checks both), it stops glowing
   individually. Instead, [client/ClientCircleWisps.java](src/main/java/com/patrickma/magiccircles/client/ClientCircleWisps.java)
   takes over the whole circle: one wisp per ring rune (so a mixed-color ring shows a mix of
   wisp colors), each colored to match the rune it came from, flying loosely over the circle's
   footprint. No coordination between the 13 blocks is needed for any of this - only the true
   center of a given circle will ever find its own ring complete, and every rune checks this
   for itself every 10 ticks. Break any one of the 13 and the effect stops within that window,
   and whichever runes are left glow individually again. Breaking a rune drops nothing - there's
   deliberately no loot table for it (durability chalk means the rune itself was never "the
   item"; it's spent when drawn, not when broken).

   Each wisp leaves a short trail (several interpolated particles per tick along the segment
   it just moved through, not one dot) and, once orbiting a Heart Core, follows its own
   inclined elliptical path (a golden-angle spread of tilts, one per wisp, plus a per-wisp
   radius/speed) rather than 12 dots sharing one flat ring. While a spell is actively
   channeling through the circle (see the storm spell below), the orbit is overridden entirely
   by a [ritual/WispChannel.java](src/main/java/com/patrickma/magiccircles/ritual/WispChannel.java) -
   synced from the Heart Core's block entity to the client (`getUpdateTag`/`handleUpdateTag`,
   since ordinary block-entity fields don't reach an already-loaded client on their own) so
   `ClientCircleWisps` knows to send them somewhere else instead (skyward, for the storm).

   Since both chalks are separate `BlockItem`s wrapping this same block, Forge's block→item
   lookup (used for creative-mode pick-block) only remembers whichever was constructed last -
   `MagicCircleBlock#getCloneItemStack` overrides that so pick-blocking a rune always hands
   back the chalk that actually matches its color.
4. **Heartstone** (`item/heartstone`,
   [HeartstoneItem.java](src/main/java/com/patrickma/magiccircles/item/HeartstoneItem.java)) —
   holds up to 1000 **mana**, shown via the vanilla durability bar (repurposed: the item is
   never actually damaged or destroyed, `isBarVisible`/`getBarWidth`/`getBarColor` are
   overridden to read a custom "Mana" NBT value instead of real item damage — see the class
   doc comment for why that split matters). How you first *acquire* one isn't implemented
   yet, so for now it's only available from the creative inventory.

   In hand, on the ground, or in an item frame, it renders as the same glowing orb+aura the
   placed Heart Core uses, not a flat 2D icon - see
   [client/HeartstoneItemRenderer.java](src/main/java/com/patrickma/magiccircles/client/HeartstoneItemRenderer.java)
   and `HeartstoneItem#initializeClient`, the standard Forge hook (`Item#initializeClient`,
   called only client-side) for handing an item a custom `BlockEntityWithoutLevelRenderer` -
   the same mechanism vanilla uses for chests, shields, and tridents. The item model just
   declares `"parent": "builtin/entity"` and points nowhere else; the 2D icon file still
   exists and is still used for the inventory slot and the break-particle texture, since
   neither of those go through this renderer. `renderByItem`'s pose scale is `0.45`, well
   *below* the block-scale the model is authored at - an earlier `1.4` (bigger than the actual
   block) is what made the held/dropped item look absurdly oversized.

   Right-click a complete circle's **center rune** with it (not the ground - the rune
   itself) and, since a circle's center is never actually empty, that rune becomes a
   [HeartCoreBlock](src/main/java/com/patrickma/magiccircles/block/HeartCoreBlock.java)
   carrying the Heartstone's mana and remembering that rune's `RuneColor`
   (missing the ring, or clicking a rune that isn't a valid center, gives you a red
   action-bar message instead of silently doing nothing) - "complete circle" here means
   either the rounded 12-ring most spells use, or the portal's 5x5 border (see "Opening a
   portal" below), whichever one the rune actually happens to be the center of. It has no
   `BlockItem` and isn't placeable any other way. Visually it's fully transparent as a block
   (`RenderShape.INVISIBLE`) - everything you see is drawn by
   [client/HeartCoreBlockEntityRenderer.java](src/main/java/com/patrickma/magiccircles/client/HeartCoreBlockEntityRenderer.java):
   a small glowing cube (see [client/HeartCoreModel.java](src/main/java/com/patrickma/magiccircles/client/HeartCoreModel.java)) -
   an opaque "orb" a couple pixels across, lit normally but textured with a per-face gradient
   so it reads as round rather than one flat color, wrapped in a larger, translucent "aura"
   shell rendered full-bright and additively blended, the same layering trick a glowing wire
   or neon sign texture uses - floating 1.5 blocks above the rune (roughly eye height),
   gently bobbing and slowly spinning. As long as its ring stays complete, it takes over the
   circle's wisps from whichever `MagicCircleBlockEntity` used to own them and tightens them
   into an orbit around itself instead of them wandering the whole circle.

   This is only the fully-charged look, though: whenever the heart is depleted (see "Mana
   depletion" below), the renderer switches to a dark, shrunk, non-glowing version
   instead - a darkened tint on the orb, the aura scaled down and switched from the full-bright
   emissive render path to a normal translucent one lit by real ambient light (which is the
   entire reason it stops reading as "glowing" - full-bright emissive is what makes it glow
   regardless of lighting in the first place), so a depleted heart looks convincingly "off"
   rather than just a dimmer version of the same thing. It snaps straight back to the full
   glowing look the instant mana returns above the depletion threshold - there's no separate
   "charging" animation, just the absence of the depleted look, matching how
   `HeartCoreBlockEntity#manaDepleted` is already a plain boolean rather than a gradual meter.

   It also lights up the area like a torch: Minecraft's light engine only has one brightness
   channel (0-15) and no concept of color at all, so genuinely colored world light isn't
   something any vanilla-compatible block can produce - the *blue* you see is entirely the
   orb/aura's own color, not the light itself, which is just `.lightLevel(14)` in `ModBlocks`
   (a normal block property, torch-strength, colorless like every other light source in the
   game).

   Since the heart floats a block and a half up - mostly inside the cell *above* its own
   block - [HeartCoreTopBlock.java](src/main/java/com/patrickma/magiccircles/block/HeartCoreTopBlock.java)
   is placed in that cell purely to give the floating heart itself a hitbox: Minecraft can't
   give one block a hitbox reaching into a neighboring position, so this is a second,
   invisible block that just forwards every click and break straight back down to the real
   one (`HeartCoreBlock.interact`/`HeartCoreBlock.destroy`, both package-visible so it can
   call them directly).

   Breaking either block (see `HeartCoreBlock#destroy`) doesn't leave air - a circle's
   center is never empty - it turns back into an ordinary rune of whatever color it
   originally was (so the circle, and its wandering wisps, keep going) and drops a Heartstone
   item carrying the same mana back. If a Shield spell (see below) was active, breaking the
   heart also discards every shield node it owned - there's no block left for them to drain
   mana from, so leaving them behind would make an orphaned, permanent, unbreakable sphere.
5. **Fairy Fossil Ore** (`block/fairy_fossil_ore`) - a pink ore, as common as Coal and
   generating across the same Y range (see "Worldgen" below). Needs a stone pickaxe or
   better; mining it always drops exactly one of three things via a weighted loot table
   ([loot_tables/blocks/fairy_fossil_ore.json](src/main/resources/data/magiccircles/loot_tables/blocks/fairy_fossil_ore.json)):
   a Bone (70%), a **Fairy Horn** (20%), or a Heartstone already holding 500 mana (10%, via
   the loot table's `set_nbt` function). There's no Silk Touch support - it always resolves
   through this table, it never drops itself.
6. **Fairy Horn** (`item/fairy_horn`) - the spell activator. Doesn't do anything on its own;
   see the storm spell below.
7. **Fairy Portal** (`block/fairy_portal`) - a thin, translucent, non-solid slab (same shape
   family as vanilla's Nether Portal) that teleports anything standing in it. Not obtainable
   or placeable directly - only [ritual/PortalRitual.java](src/main/java/com/patrickma/magiccircles/ritual/PortalRitual.java)
   ever creates one. See "Opening a portal" below.
8. **Book of the Faye** ([BookOfTheFaye.java](src/main/java/com/patrickma/magiccircles/item/BookOfTheFaye.java)) -
   a lore book, creative-only for now (no recipe or drop source yet). It's a real vanilla
   `minecraft:written_book` with its pages baked into fixed NBT rather than left blank for a
   player to write, deliberately *not* a custom item - see "The Book of the Faye" below for
   why that specifically matters, what the book says, and how its in-book navigation works.

All items and the rune show up in the "Magic Circles" creative-inventory tab
([registry/ModCreativeTabs.java](src/main/java/com/patrickma/magiccircles/registry/ModCreativeTabs.java)) -
the Heart Core deliberately doesn't, since you're only meant to get one via the ritual above.

## Casting spells

Right-click a working Heart Core with a Fairy Horn (see `HeartCoreBlock#use` /
`HeartCoreBlock#interact`). Which spell that casts depends entirely on the ring's **color
composition** - `MagicCircleRitual.detectSpell` counts each of the 12 ring runes' colors
(`ringColorCounts`) and looks the result up in [HeartSpell.java](src/main/java/com/patrickma/magiccircles/ritual/HeartSpell.java):

- **A solid ring** (all 12 the same color) casts that color's **solo spell** -
  `HeartSpell.soloFor`.
- **A ring split exactly 6-and-6 between two colors** - position doesn't matter, only the
  count, so the 6 of a given color can be scattered anywhere around the ring, not just a
  clean half-circle arc - casts that pair's **combo spell**, if one's been wired up yet
  (`HeartSpell.comboFor`). With 5 colors there are 10 possible pairs; only 6 have a spell so
  far (see below) - the other 4 (Blue+Gold, Blue+Green, Gold+Purple, Purple+Red) are real,
  validly-shaped 6-6 splits that just aren't recognized yet.
- **The portal's own border pattern** - a different shape entirely (see "Opening a portal"
  below) - casts a 12th spell, Zuzo's Crossing, that isn't part of this color-counting system
  at all and is checked for separately, before any of the above.
- **Anything else** (three-plus colors on one ring, or an uneven split) isn't recognized at
  all.

An unrecognized ring gets a `spell_not_recognized` red action-bar message rather than either
silently casting the wrong thing or reusing the "ring's broken" message for a ring that isn't
actually broken.

**A heart can power up to two prolonged spells at once** - storm, shield, tempest ward, mana
font, and the vigor buff each already track their own duration/drain state independently
(`HeartCoreBlockEntity`'s own dedicated fields, not one shared "current spell" slot), which is
what actually makes running two of them together safe: nothing about any one's state reads or
writes any of the others'. Cast Shield, then - without breaking anything, just repainting the
ring - redraw it to a different composition and cast again to start, say, the tempest ward
alongside it; both keep running independently, on the same heart's mana. `HeartSpell#isProlonged`
is what's actually gated: recasting a spell that's *already* running is rejected (it would leak
the first cast's state - a second `startShieldSpell` call, say, would spawn a whole new set of
orbs without cleaning up the old ones), and so is a third prolonged spell once two are already
active, but a **one-shot spell** (Flowers, Heal, XP, Cleansing Rain, Bloom of Life, Verdant
Harvest) was never part of this at all - it fires and finishes instantly regardless of what
else is running, so casting Cleansing Rain while Shield is still up doesn't touch the shield.

**Repainting the ring mid-burst can leave a ring wisp's home rune a different color than it
was when the wisp left.** Say Heal is cast (sending its 12 ring wisps out to `TO_TARGETS` for a
few seconds), and the ring gets repainted for a second spell before they've flown back - by the
time they'd normally rejoin the ring, their home rune isn't the color they left it as. Each
[client/ClientCircleWisps.java](src/main/java/com/patrickma/magiccircles/client/ClientCircleWisps.java)
wisp remembers the color its rune had the last time it was calmly orbiting (`Wisp#restColor`,
refreshed continuously while the channel is `ORBIT`, frozen the moment it isn't); the instant a
burst channel returns to `ORBIT`, that remembered color is compared against the rune's actual
current color, and a mismatch triggers a brief shrink-to-nothing-and-back
(`Wisp#fadeTicks`, `FADE_TICKS` = 20) instead of an abrupt snap - by the time it's fully back,
it's already showing its new color.

**Breaking the ring cancels whatever it's powering** - `HeartCoreBlockEntity#tickRingIntegrity`
checks `MagicCircleRitual.hasCompleteRing` every `RING_CHECK_INTERVAL` ticks, and the moment a
ring that was still valid stops being one, cancels every prolonged spell the heart has running
(`cancelAllActiveSpells`) exactly as if its mana had simply run out. This means *breaking* a
rune - actually destroying the block, not repainting it - not merely repainting one: a repaint
leaves every position a real `MagicCircleBlock`, which is exactly what `hasCompleteRing` still
considers "complete" and what the two-concurrent-spells mechanic above depends on. The same
check covers the portal: it isn't one of this heart's own duration fields (it's tracked
externally, in `FairyPortalManager`, keyed by the heart's own position), so `tickRingIntegrity`
separately calls `FairyPortalManager#closeIfBorderBroken` every cycle too - breaking the 5x5
border closes an open portal the same way a completed round trip normally would.

Every spell's mana cost lives on its `HeartSpell` value; casting deducts it upfront except
for `SHIELD`, `MANA_FONT`, and `VITAL_SURGE`, whose real cost is an ongoing drain/gain over
time instead (see their sections below) - for those three, the listed cost is just the
minimum needed to be worth starting at all. The portal isn't a `HeartSpell` value (again,
different pattern) but works the same way cost-wise: 500 mana, deducted upfront, checked by
`HeartCoreBlock#interact` directly rather than through `HeartSpell.manaCost()`.

Whenever a *prolonged* spell is running (storm, shield, tempest ward, mana font, vigor - not
the one-shot effects, which are over too fast for it to matter) the heart itself shows it: 6
plain white wisps ease out from the heart and into a slow patrol around the *outside* of the
whole circle, well past the 12-rune ring, then ease back in the moment the spell ends. This is
`HeartCoreBlockEntity#isSpellActive()` driving
[client/ClientHeartWisps.java](src/main/java/com/patrickma/magiccircles/client/ClientHeartWisps.java) -
a completely separate system from the ring's own colored wisps
([client/ClientCircleWisps.java](src/main/java/com/patrickma/magiccircles/client/ClientCircleWisps.java)),
since "is something ongoing happening here" is the same question regardless of which spell it
actually is, so one shared mechanism covers all of them rather than something bespoke per
spell. Since a heart can now run *two* prolonged spells at once, there's a second, smaller ring
too - 3 wisps, one block higher, patrolling the *opposite* direction
(`ClientHeartWisps#tickSecondary`, driven by `HeartCoreBlockEntity#isSecondSpellActive`) - lit
only once a second spell actually joins the first, so at a glance the two rings read as "one
thing running" vs. "two things running" rather than needing to count wisps.

**These wisps didn't move at all for a while** - they'd sit right at the heart no matter what
was happening, which traces to a genuine Forge gotcha rather than anything wrong with the
wisp math itself. `HeartCoreBlockEntity` overrides `getUpdateTag`/`handleUpdateTag` to sync a
handful of fields live (wisp channel, heal targets, and the spell-active flag this section
relies on) - the standard-looking vanilla pattern for a lightweight block-entity sync. But
Forge's own `IForgeBlockEntity` (confirmed by decompiling it) gives `BlockEntity` a *default*
`onDataPacket` that calls `load(tag)`, not `handleUpdateTag(tag)`, for every live update packet
after the first. Only the very first sync (a chunk coming into view) actually goes through
`handleUpdateTag` directly - every subsequent push silently fell back to `load()`, which never
read the spell-active flag (or the heal targets) at all, only the wisp channel happened to work
because it's redundantly read in `load()` too. The fix is a one-method override -
`onDataPacket` now explicitly calls `handleUpdateTag(packet.getTag())` - but the underlying
lesson is worth keeping in mind for any future block entity here: overriding
`handleUpdateTag` alone doesn't actually wire it up in this Forge version.

**Checking a heart's mana** - shift-clicking a Heart Core (or its floating top half) with a
Fairy Horn shows a `Mana: X / Y` action-bar message via a new
`block.magiccircles.heart_core.mana_query` translation key. It's checked first in
`HeartCoreBlock#interact`, before the spell-active check or anything ring-related, so it always
works regardless of what the heart is doing - it's a status read, not an action, so it never
consumes mana or gets blocked by a spell already running.

**This didn't actually show up at all at first, and the cause was outside `HeartCoreBlock`
entirely.** Decompiling `ServerPlayerGameMode#useItemOn` turned up a genuine vanilla mechanic:
sneak-right-clicking a block while holding *any* non-empty item skips that block's own `use()`
completely and calls the item's `useOn()` instead (gated by `Item#doesSneakBypassUse`, default
`false` - it's the same mechanic that lets you sneak-place a block against a chest instead of
opening it). Since the Fairy Horn was a plain, un-customized `Item`, every sneak-right-click on
a Heart Core never reached `HeartCoreBlock#interact` at all, no matter how correct the shift-key
check inside it was. Fixed with a dedicated
[item/FairyHornItem.java](src/main/java/com/patrickma/magiccircles/item/FairyHornItem.java)
overriding `doesSneakBypassUse` to `true` - telling vanilla the horn has no sneak-specific item
behavior of its own, so sneaking should still hand the click to the block normally.

**Mana depletion** has its own visual tell, driven by a new `HeartCoreBlockEntity#manaDepleted`
flag (synced the same way as the spell-active flag above): the moment a heart's mana drops
below `HeartSpell.minManaCost()` (currently `MANA_FONT`'s 10, computed rather than hand-copied
so it can't drift), it's zeroed out - there's no point leaving unusable leftover dust - and two
things happen client-side:

- The heart's own 6 white wisps ([client/ClientHeartWisps.java](src/main/java/com/patrickma/magiccircles/client/ClientHeartWisps.java))
  stop docking/patrolling and instead sink straight down into the ground one at a time, each
  one waiting a short staggered delay after the last before it starts falling, so all 6 don't
  drop at once. Once a wisp reaches the heart's own ground level it stops moving (and stops
  leaving a trail) entirely, reading as it sinking out of sight.
- The ring's own colored wisps ([client/ClientCircleWisps.java](src/main/java/com/patrickma/magiccircles/client/ClientCircleWisps.java))
  stop orbiting the heart and go back to the loose, wandering flight they had before the Heart
  Core ever existed - `depleted` is simply folded into the same `isHeart` check that already
  distinguishes "orbiting a heart" from "wandering over a plain rune," so an empty heart is
  treated as if it weren't a heart at all, wisp-behavior-wise.
- The heart itself ([client/HeartCoreBlockEntityRenderer.java](src/main/java/com/patrickma/magiccircles/client/HeartCoreBlockEntityRenderer.java))
  goes dark, its aura shrinks, and the aura stops glowing altogether - see "The Heart Core"
  above for exactly what that switch involves.

All three effects reverse the moment the heart recharges back above that threshold: every wisp
(grounded or still mid-fall) is freed to ease back up toward its normal dock/guard position,
the ring wisps resume orbiting, and the heart itself snaps straight back to its full glowing
look - no special "recharge" animation needed since the same easing that handles every other
wisp transition handles those, and the heart's own look is a plain on/off switch rather than
something that eases at all.

### Solo spells (one color, all 12 runes)

All in [HeartCoreBlockEntity.java](src/main/java/com/patrickma/magiccircles/block/entity/HeartCoreBlockEntity.java):

### Blue - Storm (100 mana)

`HeartCoreBlockEntity#startStormSpell`:

- Sets the world's weather directly to raining + thundering for exactly 600 ticks (30
  seconds) via `ServerLevel#setWeatherParameters`, and independently counts those same 600
  ticks down itself in `serverTick` so the storm is force-stopped at exactly 30 seconds
  regardless of what vanilla's own weather cycle would otherwise do next.
- Every 1-3.5 seconds (a randomized 30-70 tick interval) while the storm runs, it strikes a
  real `LightningBolt` at a random point 4-9 blocks out from the circle's center, snapped to
  that column's surface height - far enough out to always land outside the circle's ~2.24
  block-radius footprint, so the circle itself is never struck.

**Worth knowing:** Minecraft's weather is global to the whole dimension, not local to the
circle - there's no vanilla concept of "it's raining over here but not over there," so this
storms over everywhere in that dimension for those 30 seconds, not just around the circle.
A truly localized weather effect would need custom rendering and isn't something this spell
attempts.

### Purple - Shield (no upfront cost, drains 50 mana/minute)

`HeartCoreBlockEntity#startShieldSpell` spawns
[ShieldOrbEntity](src/main/java/com/patrickma/magiccircles/entity/ShieldOrbEntity.java)
instances forming a 7-block-radius sphere centered 1.5 blocks above the heart (up from an
original 5, and boundary enforcement - see below - is no longer tied to orb count at all, so
this could grow further without the entity-count concerns that used to come with it), using a
[Fibonacci sphere](https://en.wikipedia.org/wiki/Spiral_similarity#Fibonacci_sphere) point
distribution (`fibonacciSphere`) rather than a naive lat/long grid, which would bunch nodes up
at the poles. It's cast by Purple rather than Gold now (an earlier version had it the other
way around) because the orbs render with the Fairy Portal's texture (see below), which reads
as purple - a purple ring raising a purple-tinted sphere just looks right.

**The orbs themselves are purely cosmetic now** - `ShieldOrbEntity#canBeCollidedWith` returns
`false`, so nothing about them blocks movement directly. An earlier version made them hard-
collidable, which *did* stop everyone including the caster - and there turned out to be no way
to fix that: vanilla's collision check (`Entity#canCollideWith`, confirmed by decompiling it)
is called *on the mover*, asking `other.canBeCollidedWith()` with no way for the orb being
asked to know who's asking. Letting the caster alone pass through isn't something hard
collision can express at all. Instead, `HeartCoreBlockEntity#tickShieldBoundary` runs every
tick while the shield (or the tempest ward) is active: it finds any non-owner `LivingEntity`
that's ended up inside the boundary and teleports them back out to just past its edge, zeroing
their velocity - a "soft wall" enforced by direct correction rather than collision, which can
trivially skip whichever player cast the spell. The orbs are still immune to damage and not
pushable, so they can't be shoved aside or destroyed even though they're not what's actually
stopping anyone.

**A second, larger loop of Purple runes drawn somewhere outside the ring** turns the sphere
into a flat-topped-and-bottomed box instead - see
[ritual/ShieldShapeDetector.java](src/main/java/com/patrickma/magiccircles/ritual/ShieldShapeDetector.java).
"Enclosed" is checked the same way you'd check it by eye: flood-fill outward from just past the
inner ring, treating Purple runes as walls the fill can't cross - if the fill escapes rather
than staying bounded, there's no valid outer shape and the normal sphere is used instead. The
loop doesn't have to sit level with the inner ring, or with itself - the fill only tracks X/Z,
searching a short vertical window at each column for that column's own rune
(`ShieldShapeDetector#findRuneY`), so the outer shape can follow a slope or a staircase. A shape
that turns out to be adjacent to the inner ring itself is rejected the same way
(`ShieldShapeDetector#touchesInnerRing`, also checked ignoring Y) rather than risking a wall
that overlaps the circle it's supposed to surround.

**This detection didn't actually work at all for a while - it always fell back to the
sphere, no matter what was drawn or where.** The cause was the inner ring itself: Shield only
casts off a *solid* Purple ring (it's Purple's solo spell), so the fill - which can't tell "a
rune that's part of the known inner ring" from "a rune that's part of the outer loop" apart,
both just look like a Purple `MagicCircleBlock` - would wrap all the way around the ring and
happily add its own 12 cells to `wallCells` too, indistinguishable from whatever the player
actually drew further out. That alone wouldn't necessarily break anything, except
`touchesInnerRing` checks whether any wall cell sits *adjacent* to a ring position - and the
ring's own 12 positions are all mutually adjacent to each other (they're packed into one 5x5
area). So the instant the ring's cells leaked into `wallCells`, every shape - regardless of
how far away it actually was - got flagged as "touching the ring" and rejected. The fix treats
the inner ring's whole 5x5 footprint as excluded from the fill outright: never open, never a
candidate wall, just skipped, so the fill flows around the outside of that footprint instead of
ever wrapping into it and contaminating the result.

A valid shape becomes a box of orbs following that footprint's X/Z outline, with **one flat
top and one flat bottom rather than a dome or an open-ended wall** - both computed once at cast
time (`HeartCoreBlockEntity#findValidWallShape`): centered on the average height of the loop's
own runes (so a loop drawn on a slope still gets one level box, not one that itself zigzags with
the terrain), with a total span proportional to `SHIELD_WALL_HEIGHT_SCALE` (1.5) times the
*square root* of the 2D area the loop encloses - the same flood fill that finds the wall also
counts every open floor cell it crosses before hitting one. Scaling by the square root rather
than the raw area directly is deliberate: area grows with the *square* of a shape's linear size,
so an early version that scaled 1-to-1 made even a modest loop (a few hundred enclosed cells)
come out clamped all the way to the max height, reading as absurdly tall for what was actually
drawn - the square root ties height to roughly the shape's own radius instead, which is what
"proportional to the area, but much slower" actually looks like. That span is clamped to
somewhere sane (6-250 blocks) and then kept inside the world's actual build range, so a huge
loop doesn't try to build a box thousands of blocks tall just because the formula says so.

Orb density along each column targets roughly `SHIELD_WALL_TARGET_ORB_SPACING` (3) blocks apart,
falling back to sparser spacing only if a wall's total orb count would otherwise cross a hard
safety cap (`SHIELD_WALL_MAX_TOTAL_ORBS`, 6000) - an earlier version divided one fixed 700-orb
budget across every column regardless of the wall's actual height, which for a tall and/or wide
enough shape put as few as 3 orbs per column, tens of blocks apart: several separate-looking
*rings* rather than one continuous wall, each individual ring itself reading as "only 1 block
tall" since a single row of widely-spaced orbs is exactly what it is. Fixing the height formula
above and the density here together were both needed - the height fix keeps most walls from
growing unreasonably tall in the first place, and the density fix keeps whatever height a wall
*does* end up with actually looking continuous rather than sparse.

**A second, separate gap turned out to survive both fixes above: two wall cells touching only
at a corner.** The flood fill that finds the wall only ever steps in the 4 cardinal directions,
so two diagonally-adjacent wall cells correctly block it from leaking between them - "the
interior is enclosed" was never actually wrong. But a *continuous-space* entity (a player or
mob, not a grid walker) can still walk diagonally through that same corner gap, since there's no
orthogonal wall cell actually blocking that path in the world. "Enclosed, from the flood fill's
point of view" and "no physical gap a real entity can slip through" turned out to be two
different questions. `ShieldShapeDetector#closeDiagonalGaps` runs once over the finished wall
shape and, for every diagonal pair of wall cells where neither flanking orthogonal cell already
exists, adds one (at the averaged height of the pair) to close the corner - after this, every
diagonal step through the boundary has a real wall cell on at least one of its two flanking
sides. `tickWallBoundary`'s own push-back was hardened at the same time, independently of the
gap fix itself: it now scans outward one column at a time from the entity's own position until
it finds a column outside the wall footprint and teleports exactly there, rather than nudging by
a fixed distance - a fixed nudge could still land an entity back inside a multi-cell-thick
section of wall, or fail to keep up with something moving fast enough to outrace a single
correction. Both fixes came from re-reading the boundary logic end-to-end after the wall's
height/density were already fixed and the gap was still being reported - I don't have a way to
walk into a live wall myself to confirm the corner case was the actual cause, so this is the
most concrete fix the math points to, not something I've watched close a gap in person.

The lid orbs are capped the same way as before - 400 split between the two lid planes (a
sparse grid across the footprint's bounding rectangle, at the flat bottom and again at the flat
top). Boundary enforcement mirrors the overall shape: the side push only fires at the boundary
ring's own columns (an entity is free to move anywhere inside otherwise, same "push away from
the circle's center" approximation as before - exactly correct for a circle or square,
reasonable for anything else roughly convex), while the top/bottom push fires anywhere within
the footprint's *bounding rectangle*, since a ceiling or floor only means something if it covers
the interior rather than just the boundary - a very concave loop ends up with a lid slightly
bigger than its true enclosed area, the same kind of approximation as the side push already
makes. Tempest Ward never looks for any of this - only the plain Shield spell does, since a
half-Blue combo ring isn't "the established magic circle of purple chalk" this extends.

**A degenerate `bottomY`/`topY` pair (from any cause - a shield cast before these two fields
were even part of the save format is the one known way it could happen) is also guarded against
directly**: `spawnWallOrbs`/`tickWallBoundary` both run their `bottomY`/`topY` pair through a
small `ensureMinWallSpan` helper first, expanding it symmetrically up to `SHIELD_WALL_MIN_HEIGHT`
whenever the gap is smaller than that, rather than trusting the two fields as given.

Every 1200 ticks (60 seconds) the shield drains 50 mana from the heart; if there isn't
enough left, the shield ends and every orb it owns is discarded (`removeShieldOrbs`, also
called when the Heart Core itself is destroyed - see above). Orbs track their owning
`BlockPos` in their own save data, so they survive a server restart/reload correctly matched
to their heart.

### Gold - Flowers (30 mana, one-shot)

`HeartCoreBlockEntity#castFlowerSpell` (via the shared `growAround` helper, also used by
Verdant Harvest below) tries 50 random points scattered between 2 and 12 blocks of the circle
- a random compass direction and distance, not a fixed-size square - and places a random
vanilla flower (dandelion, poppy, blue orchid, allium, azure bluet, red/orange tulip,
cornflower) on any that turn out to be grass with open air above. Each column's *actual*
surface height is looked up (`Level#getHeight`) rather than assumed to match the heart's own
Y - an earlier version checked only the heart's exact height, which on any terrain that
wasn't perfectly flat produced a narrow, oddly banded scattering rather than a natural-looking
spread nearer and farther from the circle.

**A random point can never land inside the circle's own 5x5 footprint**, corners included -
`growAround` explicitly skips any candidate within `CIRCLE_FOOTPRINT_HALF_SIZE` (2) blocks on
either axis of the heart, checked separately from (and in addition to) the polar
`GROWTH_MIN_RADIUS` that was supposed to keep placements away from the circle in the first
place. That radius alone wasn't enough: a point at exactly `GROWTH_MIN_RADIUS` (2.0) landing at
a diagonal-ish angle can still round to one of the footprint's own corners (~2.83 blocks from
center) - and a flower placed on a corner fills the one cell `MagicCircleRitual#hasCompleteRing`
requires to stay air, silently breaking the very ring that just cast the spell and making it
unrecastable until someone notices and breaks the flower. The explicit square check closes that
gap directly rather than trying to find some larger minimum radius that happens to clear every
corner.

### Red - Heal, "Sylvaine's Mercy" (60 mana, one-shot)

`HeartCoreBlockEntity#castHealSpell` fully heals every friendly `LivingEntity` within a
20-block radius - anything that isn't an `Enemy` (`!(entity instanceof Enemy)`, which covers
both `Monster` subclasses and non-`Monster` hostiles like slimes, so hostile mobs specifically
don't get healed) - and plays `SoundEvents.PLAYER_LEVELUP` so the spell is audibly obvious even
when there's nothing else around to visibly react to it. Up to 12 of the healed creatures
(however many there are, capped at the ring's own wisp count) each get their own wisp flying
directly to them (`WispChannel#TO_TARGETS` - see "One-shot spells and the wisps" below) rather
than a shared burst direction, which reads much more like "this spell is healing *these
specific creatures*" than a generic outward flash would.

### Green - Experience, "The Gleaner's Fortune" (40 mana, one-shot)

`HeartCoreBlockEntity#castXpSpell` spawns 12 `ExperienceOrb` entities (7 XP each, 84 total)
scattered within 3 blocks of the circle, paired with a faster `TO_PLAYER` wisp speed (see
below) so the wisps visibly finish their flight to the player right around when the orbs pop
in. This used to have its own dedicated half-second delay (`XP_SPELL_DELAY_TICKS`) on top of
the faster wisp speed, added specifically so the orbs wouldn't appear (and usually already be
picked up) well before the wisps got close - that's now just the general one-second delay every
spell gets (see "Every spell effect is delayed one second" below), so nothing compounds here
beyond what any other spell already waits.

### Every spell effect is delayed one second

Every spell's actual world effect - flowers appearing, weather changing, orbs spawning, a heal
landing, a buff applying, all of it - waits exactly `SPELL_EFFECT_DELAY_TICKS` (20 ticks, one
second) after the cast, via a small generic `HeartCoreBlockEntity#scheduleEffect` queue
(`pendingEffects`, ticked down every server tick by `tickPendingEffects`). What *doesn't* wait
is whichever wisp channel that spell starts - `setWispChannel`/`startOneShotChannel`/
`startTargetedChannel` all still fire the instant the spell is cast, exactly as before. The
result is that the wisps are always what you see first, visibly flying off toward wherever the
effect is about to land (the ground for Flowers, the sky for Storm, straight up for Shield,
etc.) - the effect just used to appear at the same instant the cast happened, before the wisps
had gone anywhere, which read as instant regardless of how deliberately the wisps themselves
were animated. This replaced a single one-off delay that only Experience had (see above) with
one consistent rule every spell follows.

For the heal-based spells (Heal, Bloom of Life) specifically, this meant splitting "who gets
healed" from "healing them": the target list is still selected immediately, at cast time
(`findHealTargets`), so wisps can fly to real, current targets right away, but the actual
`entity.heal(...)` call (`healEntities`) doesn't run until the delayed effect fires - guarded by
an `isAlive()` check, in case a selected target died or otherwise stopped existing in that one
second window.

For the four prolonged spells (Storm, Shield, Tempest Ward, Mana Font) and Vital Surge, the
delay applies to the *entire* start-up (every field that flips the spell "active", plus whatever
one-time world effect goes with it - weather, orb spawning, buffs) rather than just the visible
part, so nothing about a running spell's own internal state is ever inconsistent with what
`isSpellTypeActive`/`activeSpellCount` report. The one accepted trade-off from that choice: for
that first second after casting one of these, before its delayed effect has actually landed,
the ring/two-active-spells re-cast guards in `HeartCoreBlock#interact` don't yet see it as
active - a real but narrow window (mashing the interact key inside that one second could waste
mana on a second cast), judged not worth the extra complexity of tracking "casting, not yet
active" as a third state purely to close it.

### One-shot spells and the wisps

Every one-shot spell briefly redirects the circle's wisps into a "burst"
[WispChannel](src/main/java/com/patrickma/magiccircles/ritual/WispChannel.java) for 3 seconds
(`OUTWARD`, `DOWN`, `TO_PLAYER`, or `TO_TARGETS`, whichever best fits the effect) as a visual
cue that something just happened, then eases back to the normal `ORBIT` channel on its own -
separate from the spells that run for a real duration (storm, shield, tempest ward, mana font,
vigor), which hold a channel for their entire duration instead. Every channel eases toward its
target at the same speed except `TO_PLAYER` and `TO_TARGETS`, which move noticeably faster
(`ClientCircleWisps#FAST_EASE`) - both represent something the server has *already* finished
(orbs already scattered, creatures already healed) by the time the wisp channel even starts, so
they need to visibly catch up fast or the burst reads as sluggish next to an effect that's
already over.

### Combo spells (two colors, split exactly 6-and-6)

Picked where a color pair's two solo themes suggested a natural combined effect - e.g. Gold's
growth + Red's vitality = a spell that's both at once. All in
[HeartCoreBlockEntity.java](src/main/java/com/patrickma/magiccircles/block/entity/HeartCoreBlockEntity.java)
unless noted otherwise.

**Blue + Purple - Tempest Ward (120 mana).** `HeartCoreBlockEntity#startTempestWardSpell`
spawns the same unbreakable orb sphere as Shield (see above - `spawnShieldOrbs` is shared
between the two), but for a fixed 45 seconds rather than an ongoing drain, and offensively
rather than passively: every 2-5 seconds while it's up, it strikes a real `LightningBolt` at a
random hostile `Monster` caught within the sphere's radius (no strike if none are in range).
Storm's rain calls the clouds; this is a smaller, personal version of the same lightning aimed
at whatever's threatening the circle.

**Blue + Red - Cleansing Rain (50 mana, one-shot).** `HeartCoreBlockEntity#castCleansingRainSpell`
strips every negative `MobEffectInstance` and puts out fire on every `LivingEntity` within a
12-block radius. Storm's water without the water - a purely restorative reading of Blue.

**Purple + Green - Mana Font (10 mana, channeled, Wellspring-gated).** `HeartCoreBlockEntity#startManaFontSpell`
doesn't spend the heart's mana at all beyond that minimal 10 to "prime" it - instead, over 60
seconds it *adds* 15 mana back to the heart every 3 seconds (capped at the same 1000-mana
ceiling `HeartstoneItem` uses), the only spell that's a net mana gain rather than a cost.
Shield's mana-holding + XP's "generate something from nothing" theme, aimed at the heart's own
reserves instead of the player. Casting it requires a block tagged
[`magiccircles:wellspring`](src/main/resources/data/magiccircles/tags/blocks/wellspring.json)
within 15 blocks of the ring's center (`HeartCoreBlock#hasNearbyWellspring`) - an earlier version
of this spell let it regenerate mana "from the air" unconditionally, which didn't fit the mod's
lore (mana is only ever meant to come from a real Wellspring) or its economy (free, unlimited
mana regen undercuts every other spell's cost entirely). That real Wellspring now exists: the
9x9 pool at the base of [the World Tree](#the-world-tree), full of
[Wellspring Water](#wellspring-water) and tagged accordingly, so this is the one spell in the
mod that's tied to one specific permanent landmark rather than castable anywhere a ring can be
drawn. While it's running, the wisps orbiting closest to that pool visibly stream into the
heart casting it - see [Wellspring Water](#wellspring-water) for that animation.

**Gold + Red - Bloom of Life (80 mana, one-shot).** `HeartCoreBlockEntity#castBloomOfLifeSpell`
runs Flowers and Heal together (both share their underlying helpers, `growAround` /
`healAround`), and on top of the full heal, gives every player caught in the heal a 10-second
Regeneration II buff. Flowers' growth + Heal's vitality, quite directly.

**Gold + Green - Verdant Harvest (50 mana, one-shot).** `HeartCoreBlockEntity#castVerdantHarvestSpell`
is Flowers' search-and-place logic (the same `growAround` helper, retargeted) aimed at
farmland instead of grass, growing wheat, carrots, or potatoes straight to their max age
(`BlockStateProperties.AGE_7`) rather than a decorative flower, plus a few bonus experience
orbs - growth (Gold) turned into something you can actually harvest (Green, XP's "something
for nothing" theme again).

**Red + Green - Sylvaine and the Gleaner's Vigor (drains 50 mana/minute, no upfront cost).**
`HeartCoreBlockEntity#startVitalSurgeSpell` grants every player within 12 blocks Strength II,
Speed II, and Absorption II - re-applied every 5 seconds for as long as the spell holds, rather
than a single fixed-length buff, and draining the heart exactly like the Shield spell (50 mana
a minute) instead of a one-time charge. Heal's vitality and XP's energy read together as an
ongoing combat buff rather than a one-shot restorative - and being ongoing, it's one of the
spells the 6 white heart wisps (see above) show is still active.

## Opening a portal to the Fairy Realm

A second, different ritual shape - "Zuzo's Crossing" in [the Book of the Faye](#the-book-of-the-faye) -
opens a portal, cast the same way as any other Heart Core spell (Fairy Horn, enough mana) but
matched by an entirely different pattern than the rounded 12-ring the color-based spells use.
Build this with Chalk, Gold Chalk, and plain redstone dust (bird's-eye; `0` = must be left
empty, `a` = either chalk color, or a Heart Core once one's there):

```
r n g n r
g 0 0 0 g
n 0 a 0 n
g 0 0 0 g
r n g n r
```

This uses the full 5x5 border with specific materials per position - see
[ritual/PortalRitual.java](src/main/java/com/patrickma/magiccircles/ritual/PortalRitual.java).
The pattern is a palindrome in both directions, so it's checked both "as drawn" and with
rows/columns swapped, which is what makes it work whether you build it running along X or Z
- no need to separately handle all four rotations.

Once the border is complete, use a Heartstone on the center rune exactly like any other
circle - `HeartstoneItem#useOn` accepts either this pattern or the usual 12-ring as a valid
center, so a Heart Core can exist here too.

**Before casting, dig a water pit around the heart.** The 8 cells immediately surrounding the
center - the same 8 cells this pattern requires to be *empty* just to build the border in the
first place - each need a column of water starting one block below the ring's own level (the
solid ground the runes and the Heart Core actually sit on, not their own grid position - two
below the *floating* heart, if that's easier to eyeball in practice): a real water *source*
block at that depth, and then 3 more blocks of *any* water (source or flowing) below that
(`FairyPortalManager#hasWaterPit`). In practice this is one bucket per column, not four: place
a source block at the top of each hole and it flows straight down to fill the rest on its own,
the same as any vanilla water source dropped into an empty shaft. Casting without this in place
answers with a `portal_needs_water` message rather than doing anything.

From there, a Fairy Horn use checks for this exact pattern *before* the color-ring checks
(which would otherwise always fail here and print "ring broken," since the rounded ring's
corners have to be empty and this pattern deliberately fills them - see `MagicCircleRitual`'s
doc comment on why the two shapes are mutually exclusive by design) - specifically
`PortalRitual#matchesBorder`, not `#matches`: by the time anyone's ready to cast this, the 8
cells `#matches` requires to be air hold water instead, so a separate check that simply
doesn't look at those 8 cells is what actually fires the spell. If the border matches, the
water pit is in place, and the heart holds at least 500 mana, that gets spent and
[FairyPortalManager.java](src/main/java/com/patrickma/magiccircles/FairyPortalManager.java):

1. Converts all 32 water blocks (8 columns x 4 deep) into
   [FairyPortalWaterBlock](src/main/java/com/patrickma/magiccircles/block/FairyPortalWaterBlock.java) -
   a real, distinct fluid (`ModFluids`/`ModFluidTypes`), not vanilla water reused. That
   distinction matters twice over: sharing `Fluids.WATER` between two different blocks risks
   the fluid engine's own flow/update logic silently reverting this back to plain water on its
   own schedule (its `createLegacyBlock()` always resolves to vanilla's block) - outside this
   mod's control - and being a real fluid (rather than an earlier, simpler non-solid *block*
   that only pretended to act like water) is what gives it genuine swim physics and the
   vanilla air-bubble HUD for free. Both come from `FluidType` (`canSwim`/`canDrown`), read
   generically by Forge's own breathing code rather than hardcoded to vanilla water
   specifically (confirmed by decompiling `ForgeHooks#onLivingBreathe` - the old, literally
   water-specific vanilla breathing check is dead code under Forge, guarded by an `if (false)`
   in `LivingEntity#baseTick`), so a fluid with its own swirly, lighter-blue animated texture
   (`tools/gen_portal_water_texture.py`, built to tile edge-to-edge with no transparent
   corners - an earlier version faded to transparent near each tile's corners, which read as
   visible gaps between adjacent portal-water blocks) still gets full "acts like water"
   treatment.
2. Builds a matching structure somewhere in the Fairy Realm from scratch - the same 5x5 border
   (copied block-for-block from the one you drew), a fresh center rune of whatever color the
   original was (no Heart Core there - it's a crossing point, not a second castable circle),
   and the same 32-block portal-water pit, generated directly rather than requiring anyone to
   dig it. Each new portal gets its own never-reused spot, spread out across the island via the
   same golden-angle/Fibonacci spread `HeartCoreBlockEntity`'s shield sphere uses elsewhere in
   this mod (`FairyPortalManager#allocateDestSlot`), so multiple portals never collide.

Unlike every other spell, this one can be recast (each cast spends another 500 mana and opens
another portal) - there's no "already open" tracking the way the free, automatic version this
replaced used to have, since now that it costs a real resource each time, recasting it is just
spending that resource again rather than something that needs to be specially prevented.

**Crossing is by drowning, not walking through.** Submerge in the portal water - either side -
and hold your breath; the air-bubble HUD and the actual breath countdown are entirely vanilla's
own (see above - genuine air drain needs a genuine fluid). The moment your air would actually
run out, `FairyPortalManager#onLivingDrown` intervenes: it listens for Forge's
`LivingDrownEvent` (fired every tick a living entity's air is at or below zero - the direct,
generic successor to that dead vanilla water-breathing check), and if the drowning entity is
standing in a tracked portal's water, cancels the event outright - skipping the vanilla drown
damage and its bubble-burst particles entirely - resets their air to full, and teleports them
to the matching spot on the other side instead. This isn't player-only: any `LivingEntity` that
drowns in the pit crosses over the same way, so an animal (or a monster, if one wanders in)
that swims in and stays under gets carried across right along with a player - `LivingDrownEvent`
already fires generically for any living entity, and `Entity#teleportTo(ServerLevel, ...)`
already handles a cross-dimension move generically too (recreating the entity on the other side
for anything that isn't a player, exactly like vanilla's own portal/`/teleport` code), so no
separate mob-handling branch was actually needed - only the bookkeeping around *closing* the
portal still cares specifically whether the crosser was the human who opened it. Crossing back
the same way, once you've reached the other side, closes the portal - both sides' water reverts
to plain water and the crossing stops working - but only once *the player who originally cast
it* has made the full round trip; anyone (or anything) can use an open portal to cross, but only
the opener's return closes it.

**Arrival lands you at the very bottom of the pit, not the surface.** Both sides' landing spot
is the deepest cell of one fixed pit column (the same corner on both sides, so a crossing feels
consistent in either direction), so stepping through means actually swimming up through all 4
blocks of portal water to get out, rather than popping out already floating at the top.

**Both sides always close together**, even outside the normal round-trip/broken-border paths
above - `FairyPortalManager#onServerTick` checks, once a second, whether every one of an active
portal's water cells (both sides) is still real `FairyPortalWaterBlock`; the moment either side
isn't (mined out block by block, paved over with something solid, blown up, anything other than
going through the mod's own closing logic), the *other* side closes to match rather than staying
"open" - still tracked, still the special fluid - after the pair as a whole has effectively
already stopped working on one end.

**Sound and the underwater tint are hand-reproduced, not inherited for free** - and both are now
shared between the portal fluid and [Wellspring Water](#wellspring-water), since the exact same
gaps apply to any custom fluid, not just this one. Unlike breathing (generic per-`FluidType`,
see above), decompiling `Entity#isInWater`/`#updateFluidHeightAndDoFluidPushing` turned up a
second, opposite case: both are hardcoded (behind a `@Deprecated // Forge: Use ... instead`
legacy path) to check identity against Forge's own vanilla-water `FluidType` singleton
specifically, bypassing the generic `FluidType` system entirely - so no custom `FluidType`,
however water-like its properties, can ever satisfy them. That's what gates every one of
vanilla's own water sounds, so a distinct fluid (needed for its own texture) gets none of them
for free - all of the following are hand-reproduced instead, keyed off
`Entity#isInFluidType`/`#isEyeInFluidType`, the same generic per-tick checks
`ForgeHooks#onLivingBreathe` already uses for drowning:

- **Splash and swim**, in
  [block/WaterLikeFluidSounds.java](src/main/java/com/patrickma/magiccircles/block/WaterLikeFluidSounds.java) -
  `SoundEvents.GENERIC_SPLASH`/`GENERIC_SWIM`, at the entity's own `getSoundSource()` category
  (`PLAYERS` for a player, whatever's natural for a mob), plus the same splash/bubble particle
  burst, keyed off a per-entity "last touched this tick" timestamp to detect a fresh entry. A
  fast entry (diving, falling in from height) gets the louder `PLAYER_SPLASH_HIGH_SPEED` instead,
  for a player specifically - vanilla doesn't actually have a generic high-speed splash sound;
  every other entity type just reuses the same one splash regardless of speed, so that's what
  this reproduces too. An earlier version drove all of this off `Block#entityInside` instead -
  correct-looking on paper (it does fire for every block an entity's hitbox overlaps, fluid
  included) but never actually audible in testing, for a reason that was never fully pinned down;
  rebuilding it on `LivingTickEvent` removed that uncertainty rather than continuing to debug the
  original mechanism blind. This originally lived directly on `FairyPortalWaterBlock` itself,
  hardcoded to just the portal fluid - pulled out into its own class, checking every entry in
  `WaterLikeFluidSounds#WATER_LIKE_FLUID_TYPES`, once Wellspring Water needed the exact same
  treatment. That list holds `RegistryObject<FluidType>`, not resolved `FluidType`s - an early
  version resolved them eagerly in a static field initializer and crashed mod loading outright
  (`NullPointerException: Registry Object not present`, since that runs well before the registry
  event that actually populates a `RegistryObject`), caught by an actual failed boot rather than
  assumed safe.
- **The enter/exit whoosh and the continuous underwater hum**, in
  [client/WaterLikeAmbientSounds.java](src/main/java/com/patrickma/magiccircles/client/WaterLikeAmbientSounds.java)
  (renamed from `PortalAmbientSounds` once it needed to check more than one fluid) - modeled
  directly on `LocalPlayer#updateIsUnderwater`/`UnderwaterAmbientSoundInstances` (decompiled for
  reference, since their own constructors are `protected`/package-private and can't just be
  reused directly): `AMBIENT_UNDERWATER_ENTER`/`_EXIT` fire once on each eye-submersion
  transition, and a looping `AMBIENT_UNDERWATER_LOOP` fades in/out and self-stops the same way
  vanilla's own does, just checked against `isEyeInFluidType` instead of
  `LocalPlayer#isUnderWater()`. The occasional random bubble/gurgle "additions" (three rarity
  tiers, exactly matching `UnderwaterAmbientSoundHandler`'s own chances) are reproduced the same
  way.

**The full-screen tint the camera gets while submerged turned out to have a real, previously
hidden bug, not just an unlucky choice of alpha.** Three earlier passes (documented honestly in
`tools/gen_portal_underwater_overlay.py`'s own docstring rather than smoothed over) pushed the
overlay texture's own alpha higher and higher - a fairly saturated blue at a lower alpha than
vanilla's own `underwater.png`, then a near-white tint at increasing alpha, up to 225-245 out of
255 - and every single one was reported as producing no visible tint at all. That stopped looking
like "needs more alpha" and started looking structural, so decompiling
`ScreenEffectRenderer#renderFluid` (what every `IClientFluidTypeExtensions#getRenderOverlayTexture`
override ends up calling, via the default `renderOverlay`) turned up the actual cause: it
hardcodes `RenderSystem.setShaderColor(f, f, f, 0.1F)` - a **global alpha multiplier of exactly
10%**, applied on top of the overlay texture's own alpha, baked directly into that method's body
where no override could ever reach or bypass it. Pushing this mod's own texture alpha from 95 to
245 only ever moved the *real* effective alpha from about 3.7% to 9.6% - both essentially
invisible for a deliberately pale color, which is exactly the "changing the alpha does nothing"
symptom that kept showing up. Vanilla's own tint gets away with the same 10% cap because it's a
strongly saturated blue - a hue shift stays visible even at ~5% real alpha - but a pale, near-
white tint doesn't get that for free.

The fix,
[client/ModFluidOverlayRenderer.java](src/main/java/com/patrickma/magiccircles/client/ModFluidOverlayRenderer.java):
both fluid types now override `renderOverlay` directly instead of relying on the capped default -
a plain copy of `renderFluid`'s own quad/shader setup with exactly one line changed, so the
caller supplies the alpha instead of a hardcoded `0.1F`. With that cap actually gone, the
texture's own alpha is finally the *only* multiplier, so it's dialed back down from the
diagnostic-ceiling values above to 150-180 - strong enough to read as a real, present tint
without blocking the view outright the way a flat 255 would now that nothing quietly divides it
down to a tenth of that anymore. Both `ModFluidTypes.PORTAL_WATER` and `ModFluidTypes.WELLSPRING_WATER`
use the identical overlay texture and the identical override.

Arrival is computed live from each side's actual heightmap
(`ServerLevel#getHeight`/`LevelReader#getChunk(int, int)`, forcing the chunk to generate first)
rather than a hardcoded Y coordinate - an earlier, single-shared-arrival-point version of this
portal hardcoded its destination Y to match `FairyRealmChunkGenerator`'s layer stack by hand,
and players arriving could find themselves below the island's actual surface, falling through
the void beneath it with no ground in sight to explain why. Querying the real heightmap live
rules that out regardless of whether the original mismatch was in the math or just in the
destination chunk not existing yet the first time anyone arrived, and keeps working even if the
generator's layer stack changes later.

## The Fairy Realm's island

[worldgen/FairyRealmChunkGenerator.java](src/main/java/com/patrickma/magiccircles/worldgen/FairyRealmChunkGenerator.java)
generates a single circular island, 250 blocks in radius (500 across), with every column
outside that radius left completely untouched - true void, not water or bedrock, which is what
actually produces the "floating island" look. This used to be a uniformly flat disc (the same
8-layer stack everywhere); it's now shaped, driven by two small helper methods (`rawColumnAt`,
which every other method in the class calls through rather than repeating any shape logic, and
`columnShapeAt`, which adds one more thing on top of it - see the river paragraph below):

- **A flat clearing at the center** (`FLAT_RADIUS`, 100 blocks) - always exactly the same height
  regardless of direction, comfortably past the World Tree's own reach so nothing about the tree
  or its well ever touches sloped ground.
- **Real Perlin-noise mountains, rising toward the middle of the island everywhere except a wide
  corridor facing +Z** - the tree's own entrance direction (see
  [The World Tree](#the-world-tree)) - which stays flat and open all the way out to the true
  edge instead, so there's always a clear walk between the tree and
  [the portal ruins](#the-portal-ruins) without climbing anything. The mountain contribution is
  shaped by three independent smoothstep tapers multiplied together (`mountainEnvelope`): one
  that ramps up gradually from the flat center outward (so the flat clearing melds into real
  terrain rather than meeting it at a hard seam), one that ramps back down to nothing right at
  the island's true edge (so mountains always meet flat ground exactly at the boundary instead
  of getting cut off mid-slope by the void), and one that keeps the entrance wedge itself flat
  with a smooth angular blend at its own edge. Vanilla's own `PerlinSimplexNoise` (not a
  hand-rolled noise function) drives the actual bumps, normalized and raised to a power so most
  of the terrain reads as rolling hills with real peaks only where the noise is already strongly
  positive, rather than bumps distributed evenly everywhere.
- **A meandering river tracing the outer edge** through that same hilly two-thirds of the
  island, its wobble a function of compass angle (a small, fixed sum of sine harmonics -
  deliberately *not* part of the seeded noise, so the river's own path stays simple regardless of
  which mountain seed gets chosen) rather than a straight ring, carved down into a proper bed
  (sand on top, gravel below, not the grass-topped soil every other column gets) and filled with
  water. It doesn't close into a full loop - at both ends, right where it meets the entrance
  corridor, its course curves outward until it reaches the island's true edge, so both ends read
  as "the river spills off into the void here" rather than stopping at a straight line.
  **The water is always contained by ground at or above its own level** - `columnShapeAt` checks
  every non-river column's 8 neighbors and raises its own height to match any neighboring river's
  water surface, if that surface is higher. This exists because of a real, reported artifact:
  computing every column's height completely independently (the original approach) occasionally
  let the river's water poke out over neighboring land that landed, by pure noise coincidence, a
  little lower - the fix was confirmed at exact block resolution by reading back a real river's
  two banks (both sit 1-2 blocks above the water immediately next to them, on a live-generated
  world) rather than just reasoning about the code.
- **The underside tapers to a rough point below the center** instead of cutting off flat like a
  slab - deepest at the very center, shallowest near the true edge, with a bit of noise so the
  taper itself reads as an uneven, natural point rather than a mathematically perfect cone. This
  is what used to make the island look, from below, "like a chunk of the map cut out by a cookie
  cutter" - a flat-bottomed disc with only the circular outer edge as any kind of shape at all.

**The mountain (and underside) noise uses a real random seed, chosen once and recorded - not a
fixed constant, and not tied to the actual world's own seed.** `ensureTerrainReady` - called
explicitly by both [WorldTree](#the-world-tree) and [the portal ruins](#the-portal-ruins) before
either force-loads a single chunk of their own, which is what guarantees this runs before any
real terrain generation happens - either loads a seed already recorded for this world, or, the
first time only, tries random seeds until one actually produces a column tall enough to read as
a real mountain (85 blocks above the flat base) before recording it, so every later boot of the
same world reuses that exact terrain rather than rerolling it. In practice this resolves almost
immediately - a coarse grid scan across the island reliably finds a tall-enough point within a
handful of tries (2, 5, and 14 attempts across three separate test worlds), not the many rerolls
"keep generating until..." might suggest. The river's own meander is deliberately *not* part of
this seeded noise (see above) - only the mountains and the underside taper are actually random
per world.

Registering the generator's own codec needed one thing worth calling out: the plain
`BuiltInRegistries.CHUNK_GENERATOR` registry is already frozen by the time mods run, so (like the
creative tab in `ModCreativeTabs`) this goes through a `DeferredRegister` targeting the vanilla
registry key (`Registries.CHUNK_GENERATOR`) instead of a raw registration call - see
[registry/ModWorldgen.java](src/main/java/com/patrickma/magiccircles/registry/ModWorldgen.java).
Structures are disabled outright (`createState`), since normal structure placement assumes
continuous terrain, not an isolated disc.

**The vegetation reuses vanilla's own jungle generation code, not a hand-rolled tree.** The
biome (`worldgen/biome/fairy_realm.json`) used to be a copy of plains, including its
`minecraft:trees_plains` feature - scattering ordinary small oak trees across the *entire*
island, tree included, which is exactly what a report of "a remnant of the old ugly tree still
generating on top of the new one" turned out to be: not leftover code (there wasn't any left),
but vanilla's own generic tree feature dotting oak saplings right through and around the God
Tree's own canopy. Swapping that out was the fix, and it was a natural excuse to lean into an
actual jungle feel at the same time: `downfall`/`temperature` now match vanilla's own jungle
biome (denser rain, more saturated foliage), and the vegetation feature list is jungle's own
(`bamboo_light`, `flower_warm`, `patch_grass_jungle`, `vines`, `patch_melon`) with one
deliberate substitution -
[worldgen/placed_feature/trees_big_jungle.json](src/main/resources/data/magiccircles/worldgen/placed_feature/trees_big_jungle.json)
in place of `minecraft:trees_jungle`. Vanilla's own jungle tree feature is actually a weighted
mix (10% fancy oak, 50% jungle bush, 33% mega jungle tree, else a normal small jungle tree) -
this points at the same underlying `minecraft:mega_jungle_tree` *configured* feature (vanilla's
own tree-shape code, not reimplemented) with vanilla's own density/placement chain
(`trees_jungle.json`'s count/in_square/water-depth/heightmap/biome filters) plus vanilla's own
survivability check (`mega_jungle_tree_checked.json`'s `would_survive` predicate), so what
actually spawns is jungle-density, but *only* ever the big 2x2 variant - never the small tree,
bush, or oak vanilla would otherwise mix in.

### The sky: six drifting streaks, not a frozen sunset

The dimension type's `"effects"` field points at `magiccircles:fairy_realm` instead of
`minecraft:overworld`, which
[client/FairyRealmEffects.java](src/main/java/com/patrickma/magiccircles/client/FairyRealmEffects.java)
registers (`ClientSetup#registerDimensionEffects`, on `RegisterDimensionSpecialEffectsEvent`).
This still reuses vanilla's own `SkyType.NORMAL` dome/sun/moon/star rendering rather than
replacing sky rendering outright - `DimensionSpecialEffects` does expose a hook for that
(`renderSky`, returning `true` suppresses vanilla's own sky entirely), but that means
hand-writing a new sky mesh with no way to preview the result short of an actual client, just to
add a few extra shapes on top of what vanilla already draws well.

An earlier version colored that dome a deep, saturated blue and forced `getSunriseColor` to
*always* return a warm gold (since this dimension's `fixed_time` locks the sun at noon, vanilla's
own version - which only returns non-null near actual sunrise/sunset - would otherwise never fire
at all), turning the normal "sunset glow" band into a permanent golden ring under the dome. That
read as exactly what it was - a sky frozen at one moment - so both are gone now:

- `worldgen/biome/fairy_realm.json`'s `effects.sky_color` is a muted, dusky tone instead, a
  neutral backdrop rather than something competing with the actual color story.
- `getSunriseColor` is no longer overridden at all - there's no permanent glow band to force on
  anymore.
- [client/FairyRealmSkyStreaks.java](src/main/java/com/patrickma/magiccircles/client/FairyRealmSkyStreaks.java)
  replaces it: six independent ribbons of color - blue, gold, purple, red, and green (every
  `RuneColor`'s own wisp color) plus white - each arcing from near the horizon up toward the
  zenith at its own fixed compass direction, each slowly rotating around the sky at its own
  independent rate so they drift and cross rather than sitting still. Hooked on
  `RenderLevelStageEvent.Stage.AFTER_SKY` ("render custom objects into the skybox," fired
  regardless of whether the sky itself actually rendered) rather than touching
  `DimensionSpecialEffects` at all - purely additive, one extra draw call per streak after
  everything vanilla already draws, with none of the risk of replacing sky rendering outright.
  Each streak is a plain color-only ribbon (`POSITION_COLOR`, the same vertex format vanilla's
  own sunrise-glow fan uses - no texture), alpha fading to 0 at both ends and peaking in the
  middle, so it reads as a soft streak rather than a hard-edged wedge.

### The World Tree

A single, massive, permanent "God Tree" house stands at the exact center of the island (world
X/Z 0,0) - see
[worldgen/WorldTree.java](src/main/java/com/patrickma/magiccircles/worldgen/WorldTree.java). This
used to be a hand-written procedural tree (a cylinder trunk, sphere leaf clusters, straight
diagonal branches); it worked, but looked exactly like what it was - simple geometry. It's now a
real player-built structure instead, captured block-for-block from a professional Minecraft
builder's own world save and baked into the mod as a compact resource
(`data/magiccircles/structures/god_tree.bin.gz`, ~945 KB compressed, 486,603 blocks, a
454-entry block-state palette).

**How it got from "someone else's world save" to a mod resource:** three small Python scripts
(scratch tooling, not part of the mod itself) - a bounding-box scanner to find the tree in the
save, a full non-air block extractor (`anvil-parser2` reading the save's region files), and a
converter into a compact binary format designed to minimize resource size: a magic string, a
deduplicated block-state palette (name + properties), then one record per block (relative X/Y/Z
as a single byte each, since every dimension here is under 256, plus a 2-byte palette index),
gzip-compressed. This is a custom format rather than vanilla's own `StructureTemplate` NBT,
partly for the smaller size and full control, but mostly because vanilla's structure-placement
machinery doesn't fit here at all - see the next paragraph.

**Placement is a one-time world edit, not a per-chunk worldgen hook or a vanilla structure
placement.** At ~487,000 blocks spanning roughly 10x10 chunks, this is far too large to paste
from inside `ChunkGenerator#fillFromNoise` (called per-chunk, with only a small region of nearby
chunks actually accessible - nowhere near enough for something this wide) or through vanilla's
`StructureTemplate` placement (built around the same per-chunk-generation constraints). Instead,
`WorldTree#placeIfNeeded` runs once, the first time the server ever starts with this mod
installed: a `ServerStartedEvent` hook force-loads every chunk the structure and its well touch,
then writes each block directly via `ServerLevel#setBlock` - a plain world edit against an
already-existing, fully-accessible level, with none of chunk-generation's access restrictions.
Whether it's already happened is tracked by a tiny `GodTreeSavedData` (`SavedData`) flag on the
Fairy Realm's own level, so a restart never repeats it.

`WorldTree#carveWell` runs immediately after placement, replacing a 9x9 area near the tree's base
with a decorated well - see [Wellspring Water](#wellspring-water) below for what fills it and
why. Every storage container the source structure had (`minecraft:chest`, `ender_chest`,
`barrel`, `shulker_box` - confirmed by dumping the extracted palette rather than guessing) is
replaced with air rather than placed, in `WorldTree#resolveBlockState` - one small, deliberate
exception to "placed exactly as extracted" below, so a house this size doesn't need a treasure
hoard's worth of chests stocked by hand just to avoid looking unfinished (their contents were
never captured in the first place either way - the compact structure format only stores
block-state, not block-entity inventory, so every one of them would have placed empty regardless).

**`WorldTree#clearInterior` fixes a real gap in the placement approach, not just a cosmetic one.**
Placement itself is purely additive - it only ever adds blocks, never removes any - so whatever
the chunks already had *before* the tree was pasted over them stayed exactly where it was,
including inside the tree's own hollow interior spaces. That's exactly what "the interior of the
tree is full of jungle leaves" turned out to be: the biome's own big-jungle-tree feature (see
"The Fairy Realm's island" above) had already scattered real trees through that whole area - it's
ordinary terrain until the tree structure is placed on top of it - well before the one-time
placement event ever ran, and a purely-additive paste had no way to remove any of it.
`clearInterior` runs right after placement and clears every position within the structure's own
bounding box that *isn't* part of the captured structure back to air: the compact format only
ever recorded non-air blocks in the first place, so anything missing from that data was genuinely
air in the source build, whether that's open sky above the canopy or a hollow room inside the
trunk. This is a large volume to scan (the bounding box is several times the ~487k blocks
actually captured), but skipping any column already air keeps the real write count far lower in
practice - confirmed on a live boot, which cleared roughly 98,000-100,000 blocks (varying slightly
by mountain seed - see below) in under ten seconds.

**Validated by actually booting a dev server against a real save and reading back what got
written**, not just "it didn't crash": the placement log (`Placing the World Tree (486603
blocks)...` / `World Tree placement complete.`) showed no exceptions across all ~487k
`setBlock` calls plus the well carve, and a follow-up read of the world's own region files
(`anvil-parser2`, the same tool used for extraction) at several key coordinates confirmed the
well's water column is genuinely `magiccircles:wellspring_water` top to bottom, the floor/rim/
corner blocks are exactly what `carveWell` specifies, and real structure blocks (spruce planks
from the source build) sit immediately outside the well where the tree's own geometry should be
- not just a log line claiming success.

**One of those read-backs found a real, one-block seam, and fixed it.** A report that "the
ground below the tree isn't level with the rest of the realm" turned out to be exactly that:
parsing the structure resource directly showed its own embedded ground plane (columns away from
the trunk, e.g. a plain `minecraft:grass_block`) landing at world Y=103 with the original
`OFFSET_Y` (95) - one block *above* the island's own topmost solid layer (Y=102). `OFFSET_Y` is
94 now, confirmed by the same direct-parse-then-read-back technique: a sampled column now shows
`grass_block` at Y=102 and open air at Y=103, flush with the island. The well's own vertical
position (`carveWell`, anchored to `FairyRealmChunkGenerator#groundY` directly rather than to
`OFFSET_Y`) was never affected by this bug and didn't need a matching change - moving it to
"fix" something that measured correctly already would have reintroduced the exact seam this
fixes, just at the well instead of the tree.

**The well also moved, 2 blocks +X and 1 block +Z from the tree's own center** (see `WELL_CENTER_X`/
`WELL_CENTER_Z`), then sunk 3 blocks below the island's normal ground level on top of that
(`WELL_Y_OFFSET`) - a deliberate recessed basin (a small step down to reach the rim, then down
again to the water) rather than a box sitting flush with the surrounding grade. The interior's
own air clearance above the water bumped from 2 blocks to 3 to match
(`WELL_CLEARANCE_ABOVE`) - a piece of the source build's own upper floor (carpet included) used
to pass near the well's spot at a height `carveWell`'s rim/corner overwrite never reached,
leaving it floating over the water untouched, and moving the well down meant that floor had that
much further to clear before reaching open air.

**Worth knowing, honestly:** a few things here are a deliberately simple first pass, not a
finished feature:

- **Every active portal is tracked in a plain in-memory map**, not saved-game data - a server
  restart loses track of which portals are open, who opened them, and where their matching
  structures are, even though the physical water/rune blocks are still there. This needs
  Forge's `SavedData` before it's more than a first pass.
- **Only the very first portal ever opened lands somewhere purpose-built** (see
  [The Portal Ruins](#the-portal-ruins)) - every portal after that still falls back to the
  golden-angle spread across the island, carving straight into whatever terrain happens to be
  there (now hills, river, or jungle canopy, rather than a uniform flat disc) rather than
  searching for or clearing a good spot first.
- **The structure is placed exactly as extracted** (containers aside, see above) **with no other
  attempt to blend it into the island's terrain** - `OFFSET_X/Z` were chosen from the source
  save's own trunk-center estimate and never re-verified as precisely as `OFFSET_Y` was (see
  above); a similar seam along X or Z, if one exists, hasn't been looked for the same way.
- **The one-time placement has no undo and no way to re-run it deliberately** - once
  `GodTreeSavedData#placed` is true, nothing short of manually clearing that flag (or deleting
  the Fairy Realm's saved data) will place it again, which is deliberate (a second placement
  pass over the same area would just double up decorative blocks like the well's rim/corners)
  but worth knowing before assuming a config change or a mod update will "fix" a bad placement
  on an existing world.
- I still haven't *seen* any of this - validation here is log output and read-back block states,
  not a screenshot, so I can't speak to how the structure actually reads visually next to the
  island, whether the well looks right against the tree's own base, or how the wisps look in
  motion.

### Wellspring Water

The well `WorldTree#carveWell` carves near the tree's base - a mossy-brick rim (Sea Lanterns at
each corner) around a 7x7 shaft, 8 blocks deep - isn't filled with plain water. It's a real,
distinct fluid,
[`ModFluidTypes#WELLSPRING_WATER`](src/main/java/com/patrickma/magiccircles/registry/ModFluidTypes.java)
backing
[`WellspringWaterBlock`](src/main/java/com/patrickma/magiccircles/block/WellspringWaterBlock.java)
(a plain `LiquidBlock` subclass - it has no special *gameplay* behavior of its own the way the
portal fluid's crossing mechanic does; it's a purely decorative/thematic pool, "this water holds
mana," not "step in and something happens"), for the same reason the portal spell's water is a
real fluid rather than reused vanilla water: its own animated, color-cycling texture
(`tools/gen_wellspring_water_texture.py`), cycling through blue, gold, purple, red, and green -
every `RuneColor`'s own wisp color - plus white, in one continuous 60-frame loop. Being a real
`FluidType` (not just a block) also means swim/drown physics, the vanilla air-bubble HUD, and
now the same splash/swim/ambient sounds and underwater screen tint as the portal fluid all work
correctly for free (or as close to free as a custom `FluidType` gets - see
[Opening a portal](#opening-a-portal-to-the-fairy-realm) for why none of the sound/tint part
is actually free at the engine level, and why both fluids now share the same fix for all of it).

This is also the actual, functional Wellspring
[`HeartSpell#MANA_FONT`](src/main/java/com/patrickma/magiccircles/ritual/HeartSpell.java) checks for -
`data/magiccircles/tags/blocks/wellspring.json` tags `magiccircles:wellspring_water`
specifically, replacing an earlier placeholder version of this tag that pointed at plain
`minecraft:sea_lantern` (which would have matched *any* Sea Lantern anywhere, not just this one
pool - a real, now-resolved imprecision).

**The wisps here do two things, in two separate systems:**

- [`client/WorldTreeWisps.java`](src/main/java/com/patrickma/magiccircles/client/WorldTreeWisps.java) -
  100 wisps orbiting the *entire* tree, from just above the roots to the very top, at every
  radius out to the tree's own farthest reach (`WorldTree#maxRadius()`/`topY()`, read back from
  the loaded structure rather than a hardcoded guess).
- [`client/WellspringWisps.java`](src/main/java/com/patrickma/magiccircles/client/WellspringWisps.java) -
  a second, smaller set orbiting close around the well itself (radius roughly 2.5-7.5 blocks,
  just above the water), cycling the same six colors as the water's own texture. While a nearby
  Heart Core has Mana Font running, the handful of wisps already orbiting closest to the well
  peel off one at a time, fly in a straight line into that heart as if being drawn in and
  absorbed, then reappear back at the well to rejoin their orbit - a continuous stream for as
  long as the spell runs. Every farther-orbiting wisp (in either system) is unaffected, which is
  what makes it read as "the closer ones" rather than a blanket effect. Finding the active heart
  is a small scan of already-loaded chunks around the well every few ticks
  (`ChunkSource#getChunkNow`, which never forces a chunk load of its own) rather than a direct
  link to any specific block entity - there's no other way for a stand-alone, fixed-position
  client system like this one to know about a nearby, otherwise-unrelated block entity's state.
  `HeartCoreBlockEntity#isManaFontActiveSynced()` is a small client-side mirror added
  specifically so this system has something to read, following the same
  get/handleUpdateTag pattern the heart's other client-visible flags already use.

Both wisp systems are purely client-side visuals, built the same way as
`ClientCircleWisps`/`ClientHeartWisps` - deterministic orbits seeded once rather than anything
eased toward a moving target, except for the handful of wisps mid-absorption, which are the one
exception to that rule in either system.

### The Portal Ruins

The very first portal anyone ever opens doesn't land in open air - a report that it "spawns in
the middle of the bushes of the tree" was exactly that: the golden-angle spread
`FairyPortalManager#allocateDestSlot` uses for every portal's destination happened to put slot 0
close enough to center (radius 48) to land inside the God Tree's own outer canopy. Instead, that
first slot is special-cased to a fixed, purpose-built location -
[worldgen/FairyPortalRuins.java](src/main/java/com/patrickma/magiccircles/worldgen/FairyPortalRuins.java) -
a ruined stone chamber built into the base of an actual hill, along the tree's own entrance axis
(+Z, `WorldTree#maxRadius()` plus 25 blocks further out, so it never overlaps the canopy). Every
portal after the first still falls back to the golden-angle spread - a single shared arrival
point isn't safe once more than one portal can be open at once, since two concurrent portals
would otherwise fight over the exact same water pit.

Built the same way as `WorldTree` - a one-time world edit from `onServerStarted`, gated by its
own `RuinsSavedData` flag so a restart never repeats it - rather than anything procedural or
per-chunk. An earlier version buried the chamber 16 blocks under otherwise-flat ground behind a
rubble-filled shaft that had to be dug through, reported as reading like a tomb - both the depth
and the "dig your way out" mechanic are gone now, replaced with something built more like an
actual dwelling:

- **A natural-looking hill mounds directly on top of whatever terrain is already there** -
  a simple parabolic dome, queried live against the real generated ground height at every column
  (`ServerLevel#getHeight`, not assumed) rather than computed independently, so it sits correctly
  no matter what the terrain generator actually produced at this exact spot.
- **The chamber itself sits shallow** (8 blocks below the *original* ground, not 16) - a rough,
  irregular room (not a clean box) built from a weighted mix of stone, stone bricks, cobblestone,
  and their mossy/cracked counterparts, with a small chance per wall/ceiling cell of being left
  as a gap instead - a fixed, hardcoded `Random` seed decides which block goes where, so the
  ruin's own material pattern looks identical on every install regardless of which random
  terrain seed that particular world happens to have chosen (see "The Fairy Realm's island"
  above for why the terrain itself *isn't* fixed the same way anymore).
- **The portal stands on a small raised pedestal** at the chamber's dead center - a 5x5 dais one
  block taller than the surrounding floor - rather than flush with it.
- **A skylight** - a vertical shaft straight up from the ceiling (directly above the pedestal)
  through the hill, capped with glass right at the hill's own surface. Light gets in; it's a real
  ceiling from outside, not a hole rain or mobs could fall through.
- **A real, immediately walkable staircase** climbs from the chamber's near wall up through the
  hill to a proper cave-mouth archway - one step of real stairs per block of rise, 3 wide, with
  headroom carved above each step. Everything around the carved passage is left as the hill's own
  solid mass, which is what actually forms the tunnel's walls and ceiling; nothing separate needed
  building there. This entirely replaces the old rubble-filled shaft (and, with it, a real bug
  that shaft had - an isolated single layer of cobblestone that didn't match anything else in the
  chamber, blocking the one spot that was supposed to open up) rather than patching either.
- **A short paved apron outside the cave mouth**, and **an intermittently paved trail** (small
  paving patches every few blocks, not a solid road) running the straight line from there to the
  tree's own entrance. That entrance is stored as an X/Z offset from `WorldTree`'s own center
  (`TREE_ENTRANCE_X_OFFSET`/`TREE_ENTRANCE_Z_OFFSET`) rather than an absolute world position -
  given directly (confirmed by standing on both doorway blocks) rather than derived from the
  structure data, since there's no per-block "this is the door" information captured anywhere -
  specifically so the path stays correct if the tree's own placement ever moves, as long as the
  door stays in the same spot *relative to the tree itself*.

**A real ordering bug turned up during this rework, not just cosmetic issues**: `WorldTree` and
`FairyPortalRuins` are two independent `@SubscribeEvent` handlers with no guaranteed order
relative to each other. The path-paving above reads the *live* world surface near the tree
(`ServerLevel#getHeight`) - if `FairyPortalRuins` happened to run first, it would pave against
ground that didn't have the tree pasted onto it yet, and the tree's own placement (or its
interior-clearing pass) could then overwrite that paving entirely. Confirmed happening in
practice, not just theorized: two of four checked paving points were missing before the fix.
`WorldTree#placeIfNeeded` is public specifically so `FairyPortalRuins` can call it directly
before its own placement, removing any dependence on Forge's own event dispatch order - after
that fix, all four checked points showed real paving material.

Validated the same way as the tree: booting a dev server and reading back the placed blocks
confirmed the pedestal material, the skylight's glass cap at the hill's own surface height, real
stair blocks at the correct positions, and (once the ordering bug above was fixed) paving stone
at the exact calculated points along the path to the tree - not just a log line claiming the
placement finished without an exception.

## One fixed map, eventually

Most of the Fairy Realm's generation is deliberately deterministic regardless of the actual
world's own seed: the island's overall shape (flat center, entrance wedge, edge taper, river
path), the jungle vegetation rules, the sky streaks, and every one-time structure (the tree, the
well, the ruins, the path between them) all come from fixed constants and hand-picked math, not
anything keyed to the player's world seed. **The one deliberate exception is the mountain terrain
itself and the island's underside taper** (see "The Fairy Realm's island" above) - those use a
real random seed, chosen once per world and recorded rather than fixed, so the actual peaks and
bumps differ between installs even though everything built *on top of* that terrain doesn't. This
is a real, considered trade-off, not an oversight: a fixed-constant version of the mountains
existed first and was replaced on request in favor of noise that actually looks like terrain -
the recorded seed still gets every install a *stable* Fairy Realm (the same one every time that
world reloads), just not an *identical* one between different installs the way the rest of the
dimension is.

That distinction still lines up with the original goal, just at a smaller scope than "every
install matches byte-for-byte": once the island's shape, the tree's placement, and the ruins are
all actually finalized, the plan is still to generate one specific result, capture it, and ship
it as fixed data forever - the same "capture it once, place it as fixed data" approach
`WorldTree`'s own structure resource already uses for the tree itself, just applied to the whole
dimension. A recorded seed is exactly as reproducible for that purpose as a hand-picked constant
would have been - "finalize the map" just means picking one specific recorded seed's result to
bake in, rather than needing every install to have already independently arrived at the same
terrain on their own.

That's also why the procedural pieces live where they do rather than spread across the codebase:
`FairyRealmChunkGenerator` (plus its biome json) is the *only* place raw procedural terrain logic
lives - hills, the river, vegetation feature selection, all of it - which is what makes "finalize
the map, then delete the procedural generator" a matter of removing that one file and its
worldgen data, not hunting for terrain logic scattered across several classes. `WorldTree` and
`FairyPortalRuins`, by contrast, are already one-time, deterministic *placements* of exact,
already-decided content (a structure resource; a hand-specified chamber) rather than open-ended
generation - they could plausibly stay exactly as they are even after the terrain generator
itself is gone, or their own output could just as easily be baked into the same shipped map
alongside the terrain. Nothing about this split needed inventing for this - it's the same
separation of concerns ("one-time placement" vs. "per-chunk procedural generation") this file
already used for the tree well before the terrain itself needed reworking.

## The Book of the Faye

A lore book ([BookOfTheFaye.java](src/main/java/com/patrickma/magiccircles/item/BookOfTheFaye.java)),
creative-only for now - no recipe or drop source yet. Right-clicking it opens the normal
vanilla book-reading screen; nothing about that screen is custom. What *is* worth explaining
is how its pages are built and how the in-book navigation works, since neither is obvious from
playing with it:

- **It's the real `minecraft:written_book`, not a custom item** - `BookOfTheFaye.createStack()`
  bakes a `pages` NBT tag onto a plain `new ItemStack(Items.WRITTEN_BOOK)`. This isn't a style
  choice: an earlier version registered a dedicated `magiccircles:book_of_the_faye` item (a
  `WrittenBookItem` subclass) instead, and it silently failed two different ways - no texture
  (nobody had given the new item a model), and right-clicking it did nothing at all. The second
  one traces to a hard vanilla check: both `ClientPacketListener#handleOpenBook` and
  `ServerPlayer#openItemGui` gate on `stack.is(Items.WRITTEN_BOOK)` specifically (confirmed by
  decompiling Forge's own sources, not assumed) before the book-reading screen ever opens - a
  same-shaped item registered under a different id fails that check and the screen just never
  appears, with no error to point at why. Being the actual vanilla item sidesteps both problems
  at once, texture included, since it now genuinely *is* a written book in every way that
  matters to vanilla.
- The `resolved` NBT tag is set, so vanilla never tries to re-parse the pages looking for
  entity selectors (`@e`-style references), which they don't contain.
- The table of contents (page 1) and the chapter 2 spell index are **not** a custom menu of
  any kind - each entry is ordinary page text with a `ClickEvent.Action.CHANGE_PAGE` click
  event attached to it, the exact mechanism vanilla itself uses for "click here to turn to
  page N" (see `BookViewScreen#handleComponentClicked`, confirmed against Forge's decompiled
  sources rather than assumed). No mixin, no custom screen, no packet - it's a feature every
  written book has always had, just rarely hand-authored.
- Every click target is a computed page number, not a hand-counted one: `createStack()` builds
  the chapter/spell page lists first, then derives where each section actually landed from
  those lists' sizes before building the clickable links. Reordering a chapter or adding a
  page can't silently break a link the way a hard-coded page number could.
- Chapter 2 illustrates each spell's ring with a compact 5x5 diagram (`soloDiagram`/
  `comboDiagram`) - `X` for the heart, a letter per color (B/G/P/R, and `V` for Green, called
  out once since it's the only non-obvious one), `_` for a position that must stay empty. A
  combo spell's diagram shows one specific 6-6 arrangement as an *example* - the real ritual
  (`MagicCircleRitual#detectSpell`) only checks how many runes of each color are in the ring,
  not where, so any arrangement of the same counts works in-game even though the book only
  draws one. Zuzo's Crossing (the portal) gets its own `portalDiagram`, in lowercase and with
  its own inline key (`r`/`n`/`g`) - it's a genuinely different pattern (`PortalRitual`, not a
  ring color count at all), so reusing the uppercase ring-color letters for it would have
  implied a relationship that isn't there.
- Page-fit was engineered conservatively against vanilla's own constants (`BookViewScreen`:
  114px wrap width, a 14-line-per-page cap) rather than guessed - every page was checked to
  stay comfortably under that, worst case around 12 of 14 lines - but I can't actually see the
  rendered book screen myself, so this hasn't been visually confirmed in-game the way the rest
  of this checklist would ideally want. If a line looks like it's overflowing the page in
  practice, that's the first thing to check.

**The lore itself**, briefly: the Faye once lived in the overworld, drawing their magic from
the Wellspring - the last remnant of the "Font of Making," the tool a pantheon called the
Worldsmiths once used to create every world. Something the Faye did angered the Worldsmiths -
deliberately left a mystery, never resolved on the page. In judgment, the Worldsmiths enacted
"the Recalling," stripping every drop of Wellspring water out of the overworld - leaving the
world itself otherwise untouched, not the total destruction the Faye had feared. Queen Zuzo
(the storm spell's namesake, per your original ask) led her people in carrying what water they
could into a hastily-made pocket dimension first; that saved fragment is what still powers the
Faye today, and is what the mod's Fairy Realm actually *is*, in-universe (the Faye's own name
for it, per the book, is Yllumere). Exile there, cut off from the wider world, was judged
punishment enough - they weren't destroyed, just sealed away with whatever they'd saved. The
book's chapter 2 then names and illustrates all 12 current spells (the 11 ring-color ones plus
the portal), most named for a handful of recurring legendary Faye figures invented for this -
Zuzo herself, General Korrin (the shield/ward figure, tied to Purple now rather than Gold),
the healer Sylvaine, the nature-spirit Verdant Mother (tied to Gold now rather than Purple -
see "Purple - Shield" above for why the two swapped), and the trickster spirit called the
Gleaner - with combo spells named as joint myths of whichever two figures' colors make up that
combo, and the portal itself named "Zuzo's Crossing" after the very exodus this chapter opens
with.

## Worldgen: Fairy Fossil Ore

Three files make this ore actually generate, all modeled closely on vanilla's own
`coal_ore` feature but simplified to one size/shape (vanilla actually layers two coal
features together) and one target block (`minecraft:stone` - not `minecraft:deepslate`,
which barely overlaps coal's Y range anyway):

- [worldgen/configured_feature/fairy_fossil_ore.json](src/main/resources/data/magiccircles/worldgen/configured_feature/fairy_fossil_ore.json) -
  vein size 17, same as coal.
- [worldgen/placed_feature/fairy_fossil_ore.json](src/main/resources/data/magiccircles/worldgen/placed_feature/fairy_fossil_ore.json) -
  20 attempts per chunk, Y 0-192, same as coal's main band.
- [forge/biome_modifier/fairy_fossil_ore.json](src/main/resources/data/magiccircles/forge/biome_modifier/fairy_fossil_ore.json) -
  the Forge-specific file that actually injects the placed feature into every overworld
  biome (`#minecraft:is_overworld`) at the `underground_ores` generation step. Without this
  one, the other two would just sit there unused - a plain datapack has no vanilla-only way
  to attach a feature to existing biomes, which is exactly the gap Forge's biome modifier
  system fills.

**Worth knowing:** ore generation only happens once, when a chunk is first created. Existing
chunks (any world you already have saved, or spawn/explored terrain from before adding this
mod) will never retroactively grow Fairy Fossil Ore - you'll need to explore new terrain, or
start a fresh world, to find any. I validated these three files by booting a dev server
(`./gradlew runServer`) and checking its log for datapack errors rather than by finding ore
in person, since I can't see the running game - it reached `Done` with no errors tied to
`magiccircles`, but if you don't find any after generating fresh chunks, that worldgen
pipeline (specifically the biome modifier - it's the newest/least common part of this stack)
is the first place to look.

Textures are hand-drawn placeholders — good enough to see things clearly in-game, but
expect to redo them once you (or an artist) have real pixel art. They live under
`src/main/resources/assets/magiccircles/textures/` (rune/item icons) and
`src/main/resources/assets/magiccircles/textures/entity/` (the heart's texture sheet). The
heart's *model* in particular is a placeholder built by hand from cuboid coordinates rather
than in a real tool — [Blockbench](https://www.blockbench.net/) can both edit and export
models in exactly the Java format `HeartCoreModel` uses, and is the natural next step if
you want a nicer heart shape without hand-editing box coordinates.

## Roadmap: getting from "a block that glows" to "a spellcasting mod"

Rough order, each step is a reasonable weekend-sized chunk:

1. **The Fairy Realm is a big open feature, not a finished one.** What exists today is a real
   dimension with a real bounded circular island (see "Opening a portal") - not the
   civilization the mod is meant to have. Roughly in the order they'd naturally come up:
   - The island already has real terrain now (a flat center, hills, a river, jungle - see
     "The Fairy Realm's island") - what it still doesn't have is a softer edge where it meets
     the void: the boundary at `ISLAND_RADIUS` is still an abrupt cliff/cutoff rather than a
     tapered or overhanging one.
   - The fairies themselves: two gendered mob models (pale skin, blue eyes, white hair, horns,
     snowy-owl wings), villager-like trading (redstone) and AI, hostile only when attacked.
   - A hand-placed village + castle at the island's center - since this can't be visually
     iterated blind, building it as a literal Java block-placement routine (like
     `HeartCoreModel`'s cuboids, just architecture-scaled) is likely more honest than an
     unverifiable NBT structure file.
   - A flowing, ambient "holy stream" - particles (wisps drawn to its surface) and sound.
   - A recipe for Purple/Red/Green Chalk sourced from Fairy Realm mobs/plants/trades - the
     plan is for these three specifically to only be obtainable there, unlike Blue and Gold
     which are plain overworld crafting recipes already (see "What's in the mod right now").
     The items and their rune/spell wiring already exist - see "Casting spells" - only the
     actual survival recipe is still open.
   - A ritual that fills a placed Heart Core's mana from that stream.

   This is a genuinely large, multi-part body of work best tackled as its own sequence of
   focused passes rather than attempted all at once.
2. **12 spell-shaped code paths exist now, but no unified `Spell` interface yet.** The portal
   ritual and all 11 Heart Core spells (5 solo + 6 combo) at least share `HeartSpell` for their
   identity and mana cost, but each spell's actual *effect* is still a separate method on
   `HeartCoreBlockEntity`, dispatched by a hand-written `switch` in `HeartCoreBlock#interact`.
   Worth pulling further now that there's this much precedent for what one actually needs (a
   pattern to match, a cost, a `cast(ServerLevel)` effect) - see item 6 below.
3. **Ritual detection is deliberately narrow.** `MagicCircleRitual.hasCompleteRing` only
   checks 12 fixed offsets at a flat Y level - it won't work on uneven ground, and the
   footprint is a single hardcoded shape/size. Revisit this once you know whether you want
   multiple circle sizes or shapes with different effects.
4. **The rune symbol itself is static.** Each is a single unanimated texture (only the
   particles move); a `BlockEntityRenderer` for `MagicCircleBlock` that rotates the symbol,
   or cycles it through a few frames when its circle is active, would sell the "working
   magic" feel a lot more. `HeartCoreBlockEntityRenderer` is a working example of this
   exact pattern already in the codebase to build from.
5. **Feed a ritual with items.** On right-click (or after a delay), scan a radius around a
   circle for dropped `ItemEntity`s using `level.getEntitiesOfClass(ItemEntity.class, aabb)`.
   Combined with the Heart Core's mana, this is how you'd let players trigger spells by
   dropping ingredients into a working circle.
6. **A `Spell` abstraction** — now that there are 12 spell-shaped code paths (and `HeartSpell`
   already covers identity/cost), the last piece is folding each spell's effect method into
   the same enum/interface too - something like `HeartSpell.cast(ServerLevel, BlockPos)` -
   rather than a hand-written `switch` in `HeartCoreBlock#interact` mapping enum values to
   method calls. This was worth waiting for - with only one or two spells it wouldn't have
   been obvious what to actually share.
7. **Datagen.** Once hand-writing blockstates/models/recipes/loot tables gets tedious,
   move them into Java generators via `GatherDataEvent` (`./gradlew runData`). It's more
   code up front but scales much better once you have a dozen items and blocks.
8. **Capabilities**, if you want per-player state (known spells, magical affinity) rather
   than mana living purely on the Heartstone/Heart Core.

For reference while building: the [Forge documentation](https://docs.minecraftforge.net/en/1.20.x/)
covers all of the above, and reading the source of similar open-source mods (Ars Nouveau
and Botania are both open-source, ritual/spell-crafting-flavored mods) is one of the
fastest ways to see idiomatic solutions to exactly these problems.

## Project layout

```
src/main/java/com/patrickma/magiccircles/
  MagicCircles.java              mod entry point, wires the registries to the mod event bus
  FairyPortalManager.java        the portal spell: water pit, crossing, drowning-event listener
  registry/                      DeferredRegisters: blocks, items, block entities, creative tab
  registry/ModDimensions.java    the Fairy Realm's ResourceKey (the world itself is mostly data)
  registry/ModWorldgen.java      registers FairyRealmChunkGenerator's codec
  registry/ModBlockTags.java     the Wellspring tag Mana Font checks for (tags Wellspring Water)
  registry/ModFluidTypes.java    the portal + Wellspring water FluidTypes - swim/drown, texture
  registry/ModFluids.java        the portal + Wellspring water source/flowing Fluid pairs
  worldgen/FairyRealmChunkGenerator.java  the island's terrain (flat center + hills + river + jungle) - see "The Fairy Realm's island"
  worldgen/WorldTree.java        the World Tree structure loader/placer + its well - see its own section
  worldgen/FairyPortalRuins.java the first portal's fixed landing chamber - see "The Portal Ruins"
  ritual/MagicCircleRitual.java  shared "is this a complete circle" geometry + color detection
  ritual/HeartSpell.java         every Heart Core spell's identity + mana cost + color mapping
  ritual/PortalRitual.java       the 5x5 redstone+chalk pattern that opens a portal
  ritual/ShieldShapeDetector.java  flood-fill detection of an optional outer Shield wall shape
  ritual/WispChannel.java        where a circle's wisps fly while a spell channels through them
  block/RuneColor.java           the 5 chalk/rune colors - wisp color, block-state value, etc.
  block/MagicCircleBlock.java    the rune's shared behavior (shape, collision, 100 variants)
  block/HeartCoreBlock.java      invisible block a center rune becomes; dispatches spells by color
  block/FairyPortalWaterBlock.java  the real fluid block the portal's water pit becomes
  block/WellspringWaterBlock.java  the real fluid block filling the World Tree's well
  block/WaterLikeFluidSounds.java  splash/swim sounds shared by both water-like fluids
  block/entity/HeartCoreBlockEntity.java  mana + all 12 spells' effects and per-tick state
  block/entity/...BlockEntity.java  per-placement state + client/server ticking
  entity/ShieldOrbEntity.java    one node of the Shield spell's sphere/wall - see "Casting spells"
  item/ChalkItem.java            durability-based rune placement (ColoredChalkItem extends it)
  item/HeartstoneItem.java       mana storage + ritual detection/consumption
  item/FairyHornItem.java        the spell-casting wand - overrides doesSneakBypassUse
  item/BookOfTheFaye.java        the lore book's fixed pages + in-book chapter/spell links
  client/ClientCircleWisps.java  the ring's own colored wisps, one per rune
  client/ClientHeartWisps.java   the heart's white "a prolonged spell is active" wisp ring(s)
  client/WorldTreeWisps.java     the World Tree's 100 wide-orbiting wisps - see its own section
  client/WellspringWisps.java    the well's close-orbiting wisps + Mana Font absorption animation
  client/WaterLikeAmbientSounds.java  enter/exit/underwater-hum/addition sounds, both water-like fluids
  client/ModFluidOverlayRenderer.java  the underwater screen tint - bypasses a hidden vanilla alpha cap
  client/FairyRealmEffects.java  the Fairy Realm's sky dome base color + fog
  client/FairyRealmSkyStreaks.java  the sky's six drifting rune-colored streaks
  client/                        client-only: the heart's 3D model + renderers + render-type
                                  registration + the wisp particle system (never loaded on a
                                  dedicated server - see the Dist.CLIENT annotation on
                                  ClientSetup)

src/main/resources/
  assets/magiccircles/           client-only: textures, models, blockstates, lang
  data/magiccircles/              loaded by client+server: recipes, loot tables,
                                  worldgen/ (ore feature + placement, the Fairy Realm's biome,
                                  the big-jungle-tree-only placed feature),
                                  tags/blocks/ (the Wellspring tag), forge/ (biome modifier),
                                  dimension/ + dimension_type/ (the Fairy Realm),
                                  structures/god_tree.bin.gz (the World Tree's compact block data)
  data/minecraft/tags/blocks/     tag *additions* - files here extend vanilla's own tags
                                  (mineable/pickaxe, needs_stone_tool) to cover our ore
  META-INF/mods.toml              mod metadata Forge reads to display/validate the mod

tools/gen_runes.py                    regenerates the 100 rune textures/models/blockstate
tools/gen_chalk_textures.py           regenerates the purple/red/green chalk item textures
tools/gen_portal_water_texture.py     regenerates the animated portal-fluid block texture
tools/gen_portal_underwater_overlay.py  regenerates the portal fluid's underwater screen tint
tools/gen_wellspring_water_texture.py   regenerates the Wellspring's color-cycling block texture
```
