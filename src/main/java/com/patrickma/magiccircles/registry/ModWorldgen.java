package com.patrickma.magiccircles.registry;

import com.mojang.serialization.Codec;
import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.worldgen.FairyRealmChunkGenerator;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registers {@link FairyRealmChunkGenerator}'s codec so {@code data/magiccircles/dimension/fairy_realm.json}
 * can reference it by id. {@code BuiltInRegistries.CHUNK_GENERATOR} itself is frozen before
 * mods ever run, so - same as the creative tab in {@link ModCreativeTabs} - this goes through
 * a {@code DeferredRegister} targeting the vanilla registry key instead of a raw registration
 * call, which Forge reopens for exactly this purpose during its own registration event.
 */
public class ModWorldgen
{
    public static final DeferredRegister<Codec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, MagicCircles.MOD_ID);

    public static final RegistryObject<Codec<? extends ChunkGenerator>> FAIRY_REALM =
            CHUNK_GENERATORS.register("fairy_realm", () -> FairyRealmChunkGenerator.CODEC);
}
