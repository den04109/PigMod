package com.example.examplemod.client;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.TameablePigEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PigModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Слой деталей мутаций. Сама свинья рисуется до него ванильной PigModel, а здесь
 * поверх добавляются крылья/нимб/пушки/сбруя и настоящие ванильные предметы
 * (топор, кирка, зелья, эндер-сундуки) через ItemRenderer.
 *
 * Все детали живут в одной модели PigAddonModel и включаются видимостью — за
 * кадр рисуется ровно то, что соответствует набору мутаций конкретной свиньи.
 */
public class PigAddonLayer extends RenderLayer<TameablePigEntity, PigModel<TameablePigEntity>> {

    private static final ResourceLocation ADDON_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "textures/entity/pig_addons.png");

    private final PigAddonModel addons;

    public PigAddonLayer(RenderLayerParent<TameablePigEntity, PigModel<TameablePigEntity>> parent,
                         EntityRendererProvider.Context context) {
        super(parent);
        this.addons = new PigAddonModel(context.bakeLayer(PigAddonModel.LAYER));
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight,
                       TameablePigEntity pig, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (pig.isInvisible()) {
            return;
        }

        this.addons.hideAll();
        boolean bomber = pig.hasMutation("destroyer") && pig.isRaidMode();
        boolean anyGeometry = selectParts(pig, bomber);

        // Летящая свинья машет крыльями заметно быстрее, чем стоящая на земле.
        float flapSpeed = pig.onGround() ? 1.0F : 2.2F;
        this.addons.setupAnim(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, flapSpeed);

        boolean baby = pig.isBaby();
        if (anyGeometry) {
            VertexConsumer buf = buffer.getBuffer(RenderType.entityCutoutNoCull(ADDON_TEXTURE));

            // Голова и туловище у детёныша смещаются/масштабируются по-разному —
            // повторяем то, что делает AgeableListModel для ванильной свиньи.
            poseStack.pushPose();
            if (baby) {
                poseStack.translate(0.0F, 0.25F, 0.25F);
            }
            this.addons.renderHead(poseStack, buf, packedLight, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();

            poseStack.pushPose();
            if (baby) {
                poseStack.scale(0.5F, 0.5F, 0.5F);
                poseStack.translate(0.0F, 1.5F, 0.0F);
            }
            this.addons.renderBody(poseStack, buf, packedLight, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }

        poseStack.pushPose();
        if (baby) {
            poseStack.scale(0.5F, 0.5F, 0.5F);
            poseStack.translate(0.0F, 1.5F, 0.0F);
        }
        renderVanillaItems(poseStack, buffer, packedLight, pig, bomber);
        poseStack.popPose();
    }

    /**
     * Включает детали под набор мутаций свиньи. Крылья взаимоисключающи: если есть
     * несколько "крылатых" мутаций сразу, показываем самые старшие, иначе они
     * налезали бы друг на друга.
     */
    private boolean selectParts(TameablePigEntity pig, boolean bomber) {
        boolean any = false;

        if (pig.hasMutation("carrot_pig")) {
            this.addons.show("tail");
            any = true;
        }
        if (pig.hasMutation("archer")) {
            this.addons.show("quiver");
            any = true;
        }
        if (pig.hasMutation("courier")) {
            this.addons.show("chest_mount_left");
            this.addons.show("chest_mount_right");
            any = true;
        }
        if (pig.hasMutation("aquatic")) {
            this.addons.show("fin_dorsal");
            this.addons.show("fin_tail");
            this.addons.show("gill_left");
            this.addons.show("gill_right");
            any = true;
        }
        if (pig.hasMutation("alchemist")) {
            this.addons.show("harness");
            any = true;
        }
        if (pig.isSaddled()) {
            this.addons.show("saddle");
            any = true;
        }
        if (pig.hasMutation("angel")) {
            this.addons.show("halo");
            any = true;
        }
        // Порталы Экстрасенса открываются только на время залпа — флаг приходит с сервера.
        if (pig.hasMutation("psychic") && pig.isPsychicPortalActive()) {
            this.addons.show("portal_near_left");
            this.addons.show("portal_near_right");
            if (pig.getMutationLevel("psychic") >= 3) {
                this.addons.show("portal_far_left");
                this.addons.show("portal_far_right");
            }
            any = true;
        }
        if (pig.hasMutation("dragon")) {
            this.addons.show("horn_left");
            this.addons.show("horn_right");
            this.addons.show("dragon_spikes");
            this.addons.show("dragon_tail");
            any = true;
        }
        if (pig.hasMutation("destroyer")) {
            this.addons.show("cyborg_plate");
            this.addons.show("cyborg_visor");
            if (bomber) {
                this.addons.show("bomber_tail");
            } else {
                this.addons.show("cannon_left");
                this.addons.show("cannon_right");
            }
            any = true;
        }

        // приоритет крыльев: дракон > бомбардировщик > ангел > полёт > планирование
        String wings = null;
        if (pig.hasMutation("dragon")) {
            wings = "wing_dragon";
        } else if (bomber) {
            wings = "metal_wing";
        } else if (pig.hasMutation("angel")) {
            wings = "wing_angel";
        } else if (pig.hasMutation("flight")) {
            wings = "wing_flight";
        } else if (pig.hasMutation("gliding")) {
            wings = "glide_wing";
        }
        if (wings != null) {
            this.addons.show(wings + "_left");
            this.addons.show(wings + "_right");
            any = true;
        }
        return any;
    }

    /**
     * Ванильные предметы вместо самодельной геометрии: морковка на хвосте, инструмент
     * рабочей профессии, взрывные зелья алхимика, эндер-сундуки курьера.
     */
    private void renderVanillaItems(PoseStack poseStack, MultiBufferSource buffer, int light,
                                    TameablePigEntity pig, boolean bomber) {
        if (pig.hasMutation("carrot_pig")) {
            item(poseStack, buffer, light, pig, this.addons.part("tail"),
                    new ItemStack(Items.CARROT), ItemDisplayContext.FIXED,
                    0.0F, 2.0F, 1.0F, -30.0F, 0.0F, 0.0F, 0.8F);
        }

        ItemStack tool = workTool(pig);
        if (!tool.isEmpty()) {
            item(poseStack, buffer, light, pig, this.addons.handAnchor(),
                    tool, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
                    -1.5F, 2.0F, -3.0F, 0.0F, -90.0F, 35.0F, 1.0F);
        }

        if (pig.hasMutation("alchemist")) {
            ItemStack potion = new ItemStack(Items.SPLASH_POTION);
            ModelPart harness = this.addons.part("harness");
            item(poseStack, buffer, light, pig, harness, potion, ItemDisplayContext.FIXED,
                    6.5F, -3.0F, -1.0F, 0.0F, 90.0F, 0.0F, 0.75F);
            item(poseStack, buffer, light, pig, harness, potion, ItemDisplayContext.FIXED,
                    6.5F, -3.0F, 4.0F, 0.0F, 90.0F, 0.0F, 0.75F);
            item(poseStack, buffer, light, pig, harness, potion, ItemDisplayContext.FIXED,
                    -6.5F, -3.0F, -1.0F, 0.0F, -90.0F, 0.0F, 0.75F);
            item(poseStack, buffer, light, pig, harness, potion, ItemDisplayContext.FIXED,
                    -6.5F, -3.0F, 4.0F, 0.0F, -90.0F, 0.0F, 0.75F);
        }

        if (pig.hasMutation("courier")) {
            ItemStack chest = new ItemStack(Items.ENDER_CHEST);
            item(poseStack, buffer, light, pig, this.addons.part("chest_mount_left"),
                    chest, ItemDisplayContext.FIXED, 2.5F, -0.5F, 1.0F, 0.0F, 90.0F, 0.0F, 0.9F);
            item(poseStack, buffer, light, pig, this.addons.part("chest_mount_right"),
                    chest, ItemDisplayContext.FIXED, -2.5F, -0.5F, 1.0F, 0.0F, -90.0F, 0.0F, 0.9F);
        }
    }

    private ItemStack workTool(TameablePigEntity pig) {
        if (pig.hasMutation("lumberjack")) {
            return new ItemStack(Items.IRON_AXE);
        }
        if (pig.hasMutation("miner")) {
            return new ItemStack(Items.IRON_PICKAXE);
        }
        if (pig.hasMutation("farmer")) {
            return new ItemStack(Items.IRON_HOE);
        }
        if (pig.hasMutation("fisher")) {
            return new ItemStack(Items.FISHING_ROD);
        }
        if (pig.hasMutation("archer")) {
            return new ItemStack(Items.BOW);
        }
        return ItemStack.EMPTY;
    }

    /**
     * Ставит настоящий ItemStack в точку кости.
     *
     * Смещение задаётся в пикселях модели (как в Blockbench, ось Y вверх), поэтому
     * по Y оно инвертируется: пространство модели энтити перевёрнуто. По той же
     * причине предмету нужен разворот на 180 градусов вокруг Z, иначе он висит
     * вверх ногами и зеркально.
     */
    private void item(PoseStack poseStack, MultiBufferSource buffer, int light, TameablePigEntity pig,
                      ModelPart anchor, ItemStack stack, ItemDisplayContext context,
                      float offX, float offY, float offZ,
                      float rotX, float rotY, float rotZ, float scale) {
        if (anchor == null || stack.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        anchor.translateAndRotate(poseStack);
        poseStack.translate(offX / 16.0F, -offY / 16.0F, offZ / 16.0F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
        if (rotY != 0.0F) {
            poseStack.mulPose(Axis.YP.rotationDegrees(rotY));
        }
        if (rotX != 0.0F) {
            poseStack.mulPose(Axis.XP.rotationDegrees(rotX));
        }
        if (rotZ != 0.0F) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(rotZ));
        }
        poseStack.scale(scale, scale, scale);
        Minecraft.getInstance().getItemRenderer().renderStatic(
                stack, context, light, OverlayTexture.NO_OVERLAY,
                poseStack, buffer, pig.level(), pig.getId());
        poseStack.popPose();
    }
}
