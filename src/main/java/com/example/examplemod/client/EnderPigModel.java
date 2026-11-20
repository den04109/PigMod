package com.example.examplemod.client;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.TameablePigEntity;
import net.minecraft.client.model.PigModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;

/**
 * Custom Ender Pig model.
 *
 * The geometry deliberately uses the same part layout and pivots as the
 * vanilla PigModel. This is important because PigModel/QuadrupedModel
 * contains the walking animation and expects the body + four legs to use
 * vanilla pivots.
 *
 * The body is kept as a 10x16x8 cube rotated 90 degrees around X, which is
 * how Minecraft's normal pig model produces a horizontal 10x8x16 torso.
 * The head is moved forward so it physically touches the body, and the legs
 * use the vanilla 6-block height/pivots so they stay attached and animate
 * correctly.
 *
 * The 64x64 texture layout is preserved for pig_enderpig.png.
 */
public class EnderPigModel extends PigModel<TameablePigEntity> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "ender_pig"),
            "main"
    );

    public EnderPigModel(ModelPart root) {
        super(root);
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Exactly the same coordinate system as the vanilla pig body.
        // After the 90-degree X rotation it becomes a horizontal
        // 10 wide x 8 high x 16 long body in world/model space.
        root.addOrReplaceChild(
                "body",
                CubeListBuilder.create()
                        .texOffs(28, 8)
                        .addBox(
                                -5.0F, -10.0F, -7.0F,
                                10.0F, 16.0F, 8.0F,
                                new CubeDeformation(0.0F)
                        ),
                PartPose.offsetAndRotation(
                        0.0F, 11.0F, 2.0F,
                        1.5708F, 0.0F, 0.0F
                )
        );

        // Head touches the front of the body at Z=-6.
        // The snout is centered vertically on the front face of the head.
        root.addOrReplaceChild(
                "head",
                CubeListBuilder.create()
                        .texOffs(0, 0)
                        .addBox(
                                -4.0F, -4.0F, -8.0F,
                                8.0F, 8.0F, 8.0F,
                                new CubeDeformation(0.0F)
                        )
                        .texOffs(16, 16)
                        .addBox(
                                -2.0F, 0.0F, -9.0F,
                                4.0F, 3.0F, 1.0F,
                                new CubeDeformation(0.0F)
                        ),
                PartPose.offset(0.0F, 12.0F, -6.0F)
        );

        // Vanilla pig leg pivots: the top of each leg is exactly at Y=18,
        // where the bottom of the rotated body is located.
        root.addOrReplaceChild(
                "right_hind_leg",
                CubeListBuilder.create()
                        .texOffs(0, 16)
                        .addBox(
                                -2.0F, 0.0F, -2.0F,
                                4.0F, 6.0F, 4.0F,
                                new CubeDeformation(0.0F)
                        ),
                PartPose.offset(3.0F, 18.0F, 7.0F)
        );
        root.addOrReplaceChild(
                "left_hind_leg",
                CubeListBuilder.create()
                        .texOffs(0, 16)
                        .addBox(
                                -2.0F, 0.0F, -2.0F,
                                4.0F, 6.0F, 4.0F,
                                new CubeDeformation(0.0F)
                        ),
                PartPose.offset(-3.0F, 18.0F, 7.0F)
        );
        root.addOrReplaceChild(
                "right_front_leg",
                CubeListBuilder.create()
                        .texOffs(0, 16)
                        .addBox(
                                -2.0F, 0.0F, -2.0F,
                                4.0F, 6.0F, 4.0F,
                                new CubeDeformation(0.0F)
                        ),
                PartPose.offset(3.0F, 18.0F, -5.0F)
        );
        root.addOrReplaceChild(
                "left_front_leg",
                CubeListBuilder.create()
                        .texOffs(0, 16)
                        .addBox(
                                -2.0F, 0.0F, -2.0F,
                                4.0F, 6.0F, 4.0F,
                                new CubeDeformation(0.0F)
                        ),
                PartPose.offset(-3.0F, 18.0F, -5.0F)
        );

        return LayerDefinition.create(mesh, 64, 64);
    }
}
