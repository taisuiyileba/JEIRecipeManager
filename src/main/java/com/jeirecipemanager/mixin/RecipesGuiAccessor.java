package com.jeirecipemanager.mixin;

import mezz.jei.gui.recipes.RecipeGuiLayouts;
import mezz.jei.gui.recipes.RecipesGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RecipesGui.class)
public interface RecipesGuiAccessor {
    @Accessor("layouts")
    RecipeGuiLayouts jeirecipemanager_getLayouts();
}
