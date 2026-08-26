package com.example.examplemod.mutation;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.TameablePigEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Реестр всех мутаций мода. Вся остальная система (пробирки, инжектор, экстрактор,
 * прокачка за очки, вкладка 2 в GUI свиньи) уже рассчитана на работу с любым
 * количеством мутаций из этого реестра — сюда просто по мере готовности дописываются
 * новые (Tier 2/3/4 — следующим шагом, по одной "сложной" категории за раз).
 *
 * LinkedHashMap специально — чтобы порядок регистрации сохранялся и мутации
 * не "прыгали" местами в списке на Вкладке 2 GUI.
 */
public class Mutations {

    private static final Map<String, MutationDefinition> REGISTRY = new LinkedHashMap<>();

    public static MutationDefinition register(MutationDefinition definition) {
        if (REGISTRY.containsKey(definition.getId())) {
            throw new IllegalStateException("Мутация с id '" + definition.getId() + "' уже зарегистрирована");
        }
        REGISTRY.put(definition.getId(), definition);
        return definition;
    }

    public static MutationDefinition get(String id) {
        return REGISTRY.get(id);
    }

    public static boolean exists(String id) {
        return REGISTRY.containsKey(id);
    }

    /** Все зарегистрированные мутации, в порядке регистрации. Не изменяемо снаружи. */
    public static Map<String, MutationDefinition> all() {
        return Collections.unmodifiableMap(REGISTRY);
    }

    // ==================================================================================
    // ==== TIER 1 — Базовый Экстрактор. Простые ванильные ресурсы.                  ====
    // ==================================================================================

