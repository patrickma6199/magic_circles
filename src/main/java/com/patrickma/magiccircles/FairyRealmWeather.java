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
 * The Fairy Realm's permanent rainstorm - raining and thundering (lightning included), always -
 * and its "no fire" rule.
 *
 * <p><b>Weather.</b> {@link #onServerTick} keeps the dimension permanently raining and
 * thundering via {@link ServerLevel#setWeatherParameters}. Lightning is left free to strike
 * anywhere, island included - now that the whole realm sits inside {@link FairyRealmShield}'s own
 * boundary, there's no separate "outside the island" to confine it to, and the existing
 * fire-disable rules below already make sure a strike can't actually set anything ablaze.
 *
 * <p><b>Fire.</b> Vanilla doesn't expose a per-dimension "fire never spreads/never appears" flag
 * either (the closest, the {@code doFireTick} game rule, is shared by every dimension on the
 * server, so setting it here would also turn off fire in the Overworld). Instead this blocks fire
 * at its two realistic sources in this dimension - flint & steel / fire charges ({@link
 * #onRightClick}) and any fire block that does manage to get placed ({@link #onFirePlaced}) - and
 * makes sure nothing standing here can actually be harmed or stay lit by fire ({@link
 * #onLivingHurt}, {@link #onServerTick}'s fire-clearing pass), which covers the cases those two
 * can't (an explosion igniting a block directly, say).
 */
@Mod.EventBusSubscriber(modid = MagicCircles.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FairyRealmWeather
{
    private FairyRealmWeather()
    {
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

        // A big fixed duration, refreshed constantly - simpler than hooking the weather-cleared
        // event to immediately re-storm, and functionally identical ("always a rainstorm").
        if (!fairyRealm.isThundering() || !fairyRealm.isRaining())
        {
            fairyRealm.setWeatherParameters(0, 12000, true, true);
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
