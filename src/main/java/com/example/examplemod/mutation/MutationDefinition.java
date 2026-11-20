package com.example.examplemod.mutation;

import com.example.examplemod.TameablePigEntity;
import net.minecraft.world.item.Item;

import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * Описание одной мутации (типа улучшения свиньи): health_boost, speed, alchemist и т.д.
 * Сам класс — просто "шаблон", конкретные мутации регистрируются в {@link Mutations}
 * (список пришлёт Денис позже, эта часть уже готова его принять).
 *
 * Помимо самого эффекта, здесь же описан состав синтеза в Генетическом Экстракторе —
 * так мутации и рецепты их получения хранятся в одном месте и не расходятся между собой.
 */
public class MutationDefinition {

    private final String id;                  // например "health_boost" — используется как ключ и как id пробирки ДНК
    private final String displayNameKey;       // ключ перевода, например "mutation.pig_mod.health_boost"
    private final int maxLevel;                // максимальный уровень мутации (Lvl X/MAX в ТЗ)
    private final int tierMultiplier;          // множитель стоимости: Tier1 мутация = 1, Tier4 = 5 и т.п.
    private final BiConsumer<TameablePigEntity, Integer> effectApplier; // применяет эффект уровня к свинье

    // ==== Состав синтеза (Генетический Экстрактор) ====
    private final int extractorTier;           // минимальный Tier Экстрактора, на котором можно синтезировать (1-4)
    private final List<MutationIngredient> carrierIngredients; // ДНК-носители (1-4 штуки, порядок не важен, дубликаты допустимы)
    private final Item catalystItem;            // катализатор: редстоун / огненный порошок / незеритовая пыль
    private final java.util.function.IntFunction<String> levelBonusDescriber; // необязательно: "+4 HP" и т.п. для UI

    public MutationDefinition(String id, String displayNameKey, int maxLevel, int tierMultiplier,
                               int extractorTier, List<MutationIngredient> carrierIngredients, Item catalystItem,
                               BiConsumer<TameablePigEntity, Integer> effectApplier) {
        this(id, displayNameKey, maxLevel, tierMultiplier, extractorTier, carrierIngredients, catalystItem,
                effectApplier, null);
    }

    /**
     * Полная версия конструктора — с описателем бонуса по уровню (для Вкладки 2 GUI, показывает,
     * например, "+4 HP" рядом с уровнем). Необязателен: если не нужен, используйте конструктор без него.
     */
    public MutationDefinition(String id, String displayNameKey, int maxLevel, int tierMultiplier,
                               int extractorTier, List<MutationIngredient> carrierIngredients, Item catalystItem,
                               BiConsumer<TameablePigEntity, Integer> effectApplier,
                               java.util.function.IntFunction<String> levelBonusDescriber) {
        if (carrierIngredients.size() != extractorTier) {
            // Защита от опечатки при регистрации: у Tier N ровно N слотов ДНК-носителей,
            // и в рецепте мутации этого Tier'а заполнен каждый из них.
            throw new IllegalArgumentException("Mutation '" + id + "': the number of DNA carriers (" + carrierIngredients.size()
                    + ") must equal the number of slots of Tier " + extractorTier);
        }
        this.id = id;
        this.displayNameKey = displayNameKey;
        this.maxLevel = maxLevel;
        this.tierMultiplier = tierMultiplier;
        this.extractorTier = extractorTier;
        this.carrierIngredients = List.copyOf(carrierIngredients);
        this.catalystItem = catalystItem;
        this.effectApplier = effectApplier;
        this.levelBonusDescriber = levelBonusDescriber;
    }

    public String getId() {
        return id;
    }

    public String getDisplayNameKey() {
        return displayNameKey;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public int getTierMultiplier() {
        return tierMultiplier;
    }

    public int getExtractorTier() {
        return extractorTier;
    }

    public List<MutationIngredient> getCarrierIngredients() {
        return carrierIngredients;
    }

    /**
     * Точное (мультимножественное) соответствие выложенных в слоты предметов списку носителей:
     * порядок слотов не важен, лишних предметов быть не должно. Поддерживает варианты "A / B" —
     * подбор назначения "предмет -> носитель" перебирается (носителей максимум 4, это мгновенно).
     *
     * @param placed только НЕпустые стаки из слотов-носителей
     */
    public boolean matchesCarriers(List<ItemStack> placed) {
        if (placed.size() != carrierIngredients.size()) {
            return false;
        }
        return assign(placed, 0, new boolean[carrierIngredients.size()]);
    }

    private boolean assign(List<ItemStack> placed, int index, boolean[] used) {
        if (index == placed.size()) {
            return true;
        }
        for (int j = 0; j < carrierIngredients.size(); j++) {
            if (!used[j] && carrierIngredients.get(j).test(placed.get(index))) {
                used[j] = true;
                if (assign(placed, index + 1, used)) {
                    return true;
                }
                used[j] = false;
            }
        }
        return false;
    }

    public Item getCatalystItem() {
        return catalystItem;
    }

    /** Короткое описание бонуса на данном уровне для UI (например "+4 HP"), или null, если не задано. */
    public String describeLevel(int level) {
        return levelBonusDescriber != null ? levelBonusDescriber.apply(level) : null;
    }

    /**
     * Стоимость прокачки с уровня currentLevel на currentLevel + 1, в очках развития.
     * Формула из ТЗ: Cost = CurrentLevel * TierMultiplier.
     * Пример (Tier 1, multiplier=1): 1 -> 2 стоит 1, 2 -> 3 стоит 2, ...
     * Пример (Tier 4, multiplier=5): 1 -> 2 стоит 5, 2 -> 3 стоит 10, ...
     */
    public int getUpgradeCost(int currentLevel) {
        return Math.max(1, currentLevel) * tierMultiplier;
    }

    /** Применяет эффект мутации текущего уровня к свинье (пересчёт статов, скоростей и т.п.). */
    public void applyEffect(TameablePigEntity pig, int level) {
        if (effectApplier != null) {
            effectApplier.accept(pig, level);
        }
    }
}