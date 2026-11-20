package com.example.examplemod.mutation;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.Tr;
import com.example.examplemod.TameablePigEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potions;

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
            throw new IllegalStateException("Mutation with id '" + definition.getId() + "' is already registered");
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

    /** Здоровье — до Lvl25 (MAX): +100 HP. Каждый уровень даёт +4 HP. */
    public static final MutationDefinition HEALTH_BOOST = register(new MutationDefinition(
            "health_boost", "mutation.pig_mod.health_boost",
            25, 1, 1,
            List.of(MutationIngredient.of(Items.GOLDEN_APPLE)), Items.REDSTONE,
            (pig, level) -> pig.setAttributeBonus(Attributes.MAX_HEALTH, modLoc("mutation_health_boost"),
                    level * 4.0, AttributeModifier.Operation.ADD_VALUE),
            level -> Tr.s("mutation.pig_mod.level.health", level * 4)
    ));

    /**
     * Регенерация — постоянный пассивный реген, тикает в TameablePigEntity.customServerAiStep().
     * Эффекта-атрибута нет (effectApplier == null) — сама механика завязана на hasMutation()/уровень
     * прямо в коде свиньи, т.к. регенерация во времени не выражается через AttributeModifier.
     */
    public static final MutationDefinition REGENERATION = register(new MutationDefinition(
            "regeneration", "mutation.pig_mod.regeneration",
            10, 1, 1,
            List.of(MutationIngredient.of(Items.GOLDEN_CARROT)), Items.REDSTONE,
            null,
            level -> Tr.s("mutation.pig_mod.level.regeneration", TameablePigEntity.regenIntervalTicks(level) / 20.0)
    ));

    /** Броня — плоский бонус к Attributes.ARMOR. */
    public static final MutationDefinition ARMOR = register(new MutationDefinition(
            "armor", "mutation.pig_mod.armor",
            20, 1, 1,
            List.of(MutationIngredient.of(Items.IRON_BLOCK)), Items.REDSTONE,
            (pig, level) -> pig.setAttributeBonus(Attributes.ARMOR, modLoc("mutation_armor"),
                    level, AttributeModifier.Operation.ADD_VALUE),
            level -> Tr.s("mutation.pig_mod.level.armor", level)
    ));

    /** Урон — плоский бонус к Attributes.ATTACK_DAMAGE (отдельно от бонуса за уровень свиньи). */
    public static final MutationDefinition DAMAGE = register(new MutationDefinition(
            "damage", "mutation.pig_mod.damage",
            20, 1, 1,
            List.of(MutationIngredient.of(Items.DIAMOND_SWORD)), Items.REDSTONE,
            (pig, level) -> pig.setAttributeBonus(Attributes.ATTACK_DAMAGE, modLoc("mutation_damage"),
                    level, AttributeModifier.Operation.ADD_VALUE),
            level -> Tr.s("mutation.pig_mod.level.damage", level)
    ));

    /**
     * Скорость — +5% к скорости передвижения за уровень (множитель, не плоский бонус).
     * Носитель — именно "Зелье стремительности II" (minecraft:strong_swiftness): содержимое зелья
     * проверяется через MutationIngredient.potion(...), обычная бутылка/другое зелье не подойдёт.
     */
    public static final MutationDefinition SPEED = register(new MutationDefinition(
            "speed", "mutation.pig_mod.speed",
            10, 1, 1,
            List.of(MutationIngredient.potion(Items.POTION, Potions.STRONG_SWIFTNESS)), Items.REDSTONE,
            (pig, level) -> pig.setAttributeBonus(Attributes.MOVEMENT_SPEED, modLoc("mutation_speed"),
                    level * 0.05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
            level -> Tr.s("mutation.pig_mod.level.speed", level * 5)
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
            List.of(MutationIngredient.of(Items.GOLDEN_CARROT)), Items.BLAZE_POWDER,
            null,
            level -> Tr.s("mutation.pig_mod.level.carrot", TameablePigEntity.carrotCooldownTicks(level) / 20)
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
            List.of(MutationIngredient.of(Items.NAUTILUS_SHELL),
                    MutationIngredient.potion(Items.POTION, Potions.WATER_BREATHING, Potions.LONG_WATER_BREATHING)),
            Items.BLAZE_POWDER,
            null,
            level -> Tr.s("mutation.pig_mod.level.overbreathing")
    ));

    /**
     * Нерушимый — полный иммунитет к огню/лаве (fireImmune()).
     * ВАЖНО: "Зелья огнестойкости II" в ванили не существует (есть только обычное и затяжное),
     * поэтому принимаются Зелье огнестойкости и Затяжное зелье огнестойкости.
     */
    public static final MutationDefinition FIRE_IMMUNITY = register(new MutationDefinition(
            "fire_immunity", "mutation.pig_mod.fire_immunity",
            1, 1, 2,
            List.of(MutationIngredient.of(Items.MAGMA_CREAM),
                    MutationIngredient.potion(Items.POTION, Potions.FIRE_RESISTANCE, Potions.LONG_FIRE_RESISTANCE)),
            Items.BLAZE_POWDER,
            null,
            level -> Tr.s("mutation.pig_mod.level.fire_immunity")
    ));

    /** Зельеустойчивость — яд и иссушение не действуют вовсе (canBeAffected блокирует их). */
    public static final MutationDefinition POTION_RESISTANCE = register(new MutationDefinition(
            "potion_resistance", "mutation.pig_mod.potion_resistance",
            1, 1, 2,
            List.of(MutationIngredient.of(Items.GHAST_TEAR), MutationIngredient.of(Items.BREWING_STAND)), Items.BLAZE_POWDER,
            null,
            level -> Tr.s("mutation.pig_mod.level.potion_resistance")
    ));

    /**
     * Планирование — без урона от падения + гасим скорость падения (см. tickTier2Mutations).
     * Носитель — Зелье плавного падения (обычное или затяжное).
     */
    public static final MutationDefinition GLIDING = register(new MutationDefinition(
            "gliding", "mutation.pig_mod.gliding",
            1, 1, 2,
            List.of(MutationIngredient.potion(Items.POTION, Potions.SLOW_FALLING, Potions.LONG_SLOW_FALLING),
                    MutationIngredient.of(Items.PHANTOM_MEMBRANE)),
            Items.BLAZE_POWDER,
            null,
            level -> Tr.s("mutation.pig_mod.level.gliding")
    ));

    /**
     * Плавание — Прыть Дельфина, пока свинья в воде; сила эффекта растёт с уровнем (макс. уровень 5).
     */
    public static final MutationDefinition AQUATIC = register(new MutationDefinition(
            "aquatic", "mutation.pig_mod.aquatic",
            5, 1, 2,
            List.of(MutationIngredient.of(Items.HEART_OF_THE_SEA), MutationIngredient.of(Items.DARK_PRISMARINE)), Items.BLAZE_POWDER,
            null,
            level -> Tr.s("mutation.pig_mod.level.aquatic", level)
    ));

    // ==== Рабочие профессии + Курьер ====
    // Все бинарные (maxLevel=1): либо свинья умеет "работу", либо нет — прокачивать пока нечего
    // (в будущем можно добавить уровни на скорость/радиус добычи).
    // ВАЖНО: "Алмазный инструмент по профессии" — для Рыбака и Лучника в ванили нет алмазных
    // удочки/лука, поэтому взял обычную удочку/лук как смысловой аналог.

    public static final MutationDefinition LUMBERJACK = register(new MutationDefinition(
            "lumberjack", "mutation.pig_mod.lumberjack",
            10, 1, 2,
            List.of(MutationIngredient.of(Items.DIAMOND_AXE), MutationIngredient.of(Items.HAY_BLOCK)), Items.BLAZE_POWDER,
            null,
            level -> Tr.s("mutation.pig_mod.level.lumberjack", describeRadius(level))
    ));

    public static final MutationDefinition FARMER = register(new MutationDefinition(
            "farmer", "mutation.pig_mod.farmer",
            10, 1, 2,
            List.of(MutationIngredient.of(Items.DIAMOND_HOE), MutationIngredient.of(Items.HAY_BLOCK)), Items.BLAZE_POWDER,
            null,
            level -> Tr.s("mutation.pig_mod.level.farmer", describeRadius(level))
    ));

    public static final MutationDefinition MINER = register(new MutationDefinition(
            "miner", "mutation.pig_mod.miner",
            10, 1, 2,
            List.of(MutationIngredient.of(Items.DIAMOND_PICKAXE), MutationIngredient.of(Items.HAY_BLOCK)), Items.BLAZE_POWDER,
            null,
            level -> Tr.s("mutation.pig_mod.level.miner", describeRadius(level))
    ));

    /** Радиус рабочих профессий: Lvl1 = 24 блока -> Lvl10 = 80 блоков (5 чанков). См. ResourceGatherGoal. */
    private static String describeRadius(int level) {
        int radius = 24 + (level - 1) * ((80 - 24) / 9);
        return Tr.s("mutation.pig_mod.level.radius", Math.min(80, radius));
    }

    public static final MutationDefinition FISHER = register(new MutationDefinition(
            "fisher", "mutation.pig_mod.fisher",
            10, 1, 2,
            List.of(MutationIngredient.of(Items.FISHING_ROD), MutationIngredient.of(Items.HAY_BLOCK)), Items.BLAZE_POWDER,
            null,
            level -> Tr.s("mutation.pig_mod.level.fisher", TameablePigEntity.fisherIntervalTicks(level) / 20)
    ));

    public static final MutationDefinition ARCHER = register(new MutationDefinition(
            "archer", "mutation.pig_mod.archer",
            5, 1, 2,
            List.of(MutationIngredient.of(Items.BOW), MutationIngredient.of(Items.HAY_BLOCK)), Items.BLAZE_POWDER,
            null,
            level -> Tr.s("mutation.pig_mod.level.archer", level)
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
            List.of(MutationIngredient.of(Items.ENDER_CHEST), MutationIngredient.of(Items.LEAD)), Items.BLAZE_POWDER,
            null,
            level -> Tr.s("mutation.pig_mod.level.courier")
    ));

    // ==================================================================================
    // ==== TIER 3 — Элитный Экстрактор. Редчайшие ингредиенты.                       ====
    // ==================================================================================
    // Катализатор Tier 3 — Хорус (тема Края), у всех мутаций этого Tier'а — та же схема,
    // что и у Tier 1 (Редстоун) / Tier 2 (Огненный порошок): один катализатор на весь Tier,
    // сами мутации различаются набором ДНК-носителей.
    //
    // Механики "Изменение размера", "Эндерсвинья", "Нежить" и "Алхимик" (эффекты + тики) уже
    // были реализованы в TameablePigEntity — здесь только регистрируем сами мутации (рецепт
    // синтеза + описание для UI), т.к. без записи в этом реестре их нельзя было получить через
    // Инжектор. "Кукла Вуду" аналогично уже обрабатывается в PigEventHandlers.
    //
    // Правило экстрактора: у Tier N ровно N слотов для ДНК-носителей, и в рецепте мутации этого
    // Tier'а каждый слот заполнен (MutationDefinition проверяет это при старте мода).
    // "/" в исходном списке мутаций означает "и": это ещё один ингредиент, а не вариант.
    // Зелья проверяются по содержимому (MutationIngredient.potion), а не просто "любая бутылка".

    private static final net.minecraft.world.item.Item TIER3_CATALYST = Items.CHORUS_FRUIT;
    private static final net.minecraft.world.item.Item TIER4_CATALYST = Items.ECHO_SHARD;

    /**
     * Свиночеловек — двуногая форма/полноразмерная броня и оружие. Функционально: с этой мутацией
     * свинья может быть экипирована (ПКМ бронёй/оружием без Shift — см. mobInteract), броня и урон
     * оружия учитываются автоматически (ванильная система атрибутов предметов работает для любого
     * LivingEntity, не только для игрока). Смена модельки на двуногую — чисто визуальная часть
     * (кастомный рендерер/модель), которой в проекте нет, поэтому свинья остаётся с обычной моделью
     * (см. аналогичную оговорку у "Моркосвиньи" в Tier 1).
     */
    public static final MutationDefinition PIGMAN = register(new MutationDefinition(
            "pigman", "mutation.pig_mod.pigman",
            1, 1, 3,
            List.of(MutationIngredient.of(Items.GOLDEN_SWORD), MutationIngredient.of(Items.ROTTEN_FLESH), MutationIngredient.of(Items.NETHERITE_SCRAP)), TIER3_CATALYST,
            null,
            level -> Tr.s("mutation.pig_mod.level.pigman")
    ));

    /**
     * Изменение размера — переключение маленький/обычный/большой кнопкой прямо в строке этой
     * мутации на Вкладке 2 GUI (см. TameablePigEntity.cycleSizeState/applySizeState и
     * PigSizeCyclePacket). Прокачка (Lvl 1-10) усиливает саму разницу в размере: уменьшение
     * сохраняется в прежних пределах, а "Большой" на максимальном уровне становится примерно
     * в 5.6 раза выше базового масштаба, вместе с сопутствующими
     * бонусами (скорость у маленького; ХП/урон/отбрасывание у большого) — см. applySizeState().
     * Носители-зелья проверяются по содержимому: Зелье силы II + Зелье стремительности
     * (обычное, затяжное или II) + Око Эндера.
     */
    public static final MutationDefinition RESIZE = register(new MutationDefinition(
            "resize", "mutation.pig_mod.resize",
            10, 2, 3,
            List.of(MutationIngredient.potion(Items.POTION, Potions.STRONG_STRENGTH),
                    MutationIngredient.potion(Items.POTION, Potions.SWIFTNESS, Potions.LONG_SWIFTNESS, Potions.STRONG_SWIFTNESS),
                    MutationIngredient.of(Items.ENDER_EYE)),
            TIER3_CATALYST,
            (pig, level) -> pig.refreshSizeState(),
            level -> Tr.s("mutation.pig_mod.level.resize", level)
    ));

    /**
     * Эндерсвинья — телепортируется от каждого снаряда и умеет аварийно уходить от опасности
     * при критически низком здоровье (см. TameablePigEntity.hurt()).
     */
    public static final MutationDefinition ENDERPIG = register(new MutationDefinition(
            "enderpig", "mutation.pig_mod.enderpig",
            1, 1, 3,
            List.of(MutationIngredient.of(Items.ENDER_EYE), MutationIngredient.of(Items.ENDER_PEARL), MutationIngredient.of(Items.PURPUR_BLOCK)), TIER3_CATALYST,
            null,
            level -> Tr.s("mutation.pig_mod.level.enderpig")
    ));

    /**
     * Нежить — враждебные мобы не выбирают свинью целью, пока она сама не вступит с ними в бой.
     * Во время езды тот же принцип распространяется и на хозяина. После атаки/получения урона
     * враждебный моб может нормально выбрать свинью или всадника своей целью.
     */
    public static final MutationDefinition UNDEAD_PIG = register(new MutationDefinition(
            "undead_pig", "mutation.pig_mod.undead_pig",
            1, 1, 3,
            List.of(MutationIngredient.of(Items.SCULK_CATALYST), MutationIngredient.of(Items.WITHER_SKELETON_SKULL),
                    MutationIngredient.potion(Items.POTION, Potions.STRONG_HARMING)),
            TIER3_CATALYST,
            null,
            level -> Tr.s("mutation.pig_mod.level.undead_pig")
    ));

    /**
     * Алхимик — пока хозяин рядом, выбирает бафф по ситуации и бросает настоящий Splash Potion.
     * Уровень влияет и на кулдаун, и на силу зелья: для эффектов, которые поддерживают уровни,
     * Lvl2 = эффект II, Lvl3 = III и т.д. (см. TameablePigEntity.tickAlchemist/throwAlchemistPotion).
     * Носители: Варочная стойка + Оседающее зелье исцеления (Исцеление или Исцеление II) + Слеза гаста.
     */
    public static final MutationDefinition ALCHEMIST = register(new MutationDefinition(
            "alchemist", "mutation.pig_mod.alchemist",
            10, 1, 3,
            List.of(MutationIngredient.of(Items.BREWING_STAND),
                    MutationIngredient.potion(Items.LINGERING_POTION, Potions.HEALING, Potions.STRONG_HEALING),
                    MutationIngredient.of(Items.GHAST_TEAR)),
            TIER3_CATALYST,
            null,
            level -> Tr.s("mutation.pig_mod.level.alchemist", TameablePigEntity.alchemistCooldownTicks(level) / 20)
    ));

    /**
     * Наездник — позволяет оседлать свинью (ПКМ Седлом, затем ПКМ пустой рукой, чтобы сесть верхом)
     * и транслирует владельцу защитные мутации свиньи, пока он верхом: подводное дыхание,
     * иммунитет к огню/лаве, яду/иссушению, урону от падения, снарядам и атакам мобов,
     * а также Прыть Дельфина от "Плавания" (см. TameablePigEntity и PigEventHandlers).
     * ВАЖНО: пока свинья оседлана этой мутацией, обычный ПКМ пустой рукой садит игрока верхом
     * ВМЕСТО переключения "сесть/встать" и вместо поедания морковки с хвоста — это осознанный
     * компромисс, иначе два разных действия конфликтовали бы на одном и том же клике.
     * Рецепт: Седло + Незеритовый слиток + Поводок. Седло всё равно нужно, чтобы оседлать свинью после синтеза.
     */
    public static final MutationDefinition RIDER = register(new MutationDefinition(
            "rider", "mutation.pig_mod.rider",
            1, 1, 3,
            List.of(MutationIngredient.of(Items.SADDLE), MutationIngredient.of(Items.NETHERITE_INGOT),
                    MutationIngredient.of(Items.LEAD)),
            TIER3_CATALYST,
            null,
            level -> Tr.s("mutation.pig_mod.level.rider")
    ));

    /**
     * Кукла Вуду — весь входящий урон владельца перенаправляется на свинью (см. PigEventHandlers).
     * Рецепт: Тотем бессмертия + Незеритовый слиток + Око Эндера.
     */
    public static final MutationDefinition VOODOO = register(new MutationDefinition(
            "voodoo", "mutation.pig_mod.voodoo",
            1, 1, 3,
            List.of(MutationIngredient.of(Items.TOTEM_OF_UNDYING), MutationIngredient.of(Items.NETHERITE_INGOT),
                    MutationIngredient.of(Items.ENDER_EYE)),
            TIER3_CATALYST,
            null,
            level -> Tr.s("mutation.pig_mod.level.voodoo")
    ));

    // ==================================================================================
    // ==== TIER 4 — Легендарные мутации. Незеритовый Экстрактор.                 ====
    // ==================================================================================
    // T4: 4 слота ДНК-носителей, все заполнены (4 ингредиента из списка мутаций),
    // а катализатор у всего Tier'а один — Осколок эха (так же, как Редстоун у T1, Огненный
    // порошок у T2 и Хорус у T3). Поэтому в рецептах Экстрасенса и Эфира Осколок эха нужен дважды:
    // один как ингредиент, второй как катализатор.

    /** Ангел — автоматически спасает свинью от смертельного урона и запускает кулдаун. */
    public static final MutationDefinition ANGEL = register(new MutationDefinition(
            "angel", "mutation.pig_mod.angel",
            5, 5, 4,
            List.of(MutationIngredient.of(Items.NETHER_STAR), MutationIngredient.of(Items.ENCHANTED_GOLDEN_APPLE), MutationIngredient.of(Items.TOTEM_OF_UNDYING), MutationIngredient.of(Items.AMETHYST_SHARD)),
            TIER4_CATALYST,
            null,
            level -> Tr.s("mutation.pig_mod.level.angel", TameablePigEntity.angelCooldownTicks(level) / 1200)
    ));

    /** Взрывная свинья — таранит врагов и создаёт безопасный для союзников взрыв. */
    public static final MutationDefinition EXPLOSIVE_PIG = register(new MutationDefinition(
            "explosive_pig", "mutation.pig_mod.explosive_pig",
            5, 5, 4,
            List.of(MutationIngredient.of(Items.END_CRYSTAL), MutationIngredient.of(Items.TNT), MutationIngredient.of(Items.NETHER_STAR), MutationIngredient.of(Items.NETHERITE_BLOCK)),
            TIER4_CATALYST,
            null,
            level -> Tr.s("mutation.pig_mod.level.explosive_pig", String.format("%.0f", TameablePigEntity.explosiveDamage(level)))
    ));

    /** Дракон — крупная форма + огненные снаряды; с Полётом сохраняет полноценное воздушное движение. */
    public static final MutationDefinition DRAGON = register(new MutationDefinition(
            "dragon", "mutation.pig_mod.dragon",
            5, 5, 4,
            List.of(MutationIngredient.of(Items.DRAGON_BREATH), MutationIngredient.of(Items.DRAGON_HEAD), MutationIngredient.of(Items.DRAGON_EGG), MutationIngredient.of(Items.NETHERITE_INGOT)),
            TIER4_CATALYST,
            (pig, level) -> pig.refreshDragonState(),
            level -> Tr.s("mutation.pig_mod.level.dragon",
                    String.format("%.1f", TameablePigEntity.dragonFireballDamage(level)),
                    TameablePigEntity.dragonHasBreathCloud(level) ? Tr.s("mutation.pig_mod.level.dragon_cloud") : "")
    ));

    /** Экстрасенс — создаёт магические порталы и стреляет быстрыми лазерными снарядами с иссушением. */
    public static final MutationDefinition PSYCHIC = register(new MutationDefinition(
            "psychic", "mutation.pig_mod.psychic",
            5, 5, 4,
            List.of(MutationIngredient.of(Items.ECHO_SHARD), MutationIngredient.of(Items.AMETHYST_SHARD), MutationIngredient.of(Items.ENDER_EYE), MutationIngredient.of(Items.END_CRYSTAL)),
            TIER4_CATALYST,
            null,
            level -> Tr.s("mutation.pig_mod.level.psychic",
                    TameablePigEntity.psychicPortalCount(level),
                    String.format("%.0f", TameablePigEntity.psychicDamage(level)))
    ));

    /** Эфир — на поверхности проходит сквозь преграды, с Полётом даёт полноценный NoClip. */
    public static final MutationDefinition ETHEREAL = register(new MutationDefinition(
            "ethereal", "mutation.pig_mod.ethereal",
            1, 5, 4,
            List.of(MutationIngredient.of(Items.ECHO_SHARD), MutationIngredient.of(Items.SCULK_CATALYST), MutationIngredient.of(Items.NETHER_STAR), MutationIngredient.of(Items.SHULKER_SHELL)),
            TIER4_CATALYST,
            null,
            level -> Tr.s("mutation.pig_mod.level.ethereal")
    ));

    /**
     * Уничтожитель — киборг-режим: глазные лазеры (спам) + ракеты, разрушающие блоки, а раз в 15-20 сек
     * трансформация в бомбардировщика (полёт на 20-30 блоков и сброс бомб, тоже с разрушением блоков).
     * Единственная Tier4-способность, которой намеренно разрешено ломать блоки — как и было заказано.
     */
    public static final MutationDefinition DESTROYER = register(new MutationDefinition(
            "destroyer", "mutation.pig_mod.destroyer",
            5, 5, 4,
            List.of(MutationIngredient.of(Items.NETHERITE_BLOCK), MutationIngredient.of(Items.REDSTONE_BLOCK), MutationIngredient.of(Items.BEACON), MutationIngredient.of(Items.END_CRYSTAL)),
            TIER4_CATALYST,
            null,
            level -> Tr.s("mutation.pig_mod.level.destroyer",
                    String.format("%.0f", TameablePigEntity.destroyerRocketDamage(level)),
                    TameablePigEntity.destroyerBombIntervalTicks(level) / 20)
    ));

    /**
     * Полёт — свинья не получает урон от падения и получает полноценную воздушную AI-навигацию
     * через FlyingPathNavigation/FlyingMoveControl. Без Наездника она самостоятельно летает по 3D-пути
     * и обходит препятствия; вместе с Наездником хозяин получает ручное управление: Space — вверх,
     * Shift — вниз, X — спешиться. Прокачка увеличивает скорость полёта — см.
     * TameablePigEntity.flightSpeedMultiplier().
     */
    public static final MutationDefinition FLIGHT = register(new MutationDefinition(
            "flight", "mutation.pig_mod.flight",
            5, 2, 3,
            List.of(MutationIngredient.of(Items.ELYTRA), MutationIngredient.of(Items.PHANTOM_MEMBRANE), MutationIngredient.of(Items.FIREWORK_ROCKET)), TIER3_CATALYST,
            null,
            level -> Tr.s("mutation.pig_mod.level.flight", String.format("%.1f", TameablePigEntity.flightSpeedMultiplier(level)))
    ));

    /**
     * Ничего не делает — вызывается один раз на старте мода (см. ExampleMod), чтобы
     * гарантированно "тронуть" этот класс и выполнить статическую инициализацию
     * (регистрацию мутаций) до того, как она понадобится в игре.
     */
    public static void bootstrap() {
    }
}