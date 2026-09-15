package com.patrickma.magiccircles.client;

import com.patrickma.magiccircles.entity.FairyQueenEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;

/**
 * The Fairy Queen's model - the player's own, slim - which knows how to sit. Vanilla only draws a
 * seated pose for something riding another entity; the queen isn't riding anything when she takes
 * her throne, so this asks for the same pose whenever she is seated (see {@code
 * FairyQueenEntity#isSeated}).
 */
public class QueenModel extends PlayerModel<FairyQueenEntity>
{
    public QueenModel(ModelPart root)
    {
        super(root, true);
    }

    @Override
    public void setupAnim(FairyQueenEntity queen, float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch)
    {
        if (queen.isSeated())
        {
            this.riding = true;
        }
        super.setupAnim(queen, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
    }
}
