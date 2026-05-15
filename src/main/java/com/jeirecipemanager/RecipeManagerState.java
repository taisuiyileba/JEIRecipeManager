package com.jeirecipemanager;

import mezz.jei.api.recipe.IRecipeManager;

public class RecipeManagerState {
    private static IRecipeManager recipeManager;

    public static void setRecipeManager(IRecipeManager manager) {
        recipeManager = manager;
    }

    public static IRecipeManager getRecipeManager() {
        return recipeManager;
    }
}
