package com.example.examplemod.ai;

import com.example.examplemod.TameablePigEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Общая логика "рабочих" мутаций-профессий (Лесоруб/Фермер/Шахтёр): ищет подходящие блоки в
 * радиусе (растёт с уровнем мутации), идёт к ближайшему и добывает его — не мгновенно, а за
 * несколько тиков (тоже зависит от уровня), с трещинами и звуком поломки блока как у игрока.
 *
 * Радиус поиска сейчас ограничен технически (полностью безлимитный перебор всего мира на каждой
 * проверке невозможен по производительности) — растёт с уровнем до потолка в 24 блока. Точное
 * ограничение "территории" посохом — отдельная будущая задача, как и обсуждали.
 *
 * ВАЖНО (упрощение): свинья "держит инструмент в зубах" — визуальная деталь (нужен рендер-слой
 * модели), которой пока нет; виртуальный инструмент используется только для расчёта дропа.
 */
public class ResourceGatherGoal extends Goal {

    private static final int RECHECK_INTERVAL = 20; // раз в секунду, пока нет активной цели
    private static final int STUCK_THRESHOLD = 30;  // тиков без продвижения, прежде чем Шахтёр начнёт копать препятствие
    private static final int MAX_DIG_ATTEMPTS = 24;  // предохранитель от бесконечного долбления в тупик
    private static final int MAX_TREE_BLOCKS = 64;   // предохранитель от гигантских деревьев (джунгли)

    private record Target(BlockPos pos, boolean isObstacle) {
    }

    private final TameablePigEntity pig;
    private final String mutationId;
    private final Predicate<BlockState> matcher;
    private final ItemStack virtualTool;
    private final boolean replant;       // Фермер: пересаживать вместо полного слома
    private final boolean wholeTree;     // Лесоруб: собрать все связанные брёвна целиком
    private final boolean canDigThrough; // Шахтёр: прорывать путь через камень к руде
    private final double speedMultiplier; // >1 = медленнее (у Лесоруба — плата за то, что рубит всё дерево)

    private int cooldown;
    private final Deque<Target> queue = new ArrayDeque<>();
    private BlockPos finalTargetPos;

    private BlockPos miningPos;
    private int miningProgress;
    private int miningTimeNeeded;
    private boolean miningIsObstacle;

    private Vec3 lastPos = Vec3.ZERO;
    private int stuckTicks;
    private int digAttempts;

    public ResourceGatherGoal(TameablePigEntity pig, String mutationId, Predicate<BlockState> matcher,
                               ItemStack virtualTool, boolean replant, boolean wholeTree,
                               boolean canDigThrough, double speedMultiplier) {
        this.pig = pig;
        this.mutationId = mutationId;
        this.matcher = matcher;
        this.virtualTool = virtualTool;
        this.replant = replant;
        this.wholeTree = wholeTree;
        this.canDigThrough = canDigThrough;
        this.speedMultiplier = speedMultiplier;
        this.setFlags(java.util.EnumSet.of(Goal.Flag.MOVE));
    }

    // ==== Масштабирование по уровню мутации ====

    private int getSearchRadius() {
        int level = pig.getMutationLevel(mutationId);
        return Math.min(24, 4 + level * 2);
    }

    private int getMiningTicks() {
        int level = pig.getMutationLevel(mutationId);
        int base = Math.max(10, 60 - level * 5); // Lvl1: 55 тиков (~2.75с) -> Lvl10: 10 тиков (~0.5с)
        return Math.max(5, (int) Math.round(base * speedMultiplier));
    }

    // ==== Goal ====

    @Override
    public boolean canUse() {
        if (!pig.hasMutation(mutationId) || !pig.isTame() || pig.isOrderedToSit()) {
            return false;
        }
        if (!queue.isEmpty() || miningPos != null) {
            return true; // не доделали прошлую цель (например, ещё рубим дерево)
        }
        if (cooldown-- > 0) {
            return false;
        }
        cooldown = RECHECK_INTERVAL;

        BlockPos found = findNearestTarget();
        if (found == null) {
            return false;
        }
        this.finalTargetPos = found;
        queue.clear();
        if (wholeTree) {
            for (BlockPos p : floodFillConnected(found)) {
                queue.add(new Target(p, false));
            }
        } else {
            queue.add(new Target(found, false));
        }
        return !queue.isEmpty();
    }

    @Override
    public boolean canContinueToUse() {
        return !queue.isEmpty() || miningPos != null;
    }

    @Override
    public void start() {
        stuckTicks = 0;
        digAttempts = 0;
        lastPos = pig.position();
    }

    @Override
    public void stop() {
        clearMiningProgressOverlay();
        miningPos = null;
        queue.clear();
        pig.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (miningPos != null) {
            continueMining();
            return;
        }
        if (queue.isEmpty()) {
            return;
        }

        Target next = queue.peek();
        if (!isStillValid(next)) {
            queue.poll();
            return;
        }

        if (pig.blockPosition().closerThan(next.pos(), 2.2)) {
            startMining(queue.poll());
            stuckTicks = 0;
            return;
        }

        if (pig.getNavigation().isDone()) {
            pig.getNavigation().moveTo(next.pos().getX() + 0.5, next.pos().getY(), next.pos().getZ() + 0.5, 1.0D);
        }

        // Шахтёр: если долго не можем приблизиться — считаем, что упёрлись в камень, и роем сквозь него
        if (canDigThrough) {
            if (pig.position().distanceToSqr(lastPos) < 0.02) {
                stuckTicks++;
            } else {
                stuckTicks = 0;
                lastPos = pig.position();
            }
            if (stuckTicks > STUCK_THRESHOLD && digAttempts < MAX_DIG_ATTEMPTS) {
                BlockPos obstacle = findObstacleTowards(pig.blockPosition(), next.pos());
                stuckTicks = 0;
                if (obstacle != null) {
                    digAttempts++;
                    queue.addFirst(new Target(obstacle, true));
                } else {
                    // Дорогу не пробить (например, упёрлись в бедрок) — сдаёмся с этой целью
                    queue.clear();
                }
            }
        }
    }

    private boolean isStillValid(Target target) {
        BlockState state = pig.level().getBlockState(target.pos());
        return target.isObstacle() ? isDiggableObstacle(state) : matcher.test(state);
    }

    // ==== Добыча блока (с анимацией трещин и звуком, как у игрока) ====

    private void startMining(Target target) {
        BlockState state = pig.level().getBlockState(target.pos());
        this.miningPos = target.pos();
        this.miningIsObstacle = target.isObstacle();
        this.miningProgress = 0;
        this.miningTimeNeeded = getMiningTicks();
        if (state.getDestroySpeed(pig.level(), target.pos()) < 0) {
            // небьющийся блок (бедрок и т.п.) затесался в список — пропускаем без добычи
            this.miningPos = null;
        }
    }

    private void continueMining() {
        if (!(pig.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        BlockState state = serverLevel.getBlockState(miningPos);
        if (state.isAir()) {
            clearMiningProgressOverlay();
            miningPos = null; // блок уже кто-то сломал (игрок, взрыв и т.п.)
            return;
        }

        miningProgress++;
        int stage = Mth.clamp((int) ((float) miningProgress / miningTimeNeeded * 10.0F), 0, 9);
        serverLevel.destroyBlockProgress(pig.getId(), miningPos, stage);

        if (miningProgress >= miningTimeNeeded) {
            finishMining(serverLevel, state);
        }
    }

    private void finishMining(ServerLevel serverLevel, BlockState state) {
        clearMiningProgressOverlay();
        BlockPos pos = this.miningPos;
        boolean isRealTarget = !miningIsObstacle;

        ItemStack tool = isRealTarget ? virtualTool : new ItemStack(Items.DIAMOND_PICKAXE);
        BlockEntity blockEntity = serverLevel.getBlockEntity(pos);
        List<ItemStack> drops = Block.getDrops(state, serverLevel, pos, blockEntity, pig, tool);

        if (isRealTarget && replant && state.getBlock() instanceof CropBlock crop) {
            serverLevel.setBlock(pos, state.setValue(CropBlock.AGE, 0), 3);
            serverLevel.levelEvent(2001, pos, Block.getId(state)); // тот же звук+частицы, что и при обычной добыче
        } else {
            // destroyBlock сам проигрывает звук/частицы разрушения (levelEvent 2001) — то же самое,
            // что видит игрок, когда ломает блок — и сам убирает блок из мира.
            serverLevel.destroyBlock(pos, false, pig, 512);
        }

        for (ItemStack drop : drops) {
            pig.storeOrDropItem(drop);
        }
        if (isRealTarget) {
            pig.addPigExp(2);
        }

        this.miningPos = null;
        this.miningProgress = 0;
    }

    private void clearMiningProgressOverlay() {
        if (miningPos != null && pig.level() instanceof ServerLevel serverLevel) {
            serverLevel.destroyBlockProgress(pig.getId(), miningPos, -1);
        }
    }

    // ==== Поиск препятствия для Шахтёра (прямая линия от свиньи к цели) ====

    private static boolean isDiggableObstacle(BlockState state) {
        return !state.isAir() && state.is(BlockTags.MINEABLE_WITH_PICKAXE);
    }

    private BlockPos findObstacleTowards(BlockPos from, BlockPos to) {
        Vec3 start = Vec3.atCenterOf(from);
        Vec3 end = Vec3.atCenterOf(to);
        Vec3 diff = end.subtract(start);
        double dist = diff.length();
        if (dist < 0.75) {
            return null;
        }
        Vec3 dir = diff.scale(1.0 / dist);
        BlockPos last = null;
        for (double t = 0.75; t < dist; t += 0.75) {
            BlockPos p = BlockPos.containing(start.add(dir.scale(t)));
            if (p.equals(from) || p.equals(to) || p.equals(last)) {
                continue;
            }
            last = p;
            BlockState state = pig.level().getBlockState(p);
            if (state.isAir() || matcher.test(state)) {
                continue; // воздух или сама цель — не препятствие
            }
            if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
                return p; // нашли, что копать
            }
            if (!state.getCollisionShape(pig.level(), p).isEmpty()) {
                return null; // упёрлись в нечто не копаемое (бедрок и т.п.) — дальше пути нет
            }
        }
        return null;
    }

    // ==== Поиск ближайшего подходящего блока — послойно по "оболочкам" куба, а не сплошным
    // перебором всего объёма: как только нашли совпадение, дальше не сканируем. Так поиск в
    // среднем намного дешевле, чем O(radius^3), и большой радиус не убивает производительность. ====

    private BlockPos findNearestTarget() {
        BlockPos origin = pig.blockPosition();
        if (matcher.test(pig.level().getBlockState(origin))) {
            return origin;
        }
        int maxRadius = getSearchRadius();
        for (int r = 1; r <= maxRadius; r++) {
            BlockPos found = scanShell(origin, r);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private BlockPos scanShell(BlockPos origin, int r) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                BlockPos top = origin.offset(dx, r, dz);
                if (matcher.test(pig.level().getBlockState(top))) {
                    return top;
                }
                BlockPos bottom = origin.offset(dx, -r, dz);
                if (matcher.test(pig.level().getBlockState(bottom))) {
                    return bottom;
                }
            }
        }
        for (int dy = -r + 1; dy <= r - 1; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                BlockPos north = origin.offset(dx, dy, -r);
                if (matcher.test(pig.level().getBlockState(north))) {
                    return north;
                }
                BlockPos south = origin.offset(dx, dy, r);
                if (matcher.test(pig.level().getBlockState(south))) {
                    return south;
                }
            }
            for (int dz = -r + 1; dz <= r - 1; dz++) {
                BlockPos west = origin.offset(-r, dy, dz);
                if (matcher.test(pig.level().getBlockState(west))) {
                    return west;
                }
                BlockPos east = origin.offset(r, dy, dz);
                if (matcher.test(pig.level().getBlockState(east))) {
                    return east;
                }
            }
        }
        return null;
    }

    // ==== Лесоруб: связанные брёвна (BFS, 6-связность), чтобы срубить дерево целиком ====

    private List<BlockPos> floodFillConnected(BlockPos start) {
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> frontier = new ArrayDeque<>();
        List<BlockPos> result = new java.util.ArrayList<>();
        frontier.add(start);
        visited.add(start);
        while (!frontier.isEmpty() && result.size() < MAX_TREE_BLOCKS) {
            BlockPos p = frontier.poll();
            result.add(p);
            for (Direction dir : Direction.values()) {
                BlockPos n = p.relative(dir);
                if (!visited.contains(n) && matcher.test(pig.level().getBlockState(n))) {
                    visited.add(n);
                    frontier.add(n);
                }
            }
        }
        return result;
    }
}