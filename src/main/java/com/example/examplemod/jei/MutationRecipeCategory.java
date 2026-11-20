package com.example.examplemod.jei;

import com.example.examplemod.DnaTubeItem;
import com.example.examplemod.ModItems;
import com.example.examplemod.mutation.MutationDefinition;
import com.example.examplemod.mutation.MutationIngredient;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * JEI-категория "Мутации: Генетический экстрактор N". Показывает ровно то, что нужно положить
 * в GUI Экстрактора: пустую пробирку, ДНК-носители, катализатор — и какая получится пробирка.
 * Категорий четыре (по одной на Tier): так вкладка JEI сразу говорит, на каком экстракторе
 * крафтится мутация, а у самих экстракторов (I-IV) в JEI по клавише U/R открываются их рецепты.
 *
 * Данные берутся прямо из {@link MutationDefinition} — того же объекта, по которому Экстрактор
 * проверяет рецепт в игре, поэтому JEI и реальный крафт разойтись не могут.
 */
public class MutationRecipeCategory implements IRecipeCategory<MutationDefinition> {

    private static final int WIDTH = 156;
    private static final int HEIGHT = 70;
    private static final int TEXT_COLOR = 0xFF404040;

    private final int tier;
    private final RecipeType<MutationDefinition> recipeType;
    private final IDrawable icon;
    private final IDrawableStatic slot;
    private final IDrawableStatic arrow;

    public MutationRecipeCategory(IGuiHelper guiHelper, int tier, RecipeType<MutationDefinition> recipeType) {
        this.tier = tier;
        this.recipeType = recipeType;
        this.icon = guiHelper.createDrawableItemStack(PigJeiPlugin.extractorStack(tier));
        this.slot = guiHelper.getSlotDrawable();
        this.arrow = guiHelper.getRecipeArrow();
    }

    @Override
    public RecipeType<MutationDefinition> getRecipeType() {
        return recipeType;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.pig_mod.category",
                Component.translatable("block.pig_mod.genetic_extractor_tier" + tier));
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, MutationDefinition recipe, IFocusGroup focuses) {
        // Основа — пустая пробирка (слот "основа" в GUI экстрактора)
        builder.addSlot(RecipeIngredientRole.INPUT, 1, 14)
                .setBackground(slot, -1, -1)
                .addItemStack(new ItemStack(ModItems.EMPTY_DNA_TUBE.get()));

        // ДНК-носители. Для вариантов "A / B" JEI сам листает варианты в одном слоте,
        // для зелий показывает именно нужное зелье.
        List<MutationIngredient> carriers = recipe.getCarrierIngredients();
        for (int i = 0; i < carriers.size(); i++) {
            builder.addSlot(RecipeIngredientRole.INPUT, 28 + i * 20, 14)
                    .setBackground(slot, -1, -1)
                    .addItemStacks(carriers.get(i).getDisplayStacks());
        }

        // Катализатор
        builder.addSlot(RecipeIngredientRole.CATALYST, 1, 40)
                .setBackground(slot, -1, -1)
                .addItemStack(new ItemStack(recipe.getCatalystItem()));

        // Результат — пробирка с мутацией
        builder.addSlot(RecipeIngredientRole.OUTPUT, 136, 14)
                .setBackground(slot, -1, -1)
                .addItemStack(DnaTubeItem.createFilled(recipe.getId()));
    }

    @Override
    public void draw(MutationDefinition recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics,
                     double mouseX, double mouseY) {
        net.minecraft.client.gui.Font font = Minecraft.getInstance().font;

        guiGraphics.drawString(font, Component.translatable(recipe.getDisplayNameKey()), 0, 0, TEXT_COLOR, false);
        arrow.draw(guiGraphics, 108, 14);
        guiGraphics.drawString(font, Component.translatable("jei.pig_mod.catalyst"), 22, 44, TEXT_COLOR, false);
        guiGraphics.drawString(font,
                Component.translatable("jei.pig_mod.min_tier", String.valueOf(recipe.getExtractorTier())),
                0, 60, TEXT_COLOR, false);
    }
}
