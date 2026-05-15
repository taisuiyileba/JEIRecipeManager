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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@JeiPlugin
public class JeiRecipeManagerPlugin implements IModPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("JEIRecipeManager");
    private static IJeiRuntime jeiRuntime;
    private static boolean showDisabledRecipes = false; // 控制是否显示禁用配方

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath("jeirecipemanager", "main");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        JeiRecipeManagerPlugin.jeiRuntime = jeiRuntime;
        RecipeManagerState.setRecipeManager(jeiRuntime.getRecipeManager());
        updateRecipeVisibility();
    }
    
    /**
     * 更新配方可见性（根据 showDisabledRecipes 状态）
     */
    public static void updateRecipeVisibility() {
        if (showDisabledRecipes) {
            unhideAllRecipesInJei();
            LOGGER.info("已启用显示禁用配方");
        } else {
            unhideAllRecipesInJei();
            hideDisabledRecipesInJei();
            LOGGER.info("已禁用显示禁用配方");
        }
    }
    
    /**
     * 切换显示/隐藏禁用配方的状态
     */
    public static void toggleShowDisabledRecipes() {
        showDisabledRecipes = !showDisabledRecipes;
        updateRecipeVisibility();
    }
    
    /**
     * 获取当前是否显示禁用配方
     */
    public static boolean isShowDisabledRecipes() {
        return showDisabledRecipes;
    }

    @SuppressWarnings("unchecked")
    public static void hideDisabledRecipesInJei() {
        if (jeiRuntime == null) {
            return;
        }

        IRecipeManager recipeManager = jeiRuntime.getRecipeManager();
        Set<String> disabledRecipes = DisabledRecipesManager.getDisabledRecipes();

        if (disabledRecipes.isEmpty()) {
            return;
        }

        LOGGER.info("Hiding {} disabled recipes in JEI", disabledRecipes.size());

        List<IRecipeCategory<?>> categories = recipeManager.createRecipeCategoryLookup().get().toList();
        for (IRecipeCategory<?> category : categories) {
            RecipeType<?> recipeType = category.getRecipeType();
            List<Object> recipesToHide = new ArrayList<>();

            recipeManager.createRecipeLookup((RecipeType) recipeType).get().forEach(recipe -> {
                ResourceLocation registryName = ((IRecipeCategory) category).getRegistryName(recipe);
                if (registryName != null && disabledRecipes.contains(registryName.toString())) {
                    recipesToHide.add(recipe);
                }
            });

            if (!recipesToHide.isEmpty()) {
                try {
                    recipeManager.hideRecipes((RecipeType) recipeType, recipesToHide);
                    LOGGER.info("Hidden {} recipes in category {}", recipesToHide.size(), recipeType.getUid());
                } catch (Exception e) {
                    LOGGER.error("Failed to hide recipes in category {}", recipeType.getUid(), e);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static void unhideAllRecipesInJei() {
        if (jeiRuntime == null) {
            return;
        }

        IRecipeManager recipeManager = jeiRuntime.getRecipeManager();
        List<IRecipeCategory<?>> categories = recipeManager.createRecipeCategoryLookup().get().toList();
        for (IRecipeCategory<?> category : categories) {
            RecipeType<?> recipeType = category.getRecipeType();
            List<Object> allRecipes = new ArrayList<>();
            // 使用 includeHidden() 获取包括隐藏的配方
            recipeManager.createRecipeLookup((RecipeType) recipeType)
                .includeHidden()
                .get()
                .forEach(allRecipes::add);
            if (!allRecipes.isEmpty()) {
                try {
                    recipeManager.unhideRecipes((RecipeType) recipeType, allRecipes);
                } catch (Exception e) {
                    LOGGER.error("Failed to unhide recipes in category {}", recipeType.getUid(), e);
                }
            }
        }
    }
}
