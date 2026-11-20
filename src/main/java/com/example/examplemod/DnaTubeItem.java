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

    /**
     * id мутации, жёстко привязанный к самому предмету. У 31 пробирки из креатива он задан,
     * у старой универсальной пробирки (pig_mod:dna_tube, оставлена для старых миров) — null,
     * и тогда мутация читается из NBT, как раньше.
     */
    private final String fixedMutationId;

    public DnaTubeItem(Properties properties) {
        this(properties, null);
    }

    public DnaTubeItem(Properties properties, String fixedMutationId) {
        super(properties);
        this.fixedMutationId = fixedMutationId;
    }

    public String getFixedMutationId() {
        return this.fixedMutationId;
    }

    /**
     * Создаёт заполненную пробирку с указанной мутацией (используется Генетическим Экстрактором).
     * Сначала ищем отдельный зарегистрированный предмет под эту мутацию — именно он лежит
     * в креативе. Если мутации нет в списке ModItems.MUTATION_IDS, откатываемся на старую
     * универсальную пробирку с NBT, чтобы экстрактор в любом случае выдал рабочий результат.
     */
    public static ItemStack createFilled(String mutationId) {
        net.minecraft.world.item.Item dedicated = ModItems.dnaTube(mutationId);
        if (dedicated != null) {
            return new ItemStack(dedicated);
        }
        ItemStack stack = new ItemStack(ModItems.DNA_TUBE.get());
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_MUTATION_ID, mutationId);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    /** Id мутации в пробирке, или null, если пробирка пустая (либо это не DNA-пробирка). */
    public static String getMutationId(ItemStack stack) {
        if (!(stack.getItem() instanceof DnaTubeItem tube)) {
            return null;
        }
        if (tube.fixedMutationId != null) {
            return tube.fixedMutationId;
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
        if (mutationId == null) {
            return 0xFFFFFF;
        }
        // Цвета разводятся по кругу в порядке регистрации мутаций — так 31 пробирка в креативе
        // идёт ровной радугой и соседние никогда не сливаются. Для неизвестного id (мутация не
        // из списка) остаётся старое поведение — стабильный цвет по хешу.
        int index = -1;
        for (int i = 0; i < ModItems.MUTATION_IDS.length; i++) {
            if (ModItems.MUTATION_IDS[i].equals(mutationId)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            int hue = Math.floorMod(mutationId.hashCode(), 360);
            return java.awt.Color.HSBtoRGB(hue / 360.0f, 0.65f, 1.0f) & 0xFFFFFF;
        }
        float hue = (index * 360.0f / ModItems.MUTATION_IDS.length) / 360.0f;
        float saturation = (index % 2 == 0) ? 0.72f : 0.55f;
        float brightness = (index % 3 == 0) ? 1.0f : 0.9f;
        return java.awt.Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String mutationId = getMutationId(stack);
        if (mutationId == null) {
            tooltip.add(Component.translatable("tooltip.pig_mod.dna_tube.empty").withStyle(ChatFormatting.GRAY));
            return;
        }

        MutationDefinition definition = Mutations.get(mutationId);
        if (definition != null) {
            tooltip.add(Component.translatable("tooltip.pig_mod.dna_tube.mutation", Component.translatable(definition.getDisplayNameKey()).withStyle(ChatFormatting.LIGHT_PURPLE)).withStyle(ChatFormatting.GRAY));
        } else {
            // Мутация ещё не зарегистрирована в моде (реестр пока пуст) — показываем как есть.
            tooltip.add(Component.translatable("tooltip.pig_mod.dna_tube.mutation", Component.literal(mutationId).withStyle(ChatFormatting.LIGHT_PURPLE)).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("tooltip.pig_mod.dna_tube.use_hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}