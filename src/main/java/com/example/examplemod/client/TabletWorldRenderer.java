package com.example.examplemod.client;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.PigControlTabletItem;
import com.example.examplemod.TameablePigEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.UUID;

/**
 * Визуальные подсказки Планшета прямо в мире (рисуются только тому игроку, который держит
 * планшет в руке — это чисто клиентский эффект, другие игроки ничего лишнего не видят):
 *
 *  - выбранная свинья обведена голубой рамкой (плюс ванильное свечение, см. ClientEventHandlers);
 *  - привязанный сундук обведён золотой рамкой, и над ним висит покачивающаяся стрелка вниз;
 *  - рабочая область показана рамкой по всем границам, первая точка (пока не задана вторая) —
 *    оранжевым кубом.
 *
 * Всё рисуется линиями через LevelRenderer.renderLineBox — это тот же способ, которым ваниль
 * рисует хитбоксы (F3+B), он не требует собственных шейдеров и корректно работает со всеми
 * настройками графики.
 */
@EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public class TabletWorldRenderer {

    /** Высота, на которую "растягивается" плоская область, чтобы рамку было видно издалека. */
    private static final int MIN_AREA_HEIGHT = 3;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        ItemStack tablet = PigControlTabletItem.findHeld(mc.player);
        if (tablet.isEmpty()) return;

        UUID selected = PigControlTabletItem.getSelectedPig(tablet);
        if (selected == null) return;

        TameablePigEntity pig = null;
        for (TameablePigEntity candidate : mc.level.getEntitiesOfClass(
                TameablePigEntity.class, mc.player.getBoundingBox().inflate(160))) {
            if (candidate.getUUID().equals(selected)) {
                pig = candidate;
                break;
            }
        }
        if (pig == null) return;

        float pulse = 0.55F + 0.45F * (float) Math.sin((System.currentTimeMillis() % 2000L) / 2000.0 * Math.PI * 2.0);

        PoseStack pose = event.getPoseStack();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());

        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);

        // --- 1. Выбранная свинья ---
        AABB pigBox = pig.getBoundingBox().inflate(0.08D);
        LevelRenderer.renderLineBox(pose, lines, pigBox, 0.13F, 0.83F, 0.93F, 0.9F);
        LevelRenderer.renderLineBox(pose, lines, pigBox.inflate(0.06D), 0.13F, 0.83F, 0.93F, 0.35F * pulse + 0.2F);

        // --- 2. Привязанный сундук + стрелка над ним ---
        BlockPos chest = pig.getCourierChestPos();
        if (chest != null) {
            AABB chestBox = new AABB(chest).inflate(0.02D);
            LevelRenderer.renderLineBox(pose, lines, chestBox, 1.0F, 0.78F, 0.16F, 0.95F);
            drawArrow(pose, lines, chest, pulse);
        }

        // --- 3. Рабочая область ---
        BlockPos a = pig.getWorkAreaPoint1();
        BlockPos b = pig.getWorkAreaPoint2();
        if (a != null && b != null) {
            double minX = Math.min(a.getX(), b.getX());
            double maxX = Math.max(a.getX(), b.getX()) + 1.0D;
            double minZ = Math.min(a.getZ(), b.getZ());
            double maxZ = Math.max(a.getZ(), b.getZ()) + 1.0D;
            double minY = Math.min(a.getY(), b.getY());
            double maxY = Math.max(Math.max(a.getY(), b.getY()) + 1.0D, minY + MIN_AREA_HEIGHT);

            AABB area = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
            LevelRenderer.renderLineBox(pose, lines, area, 0.29F, 0.87F, 0.50F, 0.85F);
            // Отдельная рамка по земле — так границу видно, даже если стоишь внутри области.
            LevelRenderer.renderLineBox(pose, lines,
                    new AABB(minX, minY, minZ, maxX, minY + 0.05D, maxZ),
                    0.29F, 0.87F, 0.50F, 0.55F + 0.35F * pulse);
            // Угловые столбы — ориентир издалека.
            double pillarTop = maxY + 1.0D;
            cornerPillar(pose, lines, minX, minY, minZ, pillarTop);
            cornerPillar(pose, lines, maxX - 0.2D, minY, minZ, pillarTop);
            cornerPillar(pose, lines, minX, minY, maxZ - 0.2D, pillarTop);
            cornerPillar(pose, lines, maxX - 0.2D, minY, maxZ - 0.2D, pillarTop);
        } else if (a != null) {
            LevelRenderer.renderLineBox(pose, lines, new AABB(a).inflate(0.03D),
                    1.0F, 0.6F, 0.1F, 0.5F + 0.45F * pulse);
        }

        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    private static void cornerPillar(PoseStack pose, VertexConsumer lines, double x, double y, double z, double top) {
        LevelRenderer.renderLineBox(pose, lines, new AABB(x, y, z, x + 0.2D, top, z + 0.2D),
                0.29F, 0.87F, 0.50F, 0.7F);
    }

    /**
     * Стрелка вниз над привязанным сундуком. Собрана из тонких коробок-линий "пиксельной"
     * ступенькой — в стиле самой игры, и при этом не требует собственного шейдера/меша.
     */
    private static void drawArrow(PoseStack pose, VertexConsumer lines, BlockPos chest, float pulse) {
        double cx = chest.getX() + 0.5D;
        double cz = chest.getZ() + 0.5D;
        double base = chest.getY() + 1.15D + 0.18D * pulse; // лёгкое покачивание вверх-вниз

        float r = 1.0F, g = 0.78F, b = 0.16F, alpha = 0.95F;

        // Древко
        LevelRenderer.renderLineBox(pose, lines,
                new AABB(cx - 0.055D, base + 0.30D, cz - 0.055D, cx + 0.055D, base + 0.85D, cz + 0.055D),
                r, g, b, alpha);

        // Наконечник: три ступеньки, сужающиеся книзу
        LevelRenderer.renderLineBox(pose, lines,
                new AABB(cx - 0.26D, base + 0.24D, cz - 0.045D, cx + 0.26D, base + 0.32D, cz + 0.045D),
                r, g, b, alpha);
        LevelRenderer.renderLineBox(pose, lines,
                new AABB(cx - 0.17D, base + 0.14D, cz - 0.045D, cx + 0.17D, base + 0.24D, cz + 0.045D),
                r, g, b, alpha);
        LevelRenderer.renderLineBox(pose, lines,
                new AABB(cx - 0.08D, base + 0.02D, cz - 0.045D, cx + 0.08D, base + 0.14D, cz + 0.045D),
                r, g, b, alpha);

        // Та же стрелка, повёрнутая на 90° по Z — чтобы она читалась с любой стороны
        LevelRenderer.renderLineBox(pose, lines,
                new AABB(cx - 0.045D, base + 0.24D, cz - 0.26D, cx + 0.045D, base + 0.32D, cz + 0.26D),
                r, g, b, alpha);
        LevelRenderer.renderLineBox(pose, lines,
                new AABB(cx - 0.045D, base + 0.14D, cz - 0.17D, cx + 0.045D, base + 0.24D, cz + 0.17D),
                r, g, b, alpha);
        LevelRenderer.renderLineBox(pose, lines,
                new AABB(cx - 0.045D, base + 0.02D, cz - 0.08D, cx + 0.045D, base + 0.14D, cz + 0.08D),
                r, g, b, alpha);
    }
}
