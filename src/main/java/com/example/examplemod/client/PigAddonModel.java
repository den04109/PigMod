package com.example.examplemod.client;

import com.example.examplemod.ExampleMod;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Геометрия всех визуальных деталей мутаций: крылья, нимб, рога, пушки, сбруя,
 * плавники, портал и т.д. Сама свинья сюда НЕ входит — она рисуется ванильной
 * PigModel с ванильной текстурой, а этот класс добавляется поверх отдельным
 * слоем (PigAddonLayer) со своей текстурой pig_addons.png.
 *
 * Файл сгенерирован из .bbmodel-исходников (папка blockbench/ в корне мода):
 * геометрия, pivot'ы и UV здесь и в Blockbench гарантированно одинаковые.
 * Правится он не руками, а пересборкой моделей.
 */
public class PigAddonModel {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "pig_addons"), "main");

    /** Кости-якоря без геометрии: за них цепляются ванильные предметы. */
    private static final List<String> ANCHORS = List.of("head_anchor", "hand_anchor");

    private final Map<String, ModelPart> parts = new HashMap<>();
    private final Map<String, float[]> basePose = new HashMap<>();
    private final List<ModelPart> bodyParts = new ArrayList<>();
    private final ModelPart headAnchor;
    private final ModelPart handAnchor;

    public PigAddonModel(ModelPart root) {
        put("head_anchor", root.getChild("head_anchor"));
        put("hand_anchor", root.getChild("hand_anchor"));
        put("tail", root.getChild("tail"));
        put("chest_mount_left", root.getChild("chest_mount_left"));
        put("chest_mount_right", root.getChild("chest_mount_right"));
        put("quiver", root.getChild("quiver"));
        put("glide_wing_left", root.getChild("glide_wing_left"));
        put("glide_wing_right", root.getChild("glide_wing_right"));
        put("fin_dorsal", root.getChild("fin_dorsal"));
        put("fin_tail", root.getChild("fin_tail"));
        put("gill_left", root.getChild("gill_left"));
        put("gill_right", root.getChild("gill_right"));
        put("harness", root.getChild("harness"));
        put("saddle", root.getChild("saddle"));
        put("wing_angel_left", root.getChild("wing_angel_left"));
        put("wing_angel_right", root.getChild("wing_angel_right"));
        put("wing_flight_left", root.getChild("wing_flight_left"));
        put("wing_flight_right", root.getChild("wing_flight_right"));
        put("dragon_spikes", root.getChild("dragon_spikes"));
        put("dragon_tail", root.getChild("dragon_tail"));
        put("wing_dragon_left", root.getChild("wing_dragon_left"));
        put("wing_dragon_right", root.getChild("wing_dragon_right"));
        put("portal_near_left", root.getChild("portal_near_left"));
        put("portal_near_right", root.getChild("portal_near_right"));
        put("portal_far_left", root.getChild("portal_far_left"));
        put("portal_far_right", root.getChild("portal_far_right"));
        put("cyborg_plate", root.getChild("cyborg_plate"));
        put("cannon_left", root.getChild("cannon_left"));
        put("cannon_right", root.getChild("cannon_right"));
        put("metal_wing_left", root.getChild("metal_wing_left"));
        put("metal_wing_right", root.getChild("metal_wing_right"));
        put("bomber_tail", root.getChild("bomber_tail"));
        put("halo", this.parts.get("head_anchor").getChild("halo"));
        put("horn_left", this.parts.get("head_anchor").getChild("horn_left"));
        put("horn_right", this.parts.get("head_anchor").getChild("horn_right"));
        put("cyborg_visor", this.parts.get("head_anchor").getChild("cyborg_visor"));
        this.headAnchor = this.parts.get("head_anchor");
        this.handAnchor = this.parts.get("hand_anchor");
        for (Map.Entry<String, ModelPart> e : this.parts.entrySet()) {
            ModelPart p = e.getValue();
            this.basePose.put(e.getKey(), new float[]{p.xRot, p.yRot, p.zRot});
        }
        for (String name : ROOT_NAMES) {
            if (!name.equals("head_anchor")) {
                this.bodyParts.add(this.parts.get(name));
            }
        }
    }

    private static final String[] ROOT_NAMES = {"head_anchor", "hand_anchor", "tail", "chest_mount_left", "chest_mount_right", "quiver", "glide_wing_left", "glide_wing_right", "fin_dorsal", "fin_tail", "gill_left", "gill_right", "harness", "saddle", "wing_angel_left", "wing_angel_right", "wing_flight_left", "wing_flight_right", "dragon_spikes", "dragon_tail", "wing_dragon_left", "wing_dragon_right", "portal_near_left", "portal_near_right", "portal_far_left", "portal_far_right", "cyborg_plate", "cannon_left", "cannon_right", "metal_wing_left", "metal_wing_right", "bomber_tail"};

    private void put(String name, ModelPart part) {
        this.parts.put(name, part);
    }

    public ModelPart part(String name) {
        return this.parts.get(name);
    }

    /** Скрывает все детали: слой каждый кадр включает только нужные. */
    public void hideAll() {
        for (Map.Entry<String, ModelPart> e : this.parts.entrySet()) {
            e.getValue().visible = ANCHORS.contains(e.getKey());
        }
    }

    public void show(String name) {
        ModelPart p = this.parts.get(name);
        if (p != null) {
            p.visible = true;
        }
    }

    public boolean isVisible(String name) {
        ModelPart p = this.parts.get(name);
        return p != null && p.visible;
    }

    public ModelPart handAnchor() {
        return this.handAnchor;
    }

    /**
     * Повторяет анимацию ванильной QuadrupedModel: голова смотрит туда же, куда
     * у свиньи, а "рука" (якорь под инструмент) качается вместе с правой передней
     * ногой. Плюс собственный взмах крыльев от времени.
     */
    public void setupAnim(float limbSwing, float limbSwingAmount, float ageInTicks,
                          float netHeadYaw, float headPitch, float flapSpeed) {
        this.headAnchor.xRot = headPitch * ((float) Math.PI / 180F);
        this.headAnchor.yRot = netHeadYaw * ((float) Math.PI / 180F);
        this.handAnchor.xRot = Mth.cos(limbSwing * 0.6662F + (float) Math.PI) * 1.4F * limbSwingAmount;

        float wing = Mth.cos(ageInTicks * 0.24F * flapSpeed) * 0.20F * flapSpeed;
        float glide = Mth.cos(ageInTicks * 0.12F) * 0.05F;
        flap("wing_angel_left", + wing);
        flap("wing_angel_right", - wing);
        flap("wing_flight_left", + wing);
        flap("wing_flight_right", - wing);
        flap("wing_dragon_left", + wing);
        flap("wing_dragon_right", - wing);
        flap("glide_wing_left", + glide);
        flap("glide_wing_right", - glide);
        flap("metal_wing_left", + glide);
        flap("metal_wing_right", - glide);
    }

    private void flap(String name, float delta) {
        ModelPart p = this.parts.get(name);
        float[] base = this.basePose.get(name);
        if (p != null && base != null) {
            p.xRot = base[0];
            p.yRot = base[1];
            p.zRot = base[2] + delta;
        }
    }

    /** Детали, закреплённые на голове (нимб, рога, портал, визор киборга). */
    public void renderHead(PoseStack poseStack, VertexConsumer buffer, int light, int overlay) {
        this.headAnchor.render(poseStack, buffer, light, overlay);
    }

    /** Все остальные детали. */
    public void renderBody(PoseStack poseStack, VertexConsumer buffer, int light, int overlay) {
        for (ModelPart p : this.bodyParts) {
            p.render(poseStack, buffer, light, overlay);
        }
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition p_head_anchor = root.addOrReplaceChild("head_anchor", CubeListBuilder.create(), PartPose.offset(0F, 12F, -6F));
        PartDefinition p_hand_anchor = root.addOrReplaceChild("hand_anchor", CubeListBuilder.create(), PartPose.offset(-3F, 18F, -5F));
        PartDefinition p_tail = root.addOrReplaceChild("tail", CubeListBuilder.create()
                .texOffs(58, 116).addBox(-1F, 0F, 0F, 2F, 3F, 2F), PartPose.offsetAndRotation(0F, 11F, 8F, 0.4363F, 0F, 0F));
        PartDefinition p_chest_mount_left = root.addOrReplaceChild("chest_mount_left", CubeListBuilder.create()
                .texOffs(42, 57).addBox(0F, -2F, -3F, 1F, 4F, 6F), PartPose.offset(5F, 13F, 1F));
        PartDefinition p_chest_mount_right = root.addOrReplaceChild("chest_mount_right", CubeListBuilder.create()
                .texOffs(27, 57).addBox(-1F, -2F, -3F, 1F, 4F, 6F), PartPose.offset(-5F, 13F, 1F));
        PartDefinition p_quiver = root.addOrReplaceChild("quiver", CubeListBuilder.create()
                .texOffs(26, 108).addBox(-2F, -3F, -1F, 3F, 4F, 3F)
                .texOffs(67, 116).addBox(-1.5F, -5.5F, -0.5F, 2F, 2.5F, 2F), PartPose.offsetAndRotation(0F, 10F, 3F, 0.3142F, 0F, 0F));
        PartDefinition p_glide_wing_left = root.addOrReplaceChild("glide_wing_left", CubeListBuilder.create()
                .texOffs(58, 91).addBox(0F, -1F, -3F, 7F, 1F, 7F), PartPose.offsetAndRotation(4F, 12F, 0F, 0F, 0F, 0.0698F));
        PartDefinition p_glide_wing_right = root.addOrReplaceChild("glide_wing_right", CubeListBuilder.create()
                .texOffs(29, 91).addBox(-7F, -1F, -3F, 7F, 1F, 7F), PartPose.offsetAndRotation(-4F, 12F, 0F, 0F, 0F, -0.0698F));
        PartDefinition p_fin_dorsal = root.addOrReplaceChild("fin_dorsal", CubeListBuilder.create()
                .texOffs(68, 81).addBox(-0.5F, -4F, -2F, 1F, 4F, 5F), PartPose.offset(0F, 10F, 0F));
        PartDefinition p_fin_tail = root.addOrReplaceChild("fin_tail", CubeListBuilder.create()
                .texOffs(113, 91).addBox(-0.5F, -3F, 0F, 1F, 5F, 3F), PartPose.offset(0F, 13F, 8F));
        PartDefinition p_gill_left = root.addOrReplaceChild("gill_left", CubeListBuilder.create()
                .texOffs(110, 108).addBox(0F, -2F, -1F, 1F, 3F, 3F), PartPose.offset(5F, 13F, -5F));
        PartDefinition p_gill_right = root.addOrReplaceChild("gill_right", CubeListBuilder.create()
                .texOffs(101, 108).addBox(-1F, -2F, -1F, 1F, 3F, 3F), PartPose.offset(-5F, 13F, -5F));
        PartDefinition p_harness = root.addOrReplaceChild("harness", CubeListBuilder.create()
                .texOffs(0, 116).addBox(-5F, -1F, -2F, 10F, 1F, 4F)
                .texOffs(68, 57).addBox(-6F, 0F, -2F, 1F, 6F, 4F)
                .texOffs(57, 57).addBox(5F, 0F, -2F, 1F, 6F, 4F), PartPose.offset(0F, 10F, 1F));
        PartDefinition p_saddle = root.addOrReplaceChild("saddle", CubeListBuilder.create()
                .texOffs(0, 100).addBox(-5F, -1F, -3F, 10F, 1F, 6F)
                .texOffs(64, 0).addBox(-2F, -2F, -3F, 4F, 1F, 2F)
                .texOffs(72, 108).addBox(-6F, 0F, -2F, 1F, 3F, 4F)
                .texOffs(61, 108).addBox(5F, 0F, -2F, 1F, 3F, 4F), PartPose.offset(0F, 10F, 0F));
        PartDefinition p_wing_angel_left = root.addOrReplaceChild("wing_angel_left", CubeListBuilder.create()
                .texOffs(13, 108).addBox(0F, -5F, 0F, 5F, 6F, 1F)
                .texOffs(50, 108).addBox(5F, -7F, 0F, 4F, 6F, 1F)
                .texOffs(49, 116).addBox(9F, -7F, 0F, 3F, 4F, 1F), PartPose.offsetAndRotation(2F, 12F, 2F, 0F, 0F, 0.2094F));
        PartDefinition p_wing_angel_right = root.addOrReplaceChild("wing_angel_right", CubeListBuilder.create()
                .texOffs(0, 108).addBox(-5F, -5F, 0F, 5F, 6F, 1F)
                .texOffs(39, 108).addBox(-9F, -7F, 0F, 4F, 6F, 1F)
                .texOffs(40, 116).addBox(-12F, -7F, 0F, 3F, 4F, 1F), PartPose.offsetAndRotation(-2F, 12F, 2F, 0F, 0F, -0.2094F));
        PartDefinition p_wing_flight_left = root.addOrReplaceChild("wing_flight_left", CubeListBuilder.create()
                .texOffs(53, 81).addBox(0F, -6F, 0F, 6F, 8F, 1F)
                .texOffs(100, 91).addBox(6F, -7F, 0F, 5F, 7F, 1F)
                .texOffs(92, 108).addBox(11F, -7F, 0F, 3F, 5F, 1F), PartPose.offsetAndRotation(2F, 12F, 2F, 0F, 0F, 0.1745F));
        PartDefinition p_wing_flight_right = root.addOrReplaceChild("wing_flight_right", CubeListBuilder.create()
                .texOffs(38, 81).addBox(-6F, -6F, 0F, 6F, 8F, 1F)
                .texOffs(87, 91).addBox(-11F, -7F, 0F, 5F, 7F, 1F)
                .texOffs(83, 108).addBox(-14F, -7F, 0F, 3F, 5F, 1F), PartPose.offsetAndRotation(-2F, 12F, 2F, 0F, 0F, -0.1745F));
        PartDefinition p_dragon_spikes = root.addOrReplaceChild("dragon_spikes", CubeListBuilder.create()
                .texOffs(101, 116).addBox(-0.5F, -2F, -6F, 1F, 2F, 2F)
                .texOffs(76, 116).addBox(-0.5F, -3F, -1F, 1F, 3F, 2F)
                .texOffs(94, 116).addBox(-0.5F, -2F, 4F, 1F, 2F, 2F), PartPose.offset(0F, 10F, 0F));
        PartDefinition p_dragon_tail = root.addOrReplaceChild("dragon_tail", CubeListBuilder.create()
                .texOffs(75, 100).addBox(-1.5F, -1.5F, 0F, 3F, 2.5F, 4F)
                .texOffs(29, 116).addBox(-1F, -1F, 4F, 2F, 1.5F, 3F), PartPose.offsetAndRotation(0F, 13F, 8F, 0.1396F, 0F, 0F));
        PartDefinition p_wing_dragon_left = root.addOrReplaceChild("wing_dragon_left", CubeListBuilder.create()
                .texOffs(21, 122).addBox(0F, -2F, -1F, 8F, 1F, 2F)
                .texOffs(101, 122).addBox(8F, -3F, -1F, 6F, 1F, 2F)
                .texOffs(0, 91).addBox(0F, -1F, -0.5F, 13F, 7F, 1F), PartPose.offsetAndRotation(3F, 11F, 2F, 0F, -0.1396F, 0.1047F));
        PartDefinition p_wing_dragon_right = root.addOrReplaceChild("wing_dragon_right", CubeListBuilder.create()
                .texOffs(0, 122).addBox(-8F, -2F, -1F, 8F, 1F, 2F)
                .texOffs(84, 122).addBox(-14F, -3F, -1F, 6F, 1F, 2F)
                .texOffs(92, 81).addBox(-13F, -1F, -0.5F, 13F, 7F, 1F), PartPose.offsetAndRotation(-3F, 11F, 2F, 0F, 0.1396F, -0.1047F));
        PartDefinition p_portal_near_left = root.addOrReplaceChild("portal_near_left", CubeListBuilder.create()
                .texOffs(62, 71).addBox(-4F, -4F, -0.5F, 8F, 8F, 1F), PartPose.offset(18F, 14F, -0.5F));
        PartDefinition p_portal_near_right = root.addOrReplaceChild("portal_near_right", CubeListBuilder.create()
                .texOffs(43, 71).addBox(-4F, -4F, -0.5F, 8F, 8F, 1F), PartPose.offset(-18F, 14F, -0.5F));
        PartDefinition p_portal_far_left = root.addOrReplaceChild("portal_far_left", CubeListBuilder.create()
                .texOffs(100, 71).addBox(-4F, -4F, -0.5F, 8F, 8F, 1F), PartPose.offset(26F, 14F, -0.5F));
        PartDefinition p_portal_far_right = root.addOrReplaceChild("portal_far_right", CubeListBuilder.create()
                .texOffs(81, 71).addBox(-4F, -4F, -0.5F, 8F, 8F, 1F), PartPose.offset(-26F, 14F, -0.5F));
        PartDefinition p_cyborg_plate = root.addOrReplaceChild("cyborg_plate", CubeListBuilder.create()
                .texOffs(0, 32).addBox(-6F, -4F, -8F, 1F, 8F, 16F)
                .texOffs(35, 32).addBox(-5F, -5F, -8F, 6F, 1F, 16F), PartPose.offset(0F, 14F, 0F));
        PartDefinition p_cannon_left = root.addOrReplaceChild("cannon_left", CubeListBuilder.create()
                .texOffs(0, 57).addBox(-1.5F, -2F, -5F, 3F, 3F, 10F)
                .texOffs(105, 100).addBox(-2F, -2.5F, -8F, 4F, 4F, 3F), PartPose.offsetAndRotation(3F, 8F, 0F, 0.1047F, 0F, 0F));
        PartDefinition p_cannon_right = root.addOrReplaceChild("cannon_right", CubeListBuilder.create()
                .texOffs(80, 32).addBox(-1.5F, -2F, -5F, 3F, 3F, 10F)
                .texOffs(90, 100).addBox(-2F, -2.5F, -8F, 4F, 4F, 3F), PartPose.offsetAndRotation(-3F, 8F, 0F, 0.1047F, 0F, 0F));
        PartDefinition p_metal_wing_left = root.addOrReplaceChild("metal_wing_left", CubeListBuilder.create()
                .texOffs(0, 71).addBox(0F, 0F, -4F, 13F, 1F, 8F)
                .texOffs(54, 100).addBox(6F, 1F, -2F, 5F, 2F, 5F)
                .texOffs(88, 0).addBox(7F, 1.5F, -4F, 3F, 1F, 2F), PartPose.offsetAndRotation(5F, 11F, 1F, 0F, 0F, 0.0524F));
        PartDefinition p_metal_wing_right = root.addOrReplaceChild("metal_wing_right", CubeListBuilder.create()
                .texOffs(79, 57).addBox(-13F, 0F, -4F, 13F, 1F, 8F)
                .texOffs(33, 100).addBox(-11F, 1F, -2F, 5F, 2F, 5F)
                .texOffs(77, 0).addBox(-10F, 1.5F, -4F, 3F, 1F, 2F), PartPose.offsetAndRotation(-5F, 11F, 1F, 0F, 0F, -0.0524F));
        PartDefinition p_bomber_tail = root.addOrReplaceChild("bomber_tail", CubeListBuilder.create()
                .texOffs(81, 81).addBox(-0.5F, -5F, -1F, 1F, 5F, 4F)
                .texOffs(63, 122).addBox(-4F, -1F, 1F, 8F, 1F, 2F), PartPose.offset(0F, 10F, 8F));
        PartDefinition p_halo = p_head_anchor.addOrReplaceChild("halo", CubeListBuilder.create()
                .texOffs(64, 4).addBox(-3F, -0.5F, -4F, 6F, 1F, 1F)
                .texOffs(109, 0).addBox(-3F, -0.5F, 3F, 6F, 1F, 1F)
                .texOffs(19, 81).addBox(-4F, -0.5F, -4F, 1F, 1F, 8F)
                .texOffs(0, 81).addBox(3F, -0.5F, -4F, 1F, 1F, 8F), PartPose.offset(0F, -6.5F, -4F));
        PartDefinition p_horn_left = p_head_anchor.addOrReplaceChild("horn_left", CubeListBuilder.create()
                .texOffs(113, 116).addBox(0F, -2F, -2F, 1F, 3F, 1F)
                .texOffs(104, 0).addBox(0F, -4F, -3F, 1F, 2F, 1F), PartPose.offset(2F, -4F, -4F));
        PartDefinition p_horn_right = p_head_anchor.addOrReplaceChild("horn_right", CubeListBuilder.create()
                .texOffs(108, 116).addBox(-1F, -2F, -2F, 1F, 3F, 1F)
                .texOffs(99, 0).addBox(-1F, -4F, -3F, 1F, 2F, 1F), PartPose.offset(-2F, -4F, -4F));
        PartDefinition p_cyborg_visor = p_head_anchor.addOrReplaceChild("cyborg_visor", CubeListBuilder.create()
                .texOffs(83, 116).addBox(-4.5F, -2F, -8.5F, 4F, 3F, 1F)
                .texOffs(42, 122).addBox(-4.5F, -4F, -8.5F, 9F, 2F, 1F), PartPose.offset(0F, 0F, 0F));
        return LayerDefinition.create(mesh, 128, 128);
    }
}
