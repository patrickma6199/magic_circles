package com.patrickma.magiccircles;

import com.patrickma.magiccircles.registry.ModDimensions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Fairy Realm's own weather, and its "no fire" rule.
 *
 * <p><b>Weather.</b> The realm has weather of its own. Vanilla gives every dimension but the
 * overworld the overworld's weather, and quietly ignores any attempt to set it there - so an
 * earlier "permanent storm" here, set through {@link ServerLevel#setWeatherParameters}, never rained
 * at all. Now {@code mixin/ServerLevelMixin} asks {@link #isRaining} instead, and it rains: now and
 * then of its own accord ({@link #onServerTick}), whenever the queen's wrath is roused ({@code
 * entity/FairyQueenEntity}), and a long while when a queen falls ({@code FairyCourt}). Never
 * thunder - a thunderstorm's own stray lightning would strike the Faye themselves.
 *
 * <p><b>Fire.</b> There is none here, from any source. Vanilla has no per-dimension switch for it
 * (the {@code doFireTick} game rule is shared by every dimension on the server), so fire is put out
 * at the block itself: {@code mixin/BaseFireBlockMixin} removes every fire block the moment it is
 * set in this dimension - spread, lava, lightning, fireballs and all - and refuses flint & steel,
 * fire charges and dispensers outright, using {@link #isFireless}. What's here as well: {@link
 * #onRightClick} stops a lighter from doing anything else (lighting a campfire, priming TNT),
 * {@link #onFirePlaced} is the older catch for fire placed by an entity, and nothing standing here
 * can be harmed or stay lit by fire ({@link #onLivingHurt}, {@link #onServerTick}'s fire-clearing
 * pass).
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FairyRealmWeather
{
    /** Left to itself, the realm rains a few minutes at a time, with a good long while between. */
    private static final int NATURAL_RAIN_MIN = 20 * 60 * 2;
    private static final int NATURAL_RAIN_RANDOM = 20 * 60 * 4;
    private static final int NATURAL_CLEAR_MIN = 20 * 60 * 10;
    private static final int NATURAL_CLEAR_RANDOM = 20 * 60 * 20;

    private static int rainTicks;
    private static int nextNaturalRain = NATURAL_CLEAR_MIN;

    private FairyRealmWeather()
    {
    }

    /** Rain over the whole realm for at least this long - never cutting short rain that would have lasted longer. */
    public static void rainFor(int ticks)
    {
        rainTicks = Math.max(rainTicks, ticks);
    }

    /** Whether it is raining in the Fairy Realm - what {@code mixin/ServerLevelMixin} tells the realm. */
    public static boolean isRaining()
    {
        return rainTicks > 0;
    }

    @SubscribeEvent
    public static void onServerStopped(net.minecraftforge.event.server.ServerStoppedEvent event)
    {
        rainTicks = 0;
        nextNaturalRain = NATURAL_CLEAR_MIN;
    }

    /** Whether fire is forbidden in this level - true only for the Fairy Realm. */
    public static boolean isFireless(net.minecraft.world.level.Level level)
    {
        return level.dimension().equals(ModDimensions.FAIRY_REALM);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }
        ServerLevel fairyRealm = event.getServer().getLevel(ModDimensions.FAIRY_REALM);
        if (fairyRealm == null)
        {
            return;
        }

        if (rainTicks > 0)
        {
            rainTicks--;
        }
        else if (--nextNaturalRain <= 0)
        {
            rainFor(NATURAL_RAIN_MIN + fairyRealm.random.nextInt(NATURAL_RAIN_RANDOM));
            nextNaturalRain = NATURAL_CLEAR_MIN + fairyRealm.random.nextInt(NATURAL_CLEAR_RANDOM);
        }

        // Covers every entity, mooshrooms included, not just players - fire can happen to
        // anything standing here. This used to look expensive purely because FairyRealmShield's
        // own ~200,000 decorative orbs dominated getAllEntities() in this same dimension; now
        // that count is down to a few thousand (see that class's own doc comment), walking every
        // real entity here costs essentially nothing.
        for (net.minecraft.world.entity.Entity entity : fairyRealm.getAllEntities())
        {
            if (entity instanceof LivingEntity living && living.isOnFire())
            {
                living.clearFire();
            }
        }
    }

    /** Fire never hurts (or stays lit on) anything in the Fairy Realm. */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event)
    {
        if (!(event.getEntity().level() instanceof ServerLevel level) || !level.dimension().equals(ModDimensions.FAIRY_REALM))
        {
            return;
        }
        if (event.getSource().is(DamageTypeTags.IS_FIRE))
        {
            event.setCanceled(true);
            event.getEntity().clearFire();
        }
    }

    /** Flint & steel / fire charges never actually light anything here. */
    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event)
    {
        if (!(event.getLevel() instanceof ServerLevel level) || !level.dimension().equals(ModDimensions.FAIRY_REALM))
        {
            return;
        }
        if (event.getItemStack().is(Items.FLINT_AND_STEEL) || event.getItemStack().is(Items.FIRE_CHARGE))
        {
            event.setCanceled(true);
        }
    }

    /** Catches anything that still manages to place a fire block directly (an explosion, another mod, ...) - it's removed the instant it appears. */
    @SubscribeEvent
    public static void onFirePlaced(BlockEvent.EntityPlaceEvent event)
    {
        if (!(event.getLevel() instanceof ServerLevel level) || !level.dimension().equals(ModDimensions.FAIRY_REALM))
        {
            return;
        }
        if (event.getPlacedBlock().is(Blocks.FIRE) || event.getPlacedBlock().is(Blocks.SOUL_FIRE))
        {
            event.setCanceled(true);
        }
    }
}
