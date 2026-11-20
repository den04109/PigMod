package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.example.examplemod.mutation.MutationDefinition;
import com.example.examplemod.mutation.Mutations;

import java.util.ArrayList;
import java.util.List;

/**
 * Блок-сущность Генетического Экстрактора. Слоты (индексы фиксированы, tier = getTier()):
 *   0            — основа (пустая пробирка)
 *   1..tier      — ДНК-носители (столько слотов, каков tier блока)
 *   tier+1       — катализатор (редстоун / огненный порошок / незеритовая пыль)
 *   tier+2       — выход (готовая Пробирка с Мутацией)
 *
 * Подходящая мутация ищется прямо в реестре {@link Mutations} (а не через datapack-рецепты) —
 * т.к. сами мутации и так описываются в Java (эффекты — это код, не JSON), логичнее держать
 * состав их синтеза там же, одним куском, а не дублировать данные в двух местах.
 */
public class GeneticExtractorBlockEntity extends BaseContainerBlockEntity {

    /** Сколько тиков идёт синтез одной мутации, независимо от Tier'а (можно будет разнести по Tier'ам позже). */
    private static final int PROCESS_TIME = 200;

    private final int tier;
    private NonNullList<ItemStack> items;
    private int progress;

    private final ContainerData containerData = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> PROCESS_TIME;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == 0) {
                progress = value;
            }
        }

        @Override
        public int getCount() {
            return 2;
        }
    };

    public GeneticExtractorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GENETIC_EXTRACTOR.get(), pos, state);
        this.tier = state.getBlock() instanceof GeneticExtractorBlock block ? block.getTier() : 1;
        this.items = NonNullList.withSize(this.tier + 3, ItemStack.EMPTY);
    }

    public int getTier() {
        return tier;
    }

    public int getBaseSlot() {
        return 0;
    }

    public int getCarrierSlotStart() {
        return 1;
    }

    public int getCatalystSlot() {
        return tier + 1;
    }

    public int getOutputSlot() {
        return tier + 2;
    }

    public ContainerData getContainerData() {
        return containerData;
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.pig_mod.genetic_extractor_tier" + tier);
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new GeneticExtractorMenu(containerId, inventory, this);
    }

    // --- ПОИСК И ПРИМЕНЕНИЕ РЕЦЕПТА ---

    /** Мутация, чей состав сейчас полностью выложен в слоты (или null, если ничего не подходит). */
    private MutationDefinition findMatchingMutation() {
        ItemStack base = items.get(getBaseSlot());
        if (base.isEmpty() || !base.is(ModItems.EMPTY_DNA_TUBE.get())) {
            return null;
        }
        ItemStack catalyst = items.get(getCatalystSlot());
        if (catalyst.isEmpty()) {
            return null;
        }

        for (MutationDefinition definition : Mutations.all().values()) {
            if (definition.getExtractorTier() > this.tier) {
                continue; // мутация сложнее, чем позволяет Tier этого блока
            }
            if (definition.getCatalystItem() == null || !catalyst.is(definition.getCatalystItem())) {
                continue;
            }
            if (definition.matchesCarriers(placedCarriers())) {
                return definition;
            }
        }
        return null;
    }

    /** Непустые стаки из слотов-носителей (сама проверка рецепта — в MutationDefinition.matchesCarriers). */
    private List<ItemStack> placedCarriers() {
        List<ItemStack> placed = new ArrayList<>();
        for (int i = 0; i < tier; i++) {
            ItemStack stack = items.get(getCarrierSlotStart() + i);
            if (!stack.isEmpty()) {
                placed.add(stack);
            }
        }
        return placed;
    }

    private boolean canInsertResult(MutationDefinition definition) {
        ItemStack output = items.get(getOutputSlot());
        if (output.isEmpty()) {
            return true;
        }
        ItemStack sample = DnaTubeItem.createFilled(definition.getId());
        return ItemStack.isSameItemSameComponents(output, sample) && output.getCount() < output.getMaxStackSize();
    }

    private void craft(MutationDefinition definition) {
        items.get(getBaseSlot()).shrink(1);

        // Рецепт уже подтверждён (matchesCarriers): каждый занятый слот-носитель ровно один раз
        // участвует в рецепте, поэтому просто списываем по одному предмету из каждого.
        for (int i = 0; i < tier; i++) {
            ItemStack stack = items.get(getCarrierSlotStart() + i);
            if (!stack.isEmpty()) {
                stack.shrink(1);
            }
        }

        items.get(getCatalystSlot()).shrink(1);

        ItemStack output = items.get(getOutputSlot());
        if (output.isEmpty()) {
            items.set(getOutputSlot(), DnaTubeItem.createFilled(definition.getId()));
        } else {
            output.grow(1);
        }
        setChanged();
    }

    public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, GeneticExtractorBlockEntity be) {
        MutationDefinition match = be.findMatchingMutation();
        boolean crafting = match != null && be.canInsertResult(match);

        if (crafting) {
            be.progress++;
            if (be.progress >= PROCESS_TIME) {
                be.craft(match);
                be.progress = 0;
            }
            be.setChanged();
        } else if (be.progress > 0) {
            // Рецепт разобрали/сломали — прогресс медленно откатывается, а не сбрасывается мгновенно
            be.progress = Math.max(0, be.progress - 2);
            be.setChanged();
        }

        // Свойство ACTIVE переключает модель на анимированную спираль ДНК (см. GeneticExtractorBlock)
        // — как lit у печи, меняем блокстейт только на самих переходах, а не каждый тик.
        if (state.getValue(GeneticExtractorBlock.ACTIVE) != crafting) {
            level.setBlock(pos, state.setValue(GeneticExtractorBlock.ACTIVE, crafting), 3);
        }
    }

    // --- NBT ---

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.items = NonNullList.withSize(this.tier + 3, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, this.items, registries);
        this.progress = tag.getInt("Progress");
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, this.items, registries);
        tag.putInt("Progress", this.progress);
    }
}