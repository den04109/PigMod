package com.example.examplemod;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

/**
 * Посох Курьера. Двухшаговая привязка:
 *  1. ПКМ по сундуку (любому блоку с Container — сундук, бочка, эндер-сундук и т.п.) — запоминает
 *     его координаты в самом посохе.
 *  2. ПКМ по своей приручённой свинье с мутацией "courier" — переносит эту привязку на свинью
 *     (см. TameablePigEntity.mobInteract). Посох при этом не расходуется, можно привязывать других свиней.
 */
public class CourierStaffItem extends Item {

    private static final String TAG_POS = "BoundChestPos";

    public CourierStaffItem(Properties properties) {
        super(properties);
    }

    public static BlockPos getBoundChest(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        return tag.contains(TAG_POS) ? BlockPos.of(tag.getLong(TAG_POS)) : null;
    }

    private static void setBoundChest(ItemStack stack, BlockPos pos) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        tag.putLong(TAG_POS, pos.asLong());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        // onItemUseFirst (а не useOn!) — вызывается ДО того, как блок обработает клик сам.
        // Обычный useOn тут не подошёл бы: сундук сначала открыл бы своё меню (ChestBlock.useWithoutItem
        // сам "съедает" клик раньше предмета), и посох просто никогда бы не получил управление.
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockEntity blockEntity = level.getBlockEntity(pos);

        if (!(blockEntity instanceof net.minecraft.world.Container)) {
            return InteractionResult.PASS; // не хранилище — пусть блок обработает клик как обычно
        }

        if (!level.isClientSide) {
            setBoundChest(stack, pos);
            Player player = context.getPlayer();
            if (player != null) {
                player.displayClientMessage(Component.translatable("message.pig_mod.courier_staff.bound").withStyle(ChatFormatting.GREEN), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        BlockPos pos = getBoundChest(stack);
        if (pos == null) {
            tooltip.add(Component.translatable("tooltip.pig_mod.courier_staff.unbound").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.pig_mod.courier_staff.bound_to", pos.getX(), pos.getY(), pos.getZ()).withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("tooltip.pig_mod.courier_staff.bind_hint", Component.translatable("mutation.pig_mod.courier")).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}