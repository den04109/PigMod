package com.example.examplemod;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Генетический Инжектор (Шприц). Инструмент многоразовый — заряжается Пробиркой с Мутацией
 * и применяет её на приручённую свинью, после чего разряжается (сама пробирка расходуется).
 *
 * Зарядка: ПКМ (в воздух, не по блоку/мобу) Инжектором в одной руке, когда в ДРУГОЙ руке
 * держится заполненная Пробирка с ДНК — пробирка расходуется, Инжектор запоминает мутацию.
 * Применение: логика вызывается из TameablePigEntity.mobInteract() при клике заряженным
 * Инжектором по своей приручённой свинье (см. applyToPig ниже).
 */
public class GeneticInjectorItem extends Item {

    private static final String TAG_MUTATION_ID = "LoadedMutationId";

    public GeneticInjectorItem(Properties properties) {
        super(properties);
    }

    public static String getLoadedMutationId(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        return tag.contains(TAG_MUTATION_ID) ? tag.getString(TAG_MUTATION_ID) : null;
    }

    private static void setLoadedMutationId(ItemStack stack, String mutationId) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        if (mutationId == null) {
            tag.remove(TAG_MUTATION_ID);
        } else {
            tag.putString(TAG_MUTATION_ID, mutationId);
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        // CustomModelData(1) переключает модель предмета на "заряженную" — см. override
        // в models/item/genetic_injector.json. Без мутации компонент снимается совсем,
        // а не выставляется в 0, чтобы не оставлять на предмете лишних данных.
        if (mutationId == null) {
            stack.remove(DataComponents.CUSTOM_MODEL_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(1));
        }
    }

    public static boolean isLoaded(ItemStack stack) {
        return getLoadedMutationId(stack) != null;
    }

    /**
     * Применяет заряженную мутацию к свинье. Вызывается из TameablePigEntity.mobInteract().
     * Инжектор разряжается только при успешной разблокировке — если у свиньи уже есть эта
     * мутация, пробирка не тратится зря, игрок получит сообщение и сможет использовать её
     * на другой свинье.
     *
     * @return true, если инжектор что-то сделал (обработал клик) — тогда вызывающий код
     *         должен считать взаимодействие обработанным
     */
    public static boolean applyToPig(ItemStack injectorStack, TameablePigEntity pig, Player player) {
        String mutationId = getLoadedMutationId(injectorStack);
        if (mutationId == null) {
            return false; // инжектор пуст — пусть обрабатывается как обычное взаимодействие
        }

        if (!player.level().isClientSide) {
            boolean unlocked = pig.unlockMutation(mutationId);
            if (unlocked) {
                setLoadedMutationId(injectorStack, null); // разряжаем инжектор
                pig.level().broadcastEntityEvent(pig, (byte) 7);
                player.level().playSound(null, pig.blockPosition(), SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 1.0F, 1.2F);
                player.sendSystemMessage(Component.translatable("message.pig_mod.injector.injected"));
            } else {
                player.sendSystemMessage(Component.translatable("message.pig_mod.injector.already"));
            }
        }
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack injectorStack = player.getItemInHand(hand);

        if (isLoaded(injectorStack)) {
            // Уже заряжен — ничего не делаем на ПКМ в воздух, разрядка происходит только по свинье.
            return InteractionResultHolder.pass(injectorStack);
        }

        InteractionHand otherHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack otherStack = player.getItemInHand(otherHand);
        String mutationId = DnaTubeItem.getMutationId(otherStack);

        if (mutationId == null) {
            player.displayClientMessage(Component.translatable("message.pig_mod.injector.need_tube").withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(injectorStack);
        }

        if (!level.isClientSide) {
            setLoadedMutationId(injectorStack, mutationId);

            // Пробирка не исчезает целиком — "перекачиваем" ДНК в инжектор, а на месте
            // заполненной пробирки остаётся пустая (как шприц, набравший жидкость из ампулы).
            // Если в руке лежала пачка из нескольких пробирок, забираем только одну из стака,
            // а пустую пробирку кладём в инвентарь отдельно — заменить ею весь стек в руке
            // означало бы стереть остальные заполненные пробирки.
            ItemStack emptyTube = new ItemStack(ModItems.EMPTY_DNA_TUBE.get());
            if (otherStack.getCount() <= 1) {
                player.setItemInHand(otherHand, emptyTube);
            } else {
                otherStack.shrink(1);
                if (!player.getInventory().add(emptyTube)) {
                    player.drop(emptyTube, false);
                }
            }

            level.playSound(null, player.blockPosition(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 1.0F, 1.5F);
        }
        return InteractionResultHolder.sidedSuccess(injectorStack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        String mutationId = getLoadedMutationId(stack);
        if (mutationId == null) {
            tooltip.add(Component.translatable("tooltip.pig_mod.injector.empty").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.pig_mod.injector.charge_hint").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            com.example.examplemod.mutation.MutationDefinition definition = com.example.examplemod.mutation.Mutations.get(mutationId);
            Component name = definition != null ? Component.translatable(definition.getDisplayNameKey()) : Component.literal(mutationId);
            tooltip.add(Component.translatable("tooltip.pig_mod.injector.charged", name.copy().withStyle(ChatFormatting.LIGHT_PURPLE)).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.pig_mod.injector.use_hint").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}