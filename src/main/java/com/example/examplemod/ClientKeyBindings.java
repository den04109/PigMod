package com.example.examplemod;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Клавиши мода, настраиваемые в ванильных Настройках управления (Options -> Controls).
 *
 * Подъём/снижение при полёте верхом (см. TameablePigEntity.travel()) намеренно НЕ заведены сюда
 * отдельными клавишами — они используют уже существующие ванильные "Прыжок" (Пробел) и "Красться"
 * (Sneak, обычно Shift), которые и так настраиваются в тех же Настройках управления, так что
 * отдельная привязка была бы просто дублированием. Единственная НОВАЯ клавиша — спешивание,
 * потому что Sneak при полёте занят под снижение (см. PigEventHandlers.onPigDismountAttempt).
 */
public final class ClientKeyBindings {

    public static final String CATEGORY = "key.categories.pig_mod";

    public static final KeyMapping DISMOUNT_FLYING_PIG = new KeyMapping(
            "key.pig_mod.dismount_flying_pig",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_X,
            CATEGORY
    );

    private ClientKeyBindings() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(DISMOUNT_FLYING_PIG);
    }
}