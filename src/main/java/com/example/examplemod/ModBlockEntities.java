package com.example.examplemod;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ExampleMod.MODID);

    // Один тип блок-сущности на все 4 Tier'а Экстрактора — сам блок хранит tier,
    // блок-сущность читает его при создании (см. GeneticExtractorBlockEntity).
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GeneticExtractorBlockEntity>> GENETIC_EXTRACTOR =
            BLOCK_ENTITIES.register("genetic_extractor", () -> BlockEntityType.Builder.of(
                    GeneticExtractorBlockEntity::new,
                    ModBlocks.GENETIC_EXTRACTOR_TIER1.get(),
                    ModBlocks.GENETIC_EXTRACTOR_TIER2.get(),
                    ModBlocks.GENETIC_EXTRACTOR_TIER3.get(),
                    ModBlocks.GENETIC_EXTRACTOR_TIER4.get()
            ).build(null));

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}