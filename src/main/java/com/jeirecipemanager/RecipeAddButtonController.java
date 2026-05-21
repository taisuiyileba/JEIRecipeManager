package com.jeirecipemanager;

import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.buttons.IButtonState;
import mezz.jei.api.gui.buttons.IIconButtonController;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.common.gui.elements.DrawableResource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

public class RecipeAddButtonController implements IIconButtonController {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("jeirecipemanager", "textures/gui/recipe_enable.png");

    private final IDrawable icon;
    private final String recipeId;
    private final boolean supportedRecipe;
    private final boolean generatedRecipe;

    public RecipeAddButtonController(IRecipeLayoutDrawable<?> recipeLayout) {
        this.icon = new DrawableResource(TEXTURE, 0, 0, 9, 9, 0, 0, 0, 0, 9, 9);
        IRecipeCategory<?> category = recipeLayout.getRecipeCategory();
        Object recipe = recipeLayout.getRecipe();
        ResourceLocation registryName = ((IRecipeCategory) category).getRegistryName(recipe);
        this.recipeId = registryName != null ? registryName.toString() : "";
        this.supportedRecipe = recipe instanceof RecipeHolder<?>;
        this.generatedRecipe = GeneratedRecipesManager.isGeneratedRecipeId(this.recipeId);
    }

    public boolean hasValidRecipeId() {
        return this.supportedRecipe && !this.recipeId.isEmpty();
    }

    @Override
    public void updateState(IButtonState state) {
        state.setIcon(icon);
        state.setForcePressed(RecipeEditManager.isEditing(recipeId));
    }

    @Override
    public boolean onPress(IJeiUserInput input) {
        if (!input.isSimulate() && !this.recipeId.isEmpty()) {
            if (RecipeEditManager.hasDraft(recipeId)) {
                RecipeEditManager.submit(recipeId);
            } else {
                RecipeEditManager.toggleEditing(recipeId);
            }
        }
        return true;
    }

    @Override
    public void getTooltips(ITooltipBuilder tooltip) {
        if (RecipeEditManager.hasDraft(recipeId)) {
            tooltip.add(Component.translatable(generatedRecipe ? "jeirecipemanager.tooltip.recipe_add.submit_modify" : "jeirecipemanager.tooltip.recipe_add.submit"));
        } else if (RecipeEditManager.isEditing(recipeId)) {
            tooltip.add(Component.translatable(generatedRecipe ? "jeirecipemanager.tooltip.recipe_add.editing_modify" : "jeirecipemanager.tooltip.recipe_add.editing"));
        } else {
            tooltip.add(Component.translatable(generatedRecipe ? "jeirecipemanager.tooltip.recipe_add.start_modify" : "jeirecipemanager.tooltip.recipe_add.start"));
        }
    }
}
