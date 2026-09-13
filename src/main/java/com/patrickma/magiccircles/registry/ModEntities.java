package com.patrickma.magiccircles.registry;

import com.patrickma.magiccircles.MagicCircles;
import com.patrickma.magiccircles.entity.AncientHeartstoneEntity;
import com.patrickma.magiccircles.entity.DreamElkEntity;
import com.patrickma.magiccircles.entity.FerrymanEntity;
import com.patrickma.magiccircles.entity.GhostPhantomEntity;
import com.patrickma.magiccircles.entity.ManaWyrmEntity;
import com.patrickma.magiccircles.entity.PixieEntity;
import com.patrickma.magiccircles.entity.PlayerCorpseEntity;
import com.patrickma.magiccircles.entity.ShieldOrbEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Every entity type the mod adds. */
public class ModEntities
{
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MagicCircles.MOD_ID);

    // Sized and packed (see HeartCoreBlockEntity#SHIELD_NODE_COUNT) so neighboring orbs'
    // hitboxes overlap on the sphere's surface rather than leaving gaps - a mob-sized gap
    // between them would defeat the point of an "unbreakable wall" shield.
    public static final RegistryObject<EntityType<ShieldOrbEntity>> SHIELD_ORB = ENTITY_TYPES.register("shield_orb",
            () -> EntityType.Builder.<ShieldOrbEntity>of(ShieldOrbEntity::new, MobCategory.MISC)
                    .sized(1.6f, 1.6f)
                    .clientTrackingRange(10)
                    .fireImmune()
                    .build("shield_orb"));

    /** A fairy's own companion mob - see {@link PixieEntity} for why this is an {@code Allay} subclass rather than a fresh mob (same size, hence the identical {@code sized(...)} call). */
    public static final RegistryObject<EntityType<PixieEntity>> PIXIE = ENTITY_TYPES.register("pixie",
            () -> EntityType.Builder.<PixieEntity>of(PixieEntity::new, MobCategory.CREATURE)
                    .sized(0.35f, 0.6f)
                    .clientTrackingRange(10)
                    .build("pixie"));

    /** A Wellspring-dwelling fish - see {@link ManaWyrmEntity}. Sized like vanilla's own Salmon, whose model this reuses. */
    public static final RegistryObject<EntityType<ManaWyrmEntity>> MANA_WYRM = ENTITY_TYPES.register("mana_wyrm",
            () -> EntityType.Builder.<ManaWyrmEntity>of(ManaWyrmEntity::new, MobCategory.WATER_AMBIENT)
                    .sized(0.7f, 0.4f)
                    .clientTrackingRange(4)
                    .build("mana_wyrm"));

    /** The permanent gold heart over the Wellspring - see {@link AncientHeartstoneEntity}. Sized the same as a real Heart Core's own floating heart. */
    public static final RegistryObject<EntityType<AncientHeartstoneEntity>> ANCIENT_HEARTSTONE = ENTITY_TYPES.register("ancient_heartstone",
            () -> EntityType.Builder.<AncientHeartstoneEntity>of(AncientHeartstoneEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f)
                    .clientTrackingRange(10)
                    .fireImmune()
                    .build("ancient_heartstone"));

    /** A huge-antlered magical elk - see {@link DreamElkEntity}. Same footprint as a vanilla Horse; the antlers (see {@code client/DreamElkModel}) render well above the hitbox, same as e.g. an Enderman's own model exceeds its hitbox. */
    public static final RegistryObject<EntityType<DreamElkEntity>> DREAM_ELK = ENTITY_TYPES.register("dream_elk",
            () -> EntityType.Builder.<DreamElkEntity>of(DreamElkEntity::new, MobCategory.CREATURE)
                    .sized(1.4f, 1.6f)
                    .clientTrackingRange(10)
                    .build("dream_elk"));

    /** Escorts a {@code limbo/RiteOfPassage} caster into the Realm of the Dead - see {@link FerrymanEntity}. Humanoid-sized, same footprint as a Villager. */
    public static final RegistryObject<EntityType<FerrymanEntity>> FERRYMAN = ENTITY_TYPES.register("ferryman",
            () -> EntityType.Builder.<FerrymanEntity>of(FerrymanEntity::new, MobCategory.CREATURE)
                    .sized(0.6f, 1.95f)
                    .clientTrackingRange(10)
                    .build("ferryman"));

    /** A dead player's body - see {@link PlayerCorpseEntity}. Sized for something lying down, not standing. */
    public static final RegistryObject<EntityType<PlayerCorpseEntity>> PLAYER_CORPSE = ENTITY_TYPES.register("player_corpse",
            () -> EntityType.Builder.<PlayerCorpseEntity>of(PlayerCorpseEntity::new, MobCategory.MISC)
                    .sized(0.8f, 0.45f)
                    .clientTrackingRange(10)
                    .build("player_corpse"));

    /** A phantom locked onto one specific victim - see {@link GhostPhantomEntity}. Same footprint as vanilla's own Phantom. */
    public static final RegistryObject<EntityType<GhostPhantomEntity>> GHOST_PHANTOM = ENTITY_TYPES.register("ghost_phantom",
            () -> EntityType.Builder.<GhostPhantomEntity>of(GhostPhantomEntity::new, MobCategory.MONSTER)
                    .sized(0.9f, 0.5f)
                    .clientTrackingRange(10)
                    .build("ghost_phantom"));
}
