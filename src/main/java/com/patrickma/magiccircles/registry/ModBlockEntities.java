package com.patrickma.magiccircles.registry;

import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.block.entity.BookOfTheFayeBlockEntity;
import com.patrickma.magiccircles.block.entity.HeartCoreBlockEntity;
import com.patrickma.magiccircles.block.entity.MagicCircleBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Block entities back the "living" state a block needs - here, the circle's animation timer. */
public class ModBlockEntities
{
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MagicCircles.MOD_ID);

    public static final RegistryObject<BlockEntityType<MagicCircleBlockEntity>> MAGIC_CIRCLE =
            BLOCK_ENTITIES.register("magic_circle", () -> BlockEntityType.Builder.of(
                    MagicCircleBlockEntity::new, ModBlocks.MAGIC_CIRCLE.get()).build(null));

    public static final RegistryObject<BlockEntityType<HeartCoreBlockEntity>> HEART_CORE =
            BLOCK_ENTITIES.register("heart_core", () -> BlockEntityType.Builder.of(
                    HeartCoreBlockEntity::new, ModBlocks.HEART_CORE.get()).build(null));

    public static final RegistryObject<BlockEntityType<BookOfTheFayeBlockEntity>> BOOK_OF_THE_FAYE =
            BLOCK_ENTITIES.register("book_of_the_faye", () -> BlockEntityType.Builder.of(
                    BookOfTheFayeBlockEntity::new, ModBlocks.BOOK_OF_THE_FAYE_BLOCK.get()).build(null));

    public static final RegistryObject<BlockEntityType<com.patrickma.magiccircles.block.entity.ArtOfBloodBlockEntity>> ART_OF_BLOOD =
            BLOCK_ENTITIES.register("art_of_blood", () -> BlockEntityType.Builder.of(
                    com.patrickma.magiccircles.block.entity.ArtOfBloodBlockEntity::new,
                    ModBlocks.ART_OF_BLOOD_BLOCK.get()).build(null));
}
