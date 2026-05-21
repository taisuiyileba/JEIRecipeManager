package com.jeirecipemanager.mixin;

import mezz.jei.gui.recipes.IRecipeLayoutWithButtons;
import mezz.jei.gui.recipes.RecipeGuiLayouts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(RecipeGuiLayouts.class)
public interface RecipeGuiLayoutsAccessor {
    @Accessor("recipeLayoutsWithButtons")
    List<IRecipeLayoutWithButtons<?>> jeirecipemanager_getRecipeLayoutsWithButtons();
}
