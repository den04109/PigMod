package com.example.examplemod;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class PigMenu extends AbstractContainerMenu {

    // ==== Координаты слотов ====
    // ВАЖНО: эти значения должны 1-в-1 совпадать с теми, что рисует PigScreen,
    // иначе слоты снова "поплывут" и предметы будут вставляться мимо рамок.
    // Слоты улучшений свиньи убраны — она в них не нуждается, поэтому инвентарь
    // игрока и хотбар подняты выше, чтобы под старым местом слотов не было пустоты.
    //
    // Окно расширено (216 -> 260) и стало чуть выше, поэтому инвентарь центрирован
    // по новой ширине: 9 слотов * 18px = 162px, (260 - 162) / 2 = 49.

    // Инвентарь игрока (3 ряда по 9)
    private static final int INV_X = 49;
    private static final int INV_Y = 126;
    private static final int SLOT_SIZE = 18;

    // Хотбар
    private static final int HOTBAR_X = 49;
    private static final int HOTBAR_Y = 188;

    private final TameablePigEntity pig;
    private final Container pigInventory;

    public PigMenu(int containerId, Inventory playerInventory, int entityId) {
        this(containerId, playerInventory, getPigFromId(playerInventory, entityId));
    }

    public PigMenu(int containerId, Inventory playerInventory, TameablePigEntity pig) {
        super(ModMenuTypes.PIG_MENU.get(), containerId);
        this.pig = pig;
        this.pigInventory = pig != null ? pig.getInventory() : new SimpleContainer(4);

        if (this.pig != null) {
            this.pigInventory.startOpen(playerInventory.player);
        }

        // Слоты улучшений свиньи убраны из UI — они ей не нужны.
        // pigInventory при этом оставлен как поле на будущее, просто больше не привязан к видимым слотам.

        // Инвентарь игрока (3 ряда по 9 слотов)
        for (int l = 0; l < 3; ++l) {
            for (int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(playerInventory, j + l * 9 + 9, INV_X + j * SLOT_SIZE, INV_Y + l * SLOT_SIZE));
            }
        }

        // Горячая панель игрока (9 слотов)
        for (int k = 0; k < 9; ++k) {
            this.addSlot(new Slot(playerInventory, k, HOTBAR_X + k * SLOT_SIZE, HOTBAR_Y));
        }
    }

    private static TameablePigEntity getPigFromId(Inventory playerInventory, int entityId) {
        if (playerInventory.player.level().getEntity(entityId) instanceof TameablePigEntity pig) {
            return pig;
        }
        return null;
    }

    public TameablePigEntity getPig() {
        return this.pig;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.pig != null && this.pig.isAlive() && this.pig.distanceTo(player) < 8.0F;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // Слотов свиньи в UI больше нет, поэтому здесь остался только обычный инвентарь игрока:
        // 27 слотов основного инвентаря (0-26) и 9 слотов хотбара (27-35). Shift-клик просто
        // перекидывает предмет между этими двумя группами, как в стандартном инвентаре.
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();

            int inventoryStart = 0;
            int inventoryEnd = 27;
            int hotbarEnd = 36;

            if (index < inventoryEnd) {
                if (!this.moveItemStackTo(itemstack1, inventoryEnd, hotbarEnd, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(itemstack1, inventoryStart, inventoryEnd, false)) {
                return ItemStack.EMPTY;
            }

            if (itemstack1.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return itemstack;
    }
}