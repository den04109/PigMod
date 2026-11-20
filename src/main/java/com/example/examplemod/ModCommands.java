package com.example.examplemod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import com.example.examplemod.mutation.MutationDefinition;
import com.example.examplemod.mutation.Mutations;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Админские/читерские команды мода.
 *
 * /pigmod givemutation <mutation> [targets] [count]
 *     Выдаёт пробирки с мутацией игроку/игрокам.
 *
 * /pigmod maxlevel <targets>
 *     Максимально прокачивает одну или несколько Боевых Свиней.
 *
 * Обе команды требуют permission level 2 (OP).
 */
public class ModCommands {

    private static final SimpleCommandExceptionType NOT_A_PIG =
            new SimpleCommandExceptionType(
                    Component.translatable("command.pig_mod.not_a_pig")
            );

    private static final SuggestionProvider<CommandSourceStack> MUTATION_SUGGESTIONS =
            (ctx, builder) ->
                    SharedSuggestionProvider.suggest(
                            Mutations.all().keySet(),
                            builder
                    );

    /**
     * Регистрация команд.
     */
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                Commands.literal("pigmod")
                        .requires(src -> src.hasPermission(2))

                        // =========================================================
                        // /pigmod givemutation <mutation> [targets] [count]
                        // =========================================================
                        .then(
                                Commands.literal("givemutation")
                                        .then(
                                                Commands.argument(
                                                                "mutation",
                                                                StringArgumentType.word()
                                                        )
                                                        .suggests(MUTATION_SUGGESTIONS)

                                                        // /pigmod givemutation <mutation>
                                                        // Выдать себе 1 пробирку.
                                                        .executes(ctx ->
                                                                giveMutationTube(
                                                                        ctx,
                                                                        1,
                                                                        List.of(
                                                                                ctx.getSource()
                                                                                        .getPlayerOrException()
                                                                        )
                                                                )
                                                        )

                                                        // /pigmod givemutation <mutation> <targets>
                                                        .then(
                                                                Commands.argument(
                                                                                "targets",
                                                                                EntityArgument.players()
                                                                        )
                                                                        .executes(ctx ->
                                                                                giveMutationTube(
                                                                                        ctx,
                                                                                        1,
                                                                                        EntityArgument.getPlayers(
                                                                                                ctx,
                                                                                                "targets"
                                                                                        )
                                                                                )
                                                                        )

                                                                        // /pigmod givemutation <mutation> <targets> <count>
                                                                        .then(
                                                                                Commands.argument(
                                                                                                "count",
                                                                                                IntegerArgumentType.integer(
                                                                                                        1,
                                                                                                        64
                                                                                                )
                                                                                        )
                                                                                        .executes(ctx ->
                                                                                                giveMutationTube(
                                                                                                        ctx,
                                                                                                        IntegerArgumentType.getInteger(
                                                                                                                ctx,
                                                                                                                "count"
                                                                                                        ),
                                                                                                        EntityArgument.getPlayers(
                                                                                                                ctx,
                                                                                                                "targets"
                                                                                                        )
                                                                                                )
                                                                                        )
                                                                        )
                                                        )
                                        )
                        )

