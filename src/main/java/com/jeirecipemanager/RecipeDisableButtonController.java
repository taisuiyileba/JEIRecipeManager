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

public class RecipeDisableButtonController implements IIconButtonController {
    private static final ResourceLocation ENABLE_TEXTURE = ResourceLocation.fromNamespaceAndPath("jeirecipemanager", "textures/gui/recipe_enable.png");
    private static final ResourceLocation DISABLE_TEXTURE = ResourceLocation.fromNamespaceAndPath("jeirecipemanager", "textures/gui/recipe_disable.png");

    private final IDrawable offIcon;
    private final IDrawable onIcon;
    private final String recipeId;
    private final String categoryUid;
    private boolean toggledOn;

    public RecipeDisableButtonController(IRecipeLayoutDrawable<?> recipeLayout) {
        this.offIcon = new DrawableResource(DISABLE_TEXTURE, 0, 0, 9, 9, 0, 0, 0, 0, 9, 9);
        this.onIcon = new DrawableResource(ENABLE_TEXTURE, 0, 0, 9, 9, 0, 0, 0, 0, 9, 9);

        IRecipeCategory<?> category = recipeLayout.getRecipeCategory();
        Object recipe = recipeLayout.getRecipe();
        ResourceLocation registryName = ((IRecipeCategory) category).getRegistryName(recipe);
        this.recipeId = registryName != null ? registryName.toString() : "";
        this.categoryUid = category.getRecipeType().getUid().toString();

        this.toggledOn = this.recipeId.isEmpty() || !DisabledRecipesManager.isRecipeDisabled(this.recipeId);
    }

    public boolean hasValidRecipeId() {
        return !this.recipeId.isEmpty();
    }

    @Override
    public void updateState(IButtonState state) {
        if (toggledOn) {
            state.setForcePressed(true);
            state.setIcon(onIcon);
        } else {
            state.setForcePressed(false);
            state.setIcon(offIcon);
        }
    }

    @Override
    public boolean onPress(IJeiUserInput input) {
        if (!input.isSimulate() && !this.recipeId.isEmpty()) {
            this.toggledOn = !this.toggledOn;
            if (toggledOn) {
                DisabledRecipesManager.enableRecipe(this.recipeId);
            } else {
                DisabledRecipesManager.disableRecipe(this.recipeId, this.categoryUid);
            }
        }
        return true;
    }

    @Override
    public void getTooltips(ITooltipBuilder tooltip) {
        if (toggledOn) {
            tooltip.add(Component.translatable("jeirecipemanager.tooltip.recipe.enabled"));
        } else {
            tooltip.add(Component.translatable("jeirecipemanager.tooltip.recipe.disabled"));
        }
    }
}