package com.example.examplemod;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Генетический Экстрактор — блок, синтезирующий Пробирки с Мутацией. Общая логика (открытие
 * GUI, тикер, модель) живёт здесь; конкретные Tier'ы — крошечные подклассы ниже, т.к. каждому
 * нужен свой статический {@code codec()} (так требует Block в 1.21.1), а сам tier — константа.
 */
public abstract class GeneticExtractorBlock extends BaseEntityBlock {

    protected GeneticExtractorBlock(Properties properties) {
        super(properties);
    }

    /** Tier этого конкретного блока (1-4) — определяет число слотов ДНК-носителей и доступные мутации. */
    public abstract int getTier();

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL; // обычная модель блока, TESR не нужен
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GeneticExtractorBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.GENETIC_EXTRACTOR.get(), GeneticExtractorBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof GeneticExtractorBlockEntity extractor) {
            if (!level.isClientSide) {
                player.openMenu(extractor, buf -> buf.writeBlockPos(pos));
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    // ==== Конкретные Tier'ы ====

    public static class Tier1 extends GeneticExtractorBlock {
        public static final com.mojang.serialization.MapCodec<Tier1> CODEC = simpleCodec(Tier1::new);
        public Tier1(Properties properties) { super(properties); }
        @Override protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
        @Override public int getTier() { return 1; }
    }

    public static class Tier2 extends GeneticExtractorBlock {
        public static final com.mojang.serialization.MapCodec<Tier2> CODEC = simpleCodec(Tier2::new);
        public Tier2(Properties properties) { super(properties); }
        @Override protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
        @Override public int getTier() { return 2; }
    }

    public static class Tier3 extends GeneticExtractorBlock {
        public static final com.mojang.serialization.MapCodec<Tier3> CODEC = simpleCodec(Tier3::new);
        public Tier3(Properties properties) { super(properties); }
        @Override protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
        @Override public int getTier() { return 3; }
    }

    public static class Tier4 extends GeneticExtractorBlock {
        public static final com.mojang.serialization.MapCodec<Tier4> CODEC = simpleCodec(Tier4::new);
        public Tier4(Properties properties) { super(properties); }
        @Override protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
        @Override public int getTier() { return 4; }
    }
}