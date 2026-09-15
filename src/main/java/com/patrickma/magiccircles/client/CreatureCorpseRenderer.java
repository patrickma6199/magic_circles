package com.patrickma.magiccircles.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.patrickma.magiccircles.entity.CreatureCorpseEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.InventoryMenu;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Draws a creature's corpse as the creature itself, lying on its side where it fell.
 *
 * <p>The corpse carries only a snapshot of how the creature looked (see {@link
 * CreatureCorpseEntity#appearance}); from that this builds a stand-in of the same creature, never
 * added to the world, and has that creature's own renderer draw it - so a horse's coat, a wolf's
 * collar and a villager's profession all come out right without any of it being redone here.
 *
 * <p>The pose is the one vanilla gives any mob at the end of its death - tipped ninety degrees onto
 * its side about the line it faces along - but applied here rather than by setting the stand-in's
 * death timer, which would also have tinted it the red of something still dying.
 */
public class CreatureCorpseRenderer extends EntityRenderer<CreatureCorpseEntity>
{
    /** Keeps the stand-in's name plate just above the body, whatever its size. */
    private static final double NAME_CLEARANCE = 0.35;

    private final Map<CreatureCorpseEntity, Body> bodies = new WeakHashMap<>();

    /** The stand-in for one corpse, and the appearance it was built from - rebuilt only if that changes. */
    private record Body(CompoundTag source, @Nullable LivingEntity entity)
    {
    }

    public CreatureCorpseRenderer(EntityRendererProvider.Context context)
    {
        super(context);
        this.shadowRadius = 0.0f;
    }

    @Override
    public ResourceLocation getTextureLocation(CreatureCorpseEntity entity)
    {
        // Never used - the stand-in's own renderer supplies its own texture.
        return InventoryMenu.BLOCK_ATLAS;
    }

    /** The body lies well outside the corpse's own small box - a horse is longer than a block - so culling looks wider. */
    @Override
    public boolean shouldRender(CreatureCorpseEntity entity, Frustum frustum, double camX, double camY, double camZ)
    {
        return entity.shouldRender(camX, camY, camZ) && frustum.isVisible(entity.getBoundingBox().inflate(2.0));
    }

    @Override
    public void render(CreatureCorpseEntity corpse, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight)
    {
        LivingEntity body = bodyFor(corpse);
        if (body == null)
        {
            return;
        }
        float facing = corpse.getYRot();
        body.setPos(corpse.getX(), corpse.getY(), corpse.getZ());
        body.setYRot(facing);
        body.yRotO = facing;
        body.yBodyRot = facing;
        body.yBodyRotO = facing;
        body.yHeadRot = facing;
        body.yHeadRotO = facing;
        body.setXRot(0.0f);
        body.xRotO = 0.0f;

        poseStack.pushPose();
        // Lying on its side, half its width would otherwise be under the ground.
        poseStack.translate(0.0, body.getBbWidth() * 0.5, 0.0);
        // Tip it about the line it faces along. The creature's renderer turns it to face that way
        // itself, so the tip is sandwiched between undoing that turn and redoing it.
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - facing));
        poseStack.mulPose(Axis.ZP.rotationDegrees(90.0f));
        poseStack.mulPose(Axis.YP.rotationDegrees(facing - 180.0f));
        this.entityRenderDispatcher.getRenderer(body).render(body, facing, partialTick, poseStack, buffers, packedLight);
        poseStack.popPose();

        if (this.shouldShowName(corpse))
        {
            // Over the middle of the body: tipped over, its height lies along the ground, off to the side.
            double half = body.getBbHeight() * 0.5;
            double yaw = Math.toRadians(facing);
            poseStack.pushPose();
            poseStack.translate(Math.cos(yaw) * half, body.getBbWidth() + NAME_CLEARANCE - corpse.getNameTagOffsetY(),
                    Math.sin(yaw) * half);
            super.render(corpse, entityYaw, partialTick, poseStack, buffers, packedLight);
            poseStack.popPose();
        }
    }

    @Nullable
    private LivingEntity bodyFor(CreatureCorpseEntity corpse)
    {
        CompoundTag appearance = corpse.appearance();
        Body cached = bodies.get(corpse);
        if (cached != null && cached.source() == appearance)
        {
            return cached.entity();
        }
        LivingEntity made = null;
        Optional<EntityType<?>> type = EntityType.byString(corpse.creatureType());
        if (type.isPresent())
        {
            Entity created = type.get().create(corpse.level());
            if (created instanceof LivingEntity living)
            {
                try
                {
                    living.load(appearance.copy());
                }
                catch (RuntimeException ignored)
                {
                    // Something in the snapshot this creature couldn't read back - drawn as a plain one of its kind.
                }
                made = living;
            }
        }
        bodies.put(corpse, new Body(appearance, made));
        return made;
    }
}
