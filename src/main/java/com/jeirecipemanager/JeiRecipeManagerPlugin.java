package com.jeirecipemanager;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@JeiPlugin
public class JeiRecipeManagerPlugin implements IModPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("JEIRecipeManager");

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath("jeirecipemanager", "main");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        IRecipeManager recipeManager = jeiRuntime.getRecipeManager();
        RecipeManagerState.setRecipeManager(recipeManager);

        Set<String> disabledRecipes = DisabledRecipesManager.getDisabledRecipes();

        if (disabledRecipes.isEmpty()) {
            return;
        }

        LOGGER.info("Found {} disabled recipes in config", disabledRecipes.size());

        Map<String, String> recipeToCategory = new HashMap<>();

        List<IRecipeCategory<?>> categories = recipeManager.createRecipeCategoryLookup().get().toList();
        for (IRecipeCategory<?> category : categories) {
            RecipeType<?> recipeType = category.getRecipeType();
            String categoryUid = recipeType.getUid().toString();

            recipeManager.createRecipeLookup((RecipeType) recipeType).get().forEach(recipe -> {
                ResourceLocation registryName = ((IRecipeCategory) category).getRegistryName(recipe);
                if (registryName != null) {
                    String recipeId = registryName.toString();
                    recipeToCategory.put(recipeId, categoryUid);
                }
            });
        }

        DisabledRecipesManager.rebuildCategoryMap(recipeToCategory);
        LOGGER.info("Category map rebuilt with {} entries", recipeToCategory.size());
    }
}