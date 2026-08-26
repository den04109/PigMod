package com.example.examplemod;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Регистрация всех 4 Tier'ов Генетического Экстрактора. Крафт каждого следующего Tier'а
 * использует блок предыдущего как ингредиент — см. data/pig_mod/recipe/genetic_extractor_tierN.json.
 */
public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(BuiltInRegistries.BLOCK, ExampleMod.MODID);

    public static final DeferredHolder<Block, GeneticExtractorBlock.Tier1> GENETIC_EXTRACTOR_TIER1 =
            BLOCKS.register("genetic_extractor_tier1", () -> new GeneticExtractorBlock.Tier1(
                    BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4.0F, 6.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()));

    public static final DeferredHolder<Block, GeneticExtractorBlock.Tier2> GENETIC_EXTRACTOR_TIER2 =
            BLOCKS.register("genetic_extractor_tier2", () -> new GeneticExtractorBlock.Tier2(
                    BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(5.0F, 8.0F).sound(SoundType.METAL).requiresCorrectToolForDrops()));

    public static final DeferredHolder<Block, GeneticExtractorBlock.Tier3> GENETIC_EXTRACTOR_TIER3 =
            BLOCKS.register("genetic_extractor_tier3", () -> new GeneticExtractorBlock.Tier3(
                    BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(6.0F, 1200.0F).sound(SoundType.AMETHYST).requiresCorrectToolForDrops()));

    public static final DeferredHolder<Block, GeneticExtractorBlock.Tier4> GENETIC_EXTRACTOR_TIER4 =
            BLOCKS.register("genetic_extractor_tier4", () -> new GeneticExtractorBlock.Tier4(
                    BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(8.0F, 1600.0F).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops()));

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}