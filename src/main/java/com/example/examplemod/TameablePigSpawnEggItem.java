package com.example.examplemod;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;

import java.util.List;
import java.util.function.Supplier;

/**
 * Яйцо призыва боевой свиньи.
 *
 * Ванильный SpawnEggItem просто создаёт сущность — поэтому раньше свинья из яйца выходила
 * ДИКОЙ: она не слушалась команд, её нельзя было выбрать планшетом и открыть её GUI.
 * Здесь спавн переопределён: свинья сразу приручена, её хозяин — тот, кто использовал яйцо,
 * ей выдаётся пол, режим "Следовать" и защита от деспавна.
 */
public class TameablePigSpawnEggItem extends DeferredSpawnEggItem {

    public TameablePigSpawnEggItem(Supplier<? extends EntityType<? extends Mob>> type,
                                   int backgroundColor, int highlightColor, Properties properties) {
        super(type, backgroundColor, highlightColor, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            // На клиенте просто подтверждаем действие — вся логика спавна серверная.
            return InteractionResult.SUCCESS;
        }

        BlockPos clicked = context.getClickedPos();
        Direction face = context.getClickedFace();
        BlockState state = level.getBlockState(clicked);

        // Если по кликнутому блоку можно пройти (трава, цветы, вода) — спавним прямо в нём,
        // иначе на соседней клетке со стороны клика. Это то же правило, что и у ванильного яйца.
        BlockPos spawnPos = state.getCollisionShape(level, clicked).isEmpty()
                ? clicked
                : clicked.relative(face);

        TameablePigEntity pig = ModEntities.TAMEABLE_PIG.get().create(serverLevel);
        if (pig == null) {
            return InteractionResult.FAIL;
        }

        pig.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D,
                serverLevel.random.nextFloat() * 360.0F, 0.0F);
        pig.randomizeGenderIfNeeded();

        Player player = context.getPlayer();
        if (player != null) {
            pig.tame(player);                 // хозяин = тот, кто использовал яйцо
            pig.setCommand(PigCommand.FOLLOW);
            pig.setPersistenceRequired();     // прирученная свинья не должна деспавниться
            pig.setCustomName(null);
        }

        serverLevel.addFreshEntity(pig);
        serverLevel.broadcastEntityEvent(pig, (byte) 7); // сердечки приручения

        if (player == null || !player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // Ванильная версия спавнит моба при клике по воде. Для прирученной свиньи это
        // бессмысленно (и обошло бы приручение выше), поэтому просто ничего не делаем.
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.pig_mod.spawn_egg").withStyle(ChatFormatting.GRAY));
    }
}
