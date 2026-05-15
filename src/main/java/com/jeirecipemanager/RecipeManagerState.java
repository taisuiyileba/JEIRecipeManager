package com.jeirecipemanager;

import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.gui.recipes.RecipesGui;

public class RecipeManagerState {
    private static RecipesGui recipesGuiInstance;
    private static IRecipeManager recipeManager;

    public static void setRecipesGuiInstance(RecipesGui gui) {
        recipesGuiInstance = gui;
    }

    public static RecipesGui getRecipesGuiInstance() {
        return recipesGuiInstance;
    }

    public static void setRecipeManager(IRecipeManager manager) {
        recipeManager = manager;
    }

    public static IRecipeManager getRecipeManager() {
        return recipeManager;
    }
}