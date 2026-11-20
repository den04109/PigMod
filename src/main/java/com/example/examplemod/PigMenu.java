package com.example.examplemod;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class PigMenu extends AbstractContainerMenu {

    /** Координаты сетки инвентаря свиньи относительно leftPos/topPos экрана. */
    public static final int INV_X = 69;
    public static final int INV_Y = 118;
    public static final int SLOT_SIZE = 18;
    public static final int INV_COLS = 9;
    public static final int INV_ROWS = 3;
    public static final int INV_SLOTS = INV_COLS * INV_ROWS;

    private final TameablePigEntity pig;
    private final Container pigInventory;

    public PigMenu(int containerId, Inventory playerInventory, int entityId) {
        this(containerId, playerInventory, getPigFromId(playerInventory, entityId));
    }

    public PigMenu(int containerId, Inventory playerInventory, TameablePigEntity pig) {
        super(ModMenuTypes.PIG_MENU.get(), containerId);
        this.pig = pig;
        this.pigInventory = pig != null ? pig.getInventory() : new SimpleContainer(INV_SLOTS);

        if (this.pig != null) {
            this.pigInventory.startOpen(playerInventory.player);
        }

        // Только инвентарь свиньи. Инвентарь игрока намеренно не добавляется в это окно.
        // Слоты всегда остаются на своих координатах; PigScreen сама решает, когда их рисовать.
        for (int row = 0; row < INV_ROWS; row++) {
            for (int col = 0; col < INV_COLS; col++) {
                this.addSlot(new Slot(
                        this.pigInventory,
                        col + row * INV_COLS,
                        INV_X + col * SLOT_SIZE,
                        INV_Y + row * SLOT_SIZE
                ));
            }
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

    public Container getPigInventory() {
        return this.pigInventory;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.pig != null && this.pig.isAlive() && this.pig.distanceTo(player) < 8.0F;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.pigInventory.stopOpen(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // Все 27 слотов принадлежат свинье, поэтому shift-клик не должен пытаться
        // переместить предмет в несуществующий инвентарь игрока внутри этого меню.
        return ItemStack.EMPTY;
    }
}