    private static ResourceLocation modLoc(String path) {
        return ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, path);
    }

    /** Здоровье — Lvl1: +4 HP ... Lvl9: +36 HP, Lvl10 (MAX): +50 HP (финальный скачок). */
    public static final MutationDefinition HEALTH_BOOST = register(new MutationDefinition(
            "health_boost", "mutation.pig_mod.health_boost",
            10, 1, 1,
            List.of(Items.GOLDEN_APPLE), Items.REDSTONE,
            (pig, level) -> pig.setAttributeBonus(Attributes.MAX_HEALTH, modLoc("mutation_health_boost"),
                    level >= 10 ? 50.0 : level * 4.0, AttributeModifier.Operation.ADD_VALUE),
            level -> "+" + (level >= 10 ? 50 : level * 4) + " HP"
    ));

    /**
     * Регенерация — постоянный пассивный реген, тикает в TameablePigEntity.customServerAiStep().
     * Эффекта-атрибута нет (effectApplier == null) — сама механика завязана на hasMutation()/уровень
     * прямо в коде свиньи, т.к. регенерация во времени не выражается через AttributeModifier.
     */
    public static final MutationDefinition REGENERATION = register(new MutationDefinition(
            "regeneration", "mutation.pig_mod.regeneration",
            5, 1, 1,
            List.of(Items.GOLDEN_CARROT), Items.REDSTONE,
            null,
            level -> "1 HP / " + (TameablePigEntity.regenIntervalTicks(level) / 20.0) + "с"
    ));

    /** Броня — плоский бонус к Attributes.ARMOR. */
    public static final MutationDefinition ARMOR = register(new MutationDefinition(
            "armor", "mutation.pig_mod.armor",
            10, 1, 1,
            List.of(Items.IRON_BLOCK), Items.REDSTONE,
            (pig, level) -> pig.setAttributeBonus(Attributes.ARMOR, modLoc("mutation_armor"),
                    level, AttributeModifier.Operation.ADD_VALUE),
            level -> "+" + level + " брони"
    ));

    /** Урон — плоский бонус к Attributes.ATTACK_DAMAGE (отдельно от бонуса за уровень свиньи). */
    public static final MutationDefinition DAMAGE = register(new MutationDefinition(
            "damage", "mutation.pig_mod.damage",
            10, 1, 1,
            List.of(Items.DIAMOND_SWORD), Items.REDSTONE,
            (pig, level) -> pig.setAttributeBonus(Attributes.ATTACK_DAMAGE, modLoc("mutation_damage"),
                    level, AttributeModifier.Operation.ADD_VALUE),
            level -> "+" + level + " урона"
    ));

    /**
     * Скорость — +5% к скорости передвижения за уровень (множитель, не плоский бонус).
     * ВАЖНО: носитель "Зелье стремительности II" сейчас проверяется только по предмету (любое
     * зелье minecraft:potion подойдёт, а не конкретно Стремительность II) — наша система ДНК-носителей
     * пока сравнивает только Item, а не содержимое зелья (PotionContents). Если нужна проверка именно
     * конкретного зелья — это отдельное расширение системы ингредиентов, скажи, и я его добавлю.
     */
    public static final MutationDefinition SPEED = register(new MutationDefinition(
            "speed", "mutation.pig_mod.speed",
            10, 1, 1,
            List.of(Items.POTION), Items.REDSTONE,
            (pig, level) -> pig.setAttributeBonus(Attributes.MOVEMENT_SPEED, modLoc("mutation_speed"),
                    level * 0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
            level -> "+" + (level * 5) + "% скорости"
    ));

    /**
     * Моркосвинья — ПКМ пустой рукой "съедает" морковку с хвоста (см. TameablePigEntity.tryEatCarrot).
     * ВАЖНО: сама морковка на хвосте — это визуальная деталь (новый рендер-слой модели свиньи),
     * которую мы пока не рисуем (нет арта/кастомной модели в проекте) — механика (эффект + кулдаун)
     * полностью работает, просто без внешней "морковки" на текстуре.
     *
     * КОНФЛИКТ НОСИТЕЛЕЙ: у "Регенерации" и "Моркосвиньи" в списке один и тот же носитель — Золотая
     * морковь. Т.к. Экстрактор ищет совпадение по (носители + катализатор), с одинаковым катализатором
     * они были бы неразличимы (всегда матчилась бы только первая по порядку регистрации). Поэтому здесь
     * катализатор — Огненный порошок вместо Редстоуна, просто чтобы развести рецепты. Если хочешь другое
     * решение (например, поменять сам носитель Моркосвиньи) — легко исправить.
     */
    public static final MutationDefinition CARROT_PIG = register(new MutationDefinition(
            "carrot_pig", "mutation.pig_mod.carrot_pig",
            5, 1, 1,
            List.of(Items.GOLDEN_CARROT), Items.BLAZE_POWDER,
            null,
            level -> "КД " + (TameablePigEntity.carrotCooldownTicks(level) / 20) + "с"
    ));

    // ==================================================================================
    // ==== TIER 2 — Продвинутый Экстрактор. Ресурсы Нижнего/Верхнего мира.           ====
    // ==================================================================================
    // Катализатор Tier 2 — Огненный порошок (нижнемирская тема), у всех мутаций этого Tier'а.
    // "Рабочие свиньи" (5 профессий) и "Курьер" сюда пока НЕ включены — это две мутации, которым
    // нужны отдельные системы (ИИ добычи ресурсов/боя и привязка сундука через посох), а не просто
    // атрибуты/иммунитеты. Сделаем их отдельным шагом, чтобы не смешивать простое со сложным.

    /**
     * Сверхдыхание — полный иммунитет к утоплению/удушению (см. isInvulnerableTo/tick в TameablePigEntity).
     * Бинарная мутация: maxLevel=1, прокачивать нечего, просто "есть или нет".
     */
    public static final MutationDefinition OVERBREATHING = register(new MutationDefinition(
            "overbreathing", "mutation.pig_mod.overbreathing",
            1, 1, 2,
            List.of(Items.NAUTILUS_SHELL), Items.BLAZE_POWDER,
            null,
            level -> "Иммунитет к утоплению"
    ));

    /**
     * Нерушимый — полный иммунитет к огню/лаве (fireImmune()).
     * ВАЖНО: "Зелье огнестойкости II" — как и в Tier 1 со Скоростью, наша система ДНК-носителей
     * сверяет только предмет (Items.POTION), а не какое именно зелье налито — подойдёт любое зелье.
     */
    public static final MutationDefinition FIRE_IMMUNITY = register(new MutationDefinition(
            "fire_immunity", "mutation.pig_mod.fire_immunity",
            1, 1, 2,
            List.of(Items.MAGMA_CREAM, Items.POTION), Items.BLAZE_POWDER,
            null,
            level -> "Иммунитет к огню/лаве"
    ));

    /** Зельеустойчивость — яд и иссушение не действуют вовсе (canBeAffected блокирует их). */
    public static final MutationDefinition POTION_RESISTANCE = register(new MutationDefinition(
            "potion_resistance", "mutation.pig_mod.potion_resistance",
            1, 1, 2,
            List.of(Items.GHAST_TEAR, Items.BREWING_STAND), Items.BLAZE_POWDER,
            null,
            level -> "Иммунитет к яду/иссушению"
    ));

    /**
     * Планирование — без урона от падения + гасим скорость падения (см. tickTier2Mutations).
     * ВАЖНО: "Зелье плавного падения" — та же оговорка про Items.POTION, что и выше.
     */
    public static final MutationDefinition GLIDING = register(new MutationDefinition(
            "gliding", "mutation.pig_mod.gliding",
            1, 1, 2,
            List.of(Items.POTION, Items.PHANTOM_MEMBRANE), Items.BLAZE_POWDER,
            null,
            level -> "Без урона от падения"
    ));

    /**
     * Плавание — Прыть Дельфина, пока свинья в воде; сила эффекта растёт с уровнем (макс. уровень 5).
     */
    public static final MutationDefinition AQUATIC = register(new MutationDefinition(
            "aquatic", "mutation.pig_mod.aquatic",
            5, 1, 2,
            List.of(Items.HEART_OF_THE_SEA, Items.DARK_PRISMARINE), Items.BLAZE_POWDER,
            null,
            level -> "Прыть дельфина " + level
    ));

    // ==== Рабочие профессии + Курьер ====
    // Все бинарные (maxLevel=1): либо свинья умеет "работу", либо нет — прокачивать пока нечего
    // (в будущем можно добавить уровни на скорость/радиус добычи).
    // ВАЖНО: "Алмазный инструмент по профессии" — для Рыбака и Лучника в ванили нет алмазных
    // удочки/лука, поэтому взял обычную удочку/лук как смысловой аналог.

    public static final MutationDefinition LUMBERJACK = register(new MutationDefinition(
            "lumberjack", "mutation.pig_mod.lumberjack",
            10, 1, 2,
            List.of(Items.DIAMOND_AXE, Items.HAY_BLOCK), Items.BLAZE_POWDER,
            null,
            level -> "Радиус " + (Math.min(24, 4 + level * 2)) + ", рубит дерево целиком"
    ));

    public static final MutationDefinition FARMER = register(new MutationDefinition(
            "farmer", "mutation.pig_mod.farmer",
            10, 1, 2,
            List.of(Items.DIAMOND_HOE, Items.HAY_BLOCK), Items.BLAZE_POWDER,
            null,
            level -> "Радиус " + (Math.min(24, 4 + level * 2)) + ", пересаживает урожай"
    ));

    public static final MutationDefinition MINER = register(new MutationDefinition(
            "miner", "mutation.pig_mod.miner",
            10, 1, 2,
            List.of(Items.DIAMOND_PICKAXE, Items.HAY_BLOCK), Items.BLAZE_POWDER,
            null,
            level -> "Радиус " + (Math.min(24, 4 + level * 2)) + ", роет к руде сквозь камень"
    ));

    public static final MutationDefinition FISHER = register(new MutationDefinition(
            "fisher", "mutation.pig_mod.fisher",
            10, 1, 2,
            List.of(Items.FISHING_ROD, Items.HAY_BLOCK), Items.BLAZE_POWDER,
            null,
            level -> "Улов раз в " + (TameablePigEntity.fisherIntervalTicks(level) / 20) + "с"
    ));

    public static final MutationDefinition ARCHER = register(new MutationDefinition(
            "archer", "mutation.pig_mod.archer",
            5, 1, 2,
            List.of(Items.BOW, Items.HAY_BLOCK), Items.BLAZE_POWDER,
            null,
            level -> "Точность стрельбы " + level + "/5"
    ));

    /**
     * Курьер — периодически (раз в ~5 сек) переносит содержимое внутреннего буфера свиньи
     * (тот же, что наполняют рабочие профессии) в сундук, привязанный Посохом Курьера.
     * УПРОЩЕНИЕ: перенос происходит "магически" по факту привязки, без реальной ходьбы свиньи
     * до сундука с сумкой в зубах — полноценная навигация до сундука через весь мир была бы
     * отдельной большой AI-задачей (обход препятствий, путь может быть очень длинным и т.д.).
     */
    public static final MutationDefinition COURIER = register(new MutationDefinition(
            "courier", "mutation.pig_mod.courier",
            1, 1, 2,
            List.of(Items.ENDER_CHEST, Items.LEAD), Items.BLAZE_POWDER,
            null,
            level -> "Разгружает добычу в привязанный сундук"
    ));

    /**
     * Ничего не делает — вызывается один раз на старте мода (см. ExampleMod), чтобы
     * гарантированно "тронуть" этот класс и выполнить статическую инициализацию
     * (регистрацию мутаций) до того, как она понадобится в игре.
     */
    public static void bootstrap() {
    }
}