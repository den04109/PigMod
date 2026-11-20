package com.example.examplemod;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.Container;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Планшет управления свиньями.
 *
 * Схема управления (важно: ПКМ по блоку больше НЕ открывает планшет — именно это мешало
 * спокойно выделять область, т.к. экран выскакивал на каждый клик по земле):
 *   - ПКМ по прирученной свинье        -> выбрать её (она подсвечивается в мире)
 *   - ПКМ по сундуку/контейнеру        -> привязать этот сундук к выбранной свинье
 *   - ПКМ по обычному блоку            -> 1-я и 2-я точки рабочей области
 *   - Shift + ПКМ (по блоку или в воздух) -> открыть сам планшет
 *   - ПКМ в воздух                     -> открыть сам планшет
 */
public class PigControlTabletItem extends Item {
    private static final String SELECTED = "SelectedPig";
    private static final String BOUND_PIGS = "BoundPigs";

    public PigControlTabletItem(Properties properties) { super(properties); }

    private static CompoundTag tag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
    }

    private static void setTag(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
    }

    public static UUID getSelectedPig(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof PigControlTabletItem)) return null;
        CompoundTag t = tag(stack);
        return t.hasUUID(SELECTED) ? t.getUUID(SELECTED) : null;
    }

    public static void selectPig(ItemStack stack, TameablePigEntity pig) {
        CompoundTag t = tag(stack);
        t.putUUID(SELECTED, pig.getUUID());
        ListTag list = t.getList(BOUND_PIGS, Tag.TAG_INT_ARRAY);
        boolean found = false;
        for (int i = 0; i < list.size(); i++) {
            try {
                if (net.minecraft.nbt.NbtUtils.loadUUID(list.get(i)).equals(pig.getUUID())) { found = true; break; }
            } catch (Exception ignored) { }
        }
        if (!found) list.add(net.minecraft.nbt.NbtUtils.createUUID(pig.getUUID()));
        t.put(BOUND_PIGS, list);
        setTag(stack, t);
    }

    /** Убирает свинью из памяти планшета (кнопка "×" в списке). */
    public static void unbindPig(ItemStack stack, UUID uuid) {
        CompoundTag t = tag(stack);
        ListTag list = t.getList(BOUND_PIGS, Tag.TAG_INT_ARRAY);
        ListTag rebuilt = new ListTag();
        for (int i = 0; i < list.size(); i++) {
            try {
                if (!net.minecraft.nbt.NbtUtils.loadUUID(list.get(i)).equals(uuid)) rebuilt.add(list.get(i));
            } catch (Exception ignored) { }
        }
        t.put(BOUND_PIGS, rebuilt);
        if (t.hasUUID(SELECTED) && t.getUUID(SELECTED).equals(uuid)) t.remove(SELECTED);
        CompoundTag snapshots = t.getCompound("PigSnapshots");
        snapshots.remove(uuid.toString());
        t.put("PigSnapshots", snapshots);
        setTag(stack, t);
    }

    public static List<UUID> getBoundPigs(ItemStack stack) {
        List<UUID> out = new ArrayList<>();
        if (stack.isEmpty() || !(stack.getItem() instanceof PigControlTabletItem)) return out;
        CompoundTag t = tag(stack);
        ListTag list = t.getList(BOUND_PIGS, Tag.TAG_INT_ARRAY);
        for (int i = 0; i < list.size(); i++) {
            try { out.add(net.minecraft.nbt.NbtUtils.loadUUID(list.get(i))); } catch (Exception ignored) { }
        }
        return out;
    }

    /** Планшет в руке игрока (главная рука приоритетнее), или пустой стек. */
    public static ItemStack findHeld(Player player) {
        if (player == null) return ItemStack.EMPTY;
        if (player.getMainHandItem().getItem() instanceof PigControlTabletItem) return player.getMainHandItem();
        if (player.getOffhandItem().getItem() instanceof PigControlTabletItem) return player.getOffhandItem();
        return ItemStack.EMPTY;
    }

    @Override
    public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            openScreen(stack);
        }
        return net.minecraft.world.InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    private static void openScreen(ItemStack stack) {
        // Отдельный метод: так класс предмета не тянет клиентские классы при загрузке на сервере.
        net.minecraft.client.Minecraft.getInstance().setScreen(new PigTabletScreen(stack));
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        // Shift + ПКМ по блоку — открыть планшет. Без Shift планшет НЕ открывается,
        // чтобы клики по блокам можно было использовать для настройки области/сундука.
        if (player.isShiftKeyDown()) return InteractionResult.PASS;

        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        UUID selected = getSelectedPig(stack);

        if (selected == null) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.pig_mod.tablet.select_first")
                        .withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        if (level.isClientSide) {
            // Клиент просто "съедает" клик — вся логика ниже серверная.
            return InteractionResult.SUCCESS;
        }

        TameablePigEntity pig = null;
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            net.minecraft.world.entity.Entity entity = serverLevel.getEntity(selected);
            if (entity instanceof TameablePigEntity candidate && candidate.isTame() && candidate.isOwnedBy(player)) {
                pig = candidate;
            }
        }
        if (pig == null) {
            player.displayClientMessage(Component.translatable("message.pig_mod.tablet.pig_not_found").withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof Container) {
            pig.setCourierChestPos(pos);
            player.displayClientMessage(Component.translatable("message.pig_mod.tablet.chest_bound", formatPos(pos), pig.getName().getString())
                    .withStyle(ChatFormatting.GREEN), true);
            return InteractionResult.CONSUME;
        }

        if (pig.getWorkAreaPoint1() == null || pig.getWorkAreaPoint2() != null) {
            pig.setWorkAreaPoint1(pos);
            pig.setWorkAreaPoint2(null);
            player.displayClientMessage(Component.translatable("message.pig_mod.tablet.area_point1", formatPos(pos))
                    .withStyle(ChatFormatting.AQUA), true);
        } else {
            pig.setWorkAreaPoint2(pos);
            BlockPos a = pig.getWorkAreaPoint1();
            int sizeX = Math.abs(a.getX() - pos.getX()) + 1;
            int sizeZ = Math.abs(a.getZ() - pos.getZ()) + 1;
            player.displayClientMessage(Component.translatable("message.pig_mod.tablet.area_set", sizeX, sizeZ)
                    .withStyle(ChatFormatting.AQUA), true);
        }
        return InteractionResult.CONSUME;
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    private static String key(UUID uuid) { return uuid.toString(); }

    public static void updateSnapshot(ItemStack stack, TameablePigEntity pig) {
        CompoundTag t = tag(stack);
        CompoundTag all = t.getCompound("PigSnapshots");
        CompoundTag snap = new CompoundTag();
        snap.putString("Name", pig.getName().getString());
        snap.putInt("Health", (int) pig.getHealth());
        snap.putInt("MaxHealth", (int) pig.getDisplayedMaxHealth());
        snap.putInt("Hunger", pig.getHunger());
        snap.putInt("MaxHunger", pig.getMaxHunger());
        snap.putInt("Level", pig.getPigLevel());
        snap.putInt("X", (int) pig.getX());
        snap.putInt("Y", (int) pig.getY());
        snap.putInt("Z", (int) pig.getZ());
        snap.putString("Command", pig.getCommand().getTranslationKey());
        all.put(key(pig.getUUID()), snap);
        t.put("PigSnapshots", all);
        setTag(stack, t);
    }

    public static CompoundTag getSnapshot(ItemStack stack, UUID uuid) {
        CompoundTag t = tag(stack);
        return t.getCompound("PigSnapshots").getCompound(key(uuid));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.pig_mod.tablet.open").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.pig_mod.tablet.select").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.pig_mod.tablet.chest").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.pig_mod.tablet.area").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.pig_mod.tablet.reset").withStyle(ChatFormatting.DARK_GRAY));
    }
}
