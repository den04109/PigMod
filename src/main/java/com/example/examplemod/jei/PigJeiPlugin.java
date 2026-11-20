package com.example.examplemod.jei;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.ModItems;
import com.example.examplemod.mutation.MutationDefinition;
import com.example.examplemod.mutation.Mutations;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Интеграция с JEI (мягкая зависимость: сам мод работает и без JEI — этот класс подгружает только JEI).
 * Показывает рецепты мутаций Генетического Экстрактора: ингредиенты, катализатор и Tier экстрактора.
 */
@JeiPlugin
public class PigJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "jei_plugin");

    public static final int MAX_TIER = 4;

    /** Типы рецептов JEI: индекс 0 = Tier 1, ... индекс 3 = Tier 4. */
    private static final List<RecipeType<MutationDefinition>> TYPES = new ArrayList<>();

    static {
        for (int tier = 1; tier <= MAX_TIER; tier++) {
            TYPES.add(RecipeType.create(ExampleMod.MODID, "mutation_tier" + tier, MutationDefinition.class));
        }
    }

    public static RecipeType<MutationDefinition> typeFor(int tier) {
        return TYPES.get(tier - 1);
    }

    public static ItemStack extractorStack(int tier) {
        Item item = switch (tier) {
            case 1 -> ModItems.GENETIC_EXTRACTOR_TIER1_ITEM.get();
            case 2 -> ModItems.GENETIC_EXTRACTOR_TIER2_ITEM.get();
            case 3 -> ModItems.GENETIC_EXTRACTOR_TIER3_ITEM.get();
            default -> ModItems.GENETIC_EXTRACTOR_TIER4_ITEM.get();
        };
        return new ItemStack(item);
    }

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper guiHelper = registration.getJeiHelpers().getGuiHelper();
        for (int tier = 1; tier <= MAX_TIER; tier++) {
            registration.addRecipeCategories(new MutationRecipeCategory(guiHelper, tier, typeFor(tier)));
        }
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        for (int tier = 1; tier <= MAX_TIER; tier++) {
            final int currentTier = tier;
            List<MutationDefinition> recipes = Mutations.all().values().stream()
                    .filter(definition -> definition.getExtractorTier() == currentTier)
                    .toList();
            registration.addRecipes(typeFor(tier), recipes);
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        // Экстрактор Tier M умеет синтезировать мутации всех Tier'ов <= M (см. findMatchingMutation),
        // поэтому в категорию Tier N попадают экстракторы с Tier >= N.
        for (int recipeTier = 1; recipeTier <= MAX_TIER; recipeTier++) {
            for (int extractorTier = recipeTier; extractorTier <= MAX_TIER; extractorTier++) {
                registration.addRecipeCatalyst(extractorStack(extractorTier), typeFor(recipeTier));
            }
        }
    }
}
