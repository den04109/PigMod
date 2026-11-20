package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import static com.example.examplemod.GeneticExtractorLayout.*;

public class GeneticExtractorMenu extends AbstractContainerMenu {

    private final GeneticExtractorBlockEntity blockEntity;
    private final int tier;
    private final Container container;

    public GeneticExtractorMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, resolveBlockEntity(playerInventory, buf.readBlockPos()));
    }

    public GeneticExtractorMenu(int containerId, Inventory playerInventory, GeneticExtractorBlockEntity blockEntity) {
        super(ModMenuTypes.GENETIC_EXTRACTOR_MENU.get(), containerId);
        this.blockEntity = blockEntity;
        this.tier = blockEntity != null ? blockEntity.getTier() : 1;
        this.container = blockEntity != null ? blockEntity : new SimpleContainer(tier + 3);

        // Слот основы — принимает только пустую пробирку
        this.addSlot(new Slot(container, 0, baseSlotX(), baseSlotY()) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.EMPTY_DNA_TUBE.get());
            }
        });

        // Слоты ДНК-носителей — сколько их, зависит от Tier'а блока
        for (int i = 0; i < tier; i++) {
            this.addSlot(new Slot(container, 1 + i, carrierSlotX(i), carrierSlotY()));
        }

        // Слот катализатора — только Редстоун / Огненный порошок / Хорус / Осколок эха (см. isCatalyst)
        this.addSlot(new Slot(container, tier + 1, catalystSlotX(), catalystSlotY()) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return isCatalyst(stack);
            }
        });

        // Выходной слот — только забрать, положить в него руками нельзя
        this.addSlot(new Slot(container, tier + 2, outputSlotX(tier), outputSlotY()) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });

        // Инвентарь игрока
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, invX(tier) + col * SLOT, invY() + row * SLOT));
            }
        }
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, invX(tier) + col * SLOT, hotbarY()));
        }

        if (blockEntity != null) {
            this.addDataSlots(blockEntity.getContainerData());
        }
    }

    /** Катализаторы Tier'ов: Редстоун (T1), Огненный порошок (T2), Хорус (T3), Осколок эха (T4). */
    private static boolean isCatalyst(ItemStack stack) {
        return stack.is(Items.REDSTONE)
                || stack.is(Items.BLAZE_POWDER)
                || stack.is(Items.CHORUS_FRUIT)
                || stack.is(Items.ECHO_SHARD);
    }

    private static GeneticExtractorBlockEntity resolveBlockEntity(Inventory playerInventory, BlockPos pos) {
        if (playerInventory.player.level().getBlockEntity(pos) instanceof GeneticExtractorBlockEntity be) {
            return be;
        }
        return null;
    }

    public int getTier() {
        return tier;
    }

    public GeneticExtractorBlockEntity getBlockEntity() {
        return blockEntity;
    }

    /** Прогресс текущего синтеза в тиках (0, если блок-сущность недоступна) — синхронизируется через ContainerData. */
    public int getProgress() {
        return blockEntity != null ? blockEntity.getContainerData().get(0) : 0;
    }

    /** Сколько тиков нужно на полный синтез. */
    public int getMaxProgress() {
        return blockEntity != null ? Math.max(1, blockEntity.getContainerData().get(1)) : 1;
    }

    @Override
    public boolean stillValid(Player player) {
        return blockEntity == null || container.stillValid(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return result;
        }

        ItemStack stack = slot.getItem();
        result = stack.copy();

        int blockSlots = tier + 3;               // 0 .. blockSlots-1 — слоты экстрактора
        int invStart = blockSlots;
        int invEnd = blockSlots + 27;
        int hotbarEnd = invEnd + 9;

        if (index < blockSlots) {
            // Из экстрактора — сразу в инвентарь игрока
            if (!this.moveItemStackTo(stack, invStart, hotbarEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Из инвентаря игрока — пробуем распределить по подходящему слоту экстрактора
            boolean placed;
            if (stack.is(ModItems.EMPTY_DNA_TUBE.get())) {
                placed = this.moveItemStackTo(stack, 0, 1, false);
            } else if (isCatalyst(stack)) {
                placed = this.moveItemStackTo(stack, tier + 1, tier + 2, false);
                if (!placed) {
                    // Слот катализатора занят — предмет может быть и ингредиентом (Осколок эха в T4)
                    placed = this.moveItemStackTo(stack, 1, tier + 1, false);
                }
            } else {
                // По умолчанию считаем, что это потенциальный ДНК-носитель
                placed = this.moveItemStackTo(stack, 1, tier + 1, false);
            }

            if (!placed) {
                // Не влезло в экстрактор — как обычно, между инвентарём и хотбаром
                if (index < invEnd) {
                    if (!this.moveItemStackTo(stack, invEnd, hotbarEnd, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!this.moveItemStackTo(stack, invStart, invEnd, false)) {
                    return ItemStack.EMPTY;
                }
            }
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return result;
    }
}