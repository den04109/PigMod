package com.example.examplemod;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

public class UpgradeItem extends Item {
    private final String upgradeName;

    public UpgradeItem(Properties properties, String upgradeName) {
        super(properties);
        this.upgradeName = upgradeName;
    }

    // Вспомогательные методы чтения NBT из предмета
    public static int getLevel(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return customData.copyTag().getInt("UpgradeLevel") == 0 ? 1 : customData.copyTag().getInt("UpgradeLevel");
    }

    public static int getExp(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        return customData.copyTag().getInt("UpgradeExp");
    }

    // Метод добавления опыта улучшению
    public static void addExp(ItemStack stack, int amount) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();

        int level = tag.getInt("UpgradeLevel") == 0 ? 1 : tag.getInt("UpgradeLevel");
        int exp = tag.getInt("UpgradeExp") + amount;
        int expNeeded = level * 20; // 20 опыта за уровень

        while (exp >= expNeeded) {
            exp -= expNeeded;
            level++;
            expNeeded = level * 20;
        }

        tag.putInt("UpgradeLevel", level);
        tag.putInt("UpgradeExp", exp);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    // КРАСИВАЯ ПОДСКАЗКА ПРИ НАВЕДЕНИИ МЫШКОЙ
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        int level = getLevel(stack);
        int exp = getExp(stack);
        int expNeeded = level * 20;

        tooltipComponents.add(Component.translatable("tooltip.pig_mod.upgrade.type", Component.translatable(this.upgradeName).withStyle(ChatFormatting.GOLD)).withStyle(ChatFormatting.GRAY));
        
        tooltipComponents.add(Component.translatable("tooltip.pig_mod.upgrade.level", Component.literal(String.valueOf(level)).withStyle(ChatFormatting.GREEN)).withStyle(ChatFormatting.GRAY));

        tooltipComponents.add(Component.translatable("tooltip.pig_mod.upgrade.exp", Component.literal(exp + " / " + expNeeded).withStyle(ChatFormatting.AQUA)).withStyle(ChatFormatting.GRAY));
    }
}