package com.example.examplemod;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import com.example.examplemod.mutation.MutationDefinition;
import com.example.examplemod.mutation.Mutations;

import java.util.List;

/**
 * Пробирка с ДНК. Пустая пробирка (без NBT) — сырьё, кладётся в Слот основы Генетического
 * Экстрактора. Заполненная пробирка (с тегом MutationId) — результат синтеза, содержит одну
 * конкретную мутацию и используется, чтобы зарядить Генетический Инжектор.
 *
 * Один и тот же класс предмета обслуживает оба состояния (пустая/заполненная) — это удобнее,
 * чем заводить два разных Item'а, т.к. и Экстрактор, и Инжектор просто проверяют наличие NBT.
 */
public class DnaTubeItem extends Item {

    private static final String TAG_MUTATION_ID = "MutationId";

    public DnaTubeItem(Properties properties) {
        super(properties);
    }

    /** Создаёт заполненную пробирку с указанной мутацией (используется Генетическим Экстрактором). */
    public static ItemStack createFilled(String mutationId) {
        ItemStack stack = new ItemStack(ModItems.DNA_TUBE.get());
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_MUTATION_ID, mutationId);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    /** Id мутации в пробирке, или null, если пробирка пустая (либо это не DNA-пробирка). */
    public static String getMutationId(ItemStack stack) {
        if (!(stack.getItem() instanceof DnaTubeItem)) {
            return null;
        }
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        return tag.contains(TAG_MUTATION_ID) ? tag.getString(TAG_MUTATION_ID) : null;
    }

    public static boolean isFilled(ItemStack stack) {
        return getMutationId(stack) != null;
    }

    /**
     * Псевдослучайный, но стабильный цвет для мутации по её id — используется и в тултипе,
     * и как тинт модели (см. ExampleModClient), пока нет отдельной текстуры под каждую мутацию.
     */
    public static int colorForMutation(String mutationId) {
        int hue = Math.floorMod(mutationId.hashCode(), 360);
        return java.awt.Color.HSBtoRGB(hue / 360.0f, 0.65f, 1.0f) & 0xFFFFFF;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String mutationId = getMutationId(stack);
        if (mutationId == null) {
            tooltip.add(Component.literal("Пустая пробирка — заполняется в Генетическом Экстракторе").withStyle(ChatFormatting.GRAY));
            return;
        }

        MutationDefinition definition = Mutations.get(mutationId);
        if (definition != null) {
            tooltip.add(Component.literal("Мутация: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.translatable(definition.getDisplayNameKey()).withStyle(ChatFormatting.LIGHT_PURPLE)));
        } else {
            // Мутация ещё не зарегистрирована в моде (реестр пока пуст) — показываем как есть.
            tooltip.add(Component.literal("Мутация: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(mutationId).withStyle(ChatFormatting.LIGHT_PURPLE)));
        }
        tooltip.add(Component.literal("ПКМ по заряженному Инжектору с этой пробиркой в другой руке").withStyle(ChatFormatting.DARK_GRAY));
    }
}