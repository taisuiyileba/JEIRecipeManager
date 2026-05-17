package com.jeirecipemanager.mixin;

import mezz.jei.gui.ingredients.IngredientFilter;
import mezz.jei.gui.ingredients.IngredientFilterApi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(IngredientFilterApi.class)
public interface IngredientFilterApiAccessor {
    @Accessor("ingredientFilter")
    IngredientFilter jeirecipemanager_getIngredientFilter();
}
