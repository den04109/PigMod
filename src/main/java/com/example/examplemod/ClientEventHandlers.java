package com.example.examplemod;

import com.example.examplemod.network.PigDismountPacket;
import com.example.examplemod.network.PigFlightInputPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Игровая шина (не мод-шина) — здесь только тик клиента, поэтому отдельный класс от
 * ExampleModClient (который сидит на мод-шине ради регистрации рендереров/экранов/клавиш).
 */
@EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public class ClientEventHandlers {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        // Планшет: выбранная свинья подсвечивается в мире. Это локальный визуальный эффект,
        // поэтому другие игроки не получают лишний glowing-эффект.
        if (mc.player != null && mc.level != null) {
            ItemStack tablet = mc.player.getMainHandItem().is(ModItems.CONTROL_TABLET.get()) ? mc.player.getMainHandItem()
                    : (mc.player.getOffhandItem().is(ModItems.CONTROL_TABLET.get()) ? mc.player.getOffhandItem() : ItemStack.EMPTY);
            java.util.UUID selected = PigControlTabletItem.getSelectedPig(tablet);
            for (TameablePigEntity pig : mc.level.getEntitiesOfClass(TameablePigEntity.class, mc.player.getBoundingBox().inflate(96))) {
                pig.setGlowingTag(selected != null && selected.equals(pig.getUUID()));
            }
        }
        // consumeClick() отдаёт "один клик" за раз — если игрок быстро тапнул клавишу несколько
        // раз между тиками, цикл разберёт их все, а не потеряет лишние нажатия.
        while (ClientKeyBindings.DISMOUNT_FLYING_PIG.consumeClick()) {
            if (mc.player != null && mc.player.getVehicle() instanceof TameablePigEntity pig
                    && pig.hasMutation("flight")) {
                PacketDistributor.sendToServer(new PigDismountPacket(pig.getId()));
            }
        }

        // Space/Shift для летающей свиньи не должны зависеть от вертикальной скорости игрока:
        // отправляем реальное состояние клавиш на сервер каждый тик, пока игрок верхом.
        if (mc.player != null && mc.player.getVehicle() instanceof TameablePigEntity pig
                && pig.hasMutation("flight")) {
            // Читаем физические клавиши напрямую, а не через состояние player/input:
            // при езде на кастомном мобе ваниль может использовать эти KeyMapping для
            // собственных действий и не передавать их как вертикальный ввод транспорта.
            long window = mc.getWindow().getWindow();
            boolean up = net.minecraft.client.Minecraft.getInstance().mouseHandler != null
                    && GLFW.glfwGetKey(window, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS;
            boolean down = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;

            // Клиент должен знать состояние сам, иначе его локальная физика транспорта
            // не увидит вертикальный ввод до прихода ответа от сервера. Сервер получает
            // тот же state через пакет для авторитетной симуляции.
            pig.setFlightInput(up, down);

            // При полёте Shift — это снижение, а не приседание. Гашу crouch-состояние
            // только визуально/локально; само нажатие ниже всё равно передаётся в пакет.
            if (down) {
                // Shift при полёте используется только как вертикальное управление.
                // Снимаем и флаг sneak, и саму визуальную позу игрока: иначе клиент
                // продолжает показывать приседание поверх свиньи.
                mc.player.setShiftKeyDown(false);
                mc.player.setPose(Pose.STANDING);
                mc.player.setSwimming(false);
            }

            // Нерушимый должен также убирать локальное состояние горения, иначе огненный
            // overlay и звуки могут кратко появляться между серверными обновлениями.
            if (pig.hasMutation("fire_immunity")) {
                // Сбрасываем не только урон от огня, но и само состояние горения,
                // чтобы клиент не рисовал fire overlay и не проигрывал fire sound.
                mc.player.clearFire();
                mc.player.setRemainingFireTicks(0);
                mc.player.setSharedFlagOnFire(false);
            }

            PacketDistributor.sendToServer(new PigFlightInputPacket(pig.getId(), up, down));
        }
    }
}