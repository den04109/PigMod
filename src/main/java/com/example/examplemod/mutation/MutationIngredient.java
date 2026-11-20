package com.example.examplemod.mutation;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.ArrayList;
import java.util.List;

/**
 * Один ДНК-носитель в рецепте мутации. Умеет то, чего не умел простой Item:
 *  - проверять конкретное зелье (например, Зелье стремительности II, а не любую бутылку).
 *
 * Тот же объект используется и для проверки в Экстракторе, и для показа в JEI,
 * поэтому рецепт в игре и рецепт в JEI не могут разойтись.
 */
public final class MutationIngredient {

    /** Один допустимый вариант: предмет (+ опционально список допустимых зелий для зельных предметов). */
    private record Option(Item item, List<Holder<Potion>> potions) {
        boolean test(ItemStack stack) {
            if (!stack.is(item)) {
                return false;
            }
            if (potions.isEmpty()) {
                return true; // зелье не важно (или это не зельный предмет)
            }
            PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
            if (contents == null || contents.potion().isEmpty()) {
                return false;
            }
            Potion actual = contents.potion().get().value();
            for (Holder<Potion> accepted : potions) {
                if (accepted.value() == actual) {
                    return true;
                }
            }
            return false;
        }
    }

    private final List<Option> options;

    private MutationIngredient(List<Option> options) {
        this.options = List.copyOf(options);
    }

    /** Обычный предмет. */
    public static MutationIngredient of(Item item) {
        return new MutationIngredient(List.of(new Option(item, List.of())));
    }

    /** Зельный предмет (бутылка / взрывное / оседающее) с одним из перечисленных зелий. */
    @SafeVarargs
    public static MutationIngredient potion(Item container, Holder<Potion>... acceptedPotions) {
        return new MutationIngredient(List.of(new Option(container, List.of(acceptedPotions))));
    }

    public boolean test(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        for (Option option : options) {
            if (option.test(stack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Все предметы, которые подходят под этот носитель, — для показа в JEI
     * (JEI сам прокручивает варианты в слоте). Для зелий — по одному стаку на каждое допустимое зелье.
     */
    public List<ItemStack> getDisplayStacks() {
        List<ItemStack> result = new ArrayList<>();
        for (Option option : options) {
            if (option.potions().isEmpty()) {
                result.add(new ItemStack(option.item()));
            } else {
                for (Holder<Potion> potion : option.potions()) {
                    result.add(PotionContents.createItemStack(option.item(), potion));
                }
            }
        }
        return result;
    }
}