                        // =========================================================
                        // /pigmod maxlevel <targets>
                        // =========================================================
                        //
                        // ВАЖНО:
                        // Здесь используется EntityArgument.entities(),
                        // а не EntityArgument.entity().
                        //
                        // Поэтому можно передать:
                        //
                        // @e[type=pig_mod:tameable_pig]
                        //
                        // и команда обработает ВСЕ найденные Боевые Свиньи.
                        //
                        .then(
                                Commands.literal("maxlevel")
                                        .then(
                                                Commands.argument(
                                                                "targets",
                                                                EntityArgument.entities()
                                                        )
                                                        .executes(ModCommands::maxLevelPigs)
                                        )
                        )
        );
    }

    /**
     * Выдача пробирок с мутацией.
     */
    private static int giveMutationTube(
            CommandContext<CommandSourceStack> ctx,
            int count,
            Collection<ServerPlayer> targets
    ) throws CommandSyntaxException {

        String mutationId = StringArgumentType.getString(
                ctx,
                "mutation"
        );

        MutationDefinition def = Mutations.get(mutationId);

        // Проверяем существование мутации.
        if (def == null) {
            ctx.getSource().sendFailure(
                    Component.translatable(
                            "command.pig_mod.unknown_mutation",
                            mutationId,
                            String.join(
                                    ", ",
                                    Mutations.all().keySet()
                            )
                    )
            );

            return 0;
        }

        // Выдаём пробирки каждому игроку.
        for (ServerPlayer target : targets) {

            for (int i = 0; i < count; i++) {

                ItemStack stack =
                        DnaTubeItem.createFilled(mutationId);

                // Пытаемся положить в инвентарь.
                // Если места нет — выбрасываем предмет на землю.
                if (!target.getInventory().add(stack)) {
                    target.drop(stack, false);
                }
            }

            target.sendSystemMessage(
                    Component.translatable(
                            "command.pig_mod.received_tubes",
                            count,
                            Component.translatable(def.getDisplayNameKey())
                    )
            );
        }

        int targetCount = targets.size();

        ctx.getSource().sendSuccess(
                () -> Component.translatable(
                        "command.pig_mod.gave_tubes",
                        count,
                        mutationId,
                        targetCount
                ),
                true
        );

        return targetCount;
    }

    /**
     * Максимально прокачивает ВСЕ выбранные Боевые Свиньи.
     *
     * Например:
     *
     * /pigmod maxlevel @e[type=pig_mod:tameable_pig]
     *
     * прокачает всех Боевых Свиней, найденных селектором.
     */
    private static int maxLevelPigs(
            CommandContext<CommandSourceStack> ctx
    ) throws CommandSyntaxException {

        Collection<? extends Entity> entities =
                EntityArgument.getEntities(ctx, "targets");

        int upgradedCount = 0;

        for (Entity entity : entities) {

            // Если это не наша свинья — пропускаем.
            if (!(entity instanceof TameablePigEntity pig)) {
                continue;
            }

            maxLevelSinglePig(pig);
            upgradedCount++;
        }

        // Если среди выбранных сущностей вообще не было
        // Боевых Свиней.
        if (upgradedCount == 0) {
            throw NOT_A_PIG.create();
        }

        int finalCount = upgradedCount;

        ctx.getSource().sendSuccess(
                () -> Component.translatable(
                        "command.pig_mod.maxlevel",
                        finalCount,
                        TameablePigEntity.MAX_LEVEL
                ),
                true
        );

        return upgradedCount;
    }

    /**
     * Максимально прокачивает ОДНУ свинью.
     *
     * Вынесено в отдельный метод, чтобы maxLevelPigs()
     * мог применить одинаковую логику к каждой найденной свинье.
     */
    private static void maxLevelSinglePig(
            TameablePigEntity pig
    ) {

        // =========================================================
        // 1. Ставим максимальный уровень свиньи.
        // =========================================================

        pig.setPigLevel(
                TameablePigEntity.MAX_LEVEL
        );

        // =========================================================
        // 2. Получаем все уже введённые мутации.
        // =========================================================

        Map<String, Integer> mutations =
                pig.getMutations();

        // =========================================================
        // 3. Считаем, сколько очков развития понадобится,
        //    чтобы прокачать ВСЕ существующие мутации
        //    до максимального уровня.
        // =========================================================

        int totalCost = 0;

        for (Map.Entry<String, Integer> entry :
                mutations.entrySet()) {

            String mutationId = entry.getKey();
            int currentLevel = entry.getValue();

            MutationDefinition def =
                    Mutations.get(mutationId);

            // Если мутация больше не существует
            // в реестре — просто пропускаем её.
            if (def == null) {
                continue;
            }

            // Считаем стоимость каждого следующего уровня.
            for (
                    int level = currentLevel;
                    level < def.getMaxLevel();
                    level++
            ) {
                totalCost += def.getUpgradeCost(level);
            }
        }

        // =========================================================
        // 4. Выдаём ровно необходимое количество очков.
        // =========================================================

        pig.setSkillPoints(totalCost);

        // =========================================================
        // 5. Прокачиваем каждую существующую мутацию
        //    до максимального уровня.
        // =========================================================

        for (String mutationId : mutations.keySet()) {

            while (
                    pig.upgradeMutation(mutationId)
            ) {
                // Продолжаем, пока мутация может повышаться.
            }
        }
    }
}