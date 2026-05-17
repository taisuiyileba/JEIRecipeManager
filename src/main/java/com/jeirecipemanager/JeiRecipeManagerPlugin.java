package com.jeirecipemanager;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.jeirecipemanager.mixin.IngredientFilterApiAccessor;
import com.mojang.serialization.JsonOps;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@JeiPlugin
public class JeiRecipeManagerPlugin implements IModPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("JEIRecipeManager");
    private static IJeiRuntime jeiRuntime;
    private static boolean showDisabledRecipes = true;

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


    public static void updateRecipeVisibility() {
        if (jeiRuntime == null) {
            return;
        }
        removeInjectedDisabledRecipesFromJei();
        injectDisabledRecipesIntoJei();
        if(showDisabledRecipes){
            unhideAllRecipesInJei();
        }else {
            hideDisabledRecipesInJei();
        }
        rebuildIngredientFilter();
    }

    public static void setShowDisabledRecipes(boolean value) {
        showDisabledRecipes = value;
        if(showDisabledRecipes){
            unhideAllRecipesInJei();
        }else {
            hideDisabledRecipesInJei();
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void injectDisabledRecipesIntoJei() {
        if (jeiRuntime == null) {
            return;
        }

        IRecipeManager recipeManager = jeiRuntime.getRecipeManager();
        Map<String, String> recipeJsonMap = DisabledRecipesManager.getClientRecipeJsonCache();
        Set<ResourceLocation> disabledRecipeOutputs = new HashSet<>();

        if (recipeJsonMap.isEmpty()) {
            DisabledRecipesManager.setClientDisabledRecipeOutputs(disabledRecipeOutputs);
            return;
        }

        Map<RecipeType<?>, List<RecipeHolder<?>>> recipesByType = new HashMap<>();

        for (var entry : recipeJsonMap.entrySet()) {
            String recipeId = entry.getKey();
            String recipeJson = entry.getValue();

            ResourceLocation id = ResourceLocation.tryParse(recipeId);
            if (id == null) {
                LOGGER.warn("Invalid recipe ID: {}", recipeId);
                continue;
            }

            JsonElement jsonElement;
            try {
                jsonElement = JsonParser.parseString(recipeJson);
            } catch (Exception e) {
                LOGGER.error("Failed to parse JSON for recipe: {}", recipeId, e);
                continue;
            }

            Recipe<?> recipe;
            try {
                recipe = Recipe.CODEC.parse(JsonOps.INSTANCE, jsonElement).getOrThrow();
            } catch (Exception e) {
                LOGGER.error("Failed to deserialize recipe: {}", recipeId, e);
                continue;
            }

            RecipeHolder<?> holder = new RecipeHolder<>(id, recipe);
            getRecipeResultItemId(recipe).ifPresent(disabledRecipeOutputs::add);
            RecipeType<?> jeiType = findJeiRecipeType(recipeManager, holder);
            if (jeiType == null) {
                LOGGER.debug("No JEI category found for recipe: {}", recipeId);
                continue;
            }

            recipesByType.computeIfAbsent(jeiType, k -> new ArrayList<>()).add(holder);
        }

        DisabledRecipesManager.setClientDisabledRecipeOutputs(disabledRecipeOutputs);

        for (var entry : recipesByType.entrySet()) {
            RecipeType recipeType = entry.getKey();
            List<RecipeHolder<?>> recipes = entry.getValue();
            try {
                recipeManager.addRecipes(recipeType, recipes);
                for (RecipeHolder<?> holder : recipes) {
                    DisabledRecipesManager.addClientInjectedRecipe(recipeType, holder);
                }
                LOGGER.info("Injected {} disabled recipes into JEI category {}", recipes.size(), recipeType.getUid());
            } catch (Exception e) {
                LOGGER.error("Failed to inject recipes into JEI category {}", recipeType.getUid(), e);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void removeInjectedDisabledRecipesFromJei() {
        if (jeiRuntime == null) {
            return;
        }

        IRecipeManager recipeManager = jeiRuntime.getRecipeManager();
        List<DisabledRecipesManager.InjectedRecipe> injectedRecipes = DisabledRecipesManager.getClientInjectedRecipes();

        if (injectedRecipes.isEmpty()) {
            return;
        }

        Map<RecipeType<?>, List<RecipeHolder<?>>> recipesByType = new HashMap<>();
        for (DisabledRecipesManager.InjectedRecipe injected : injectedRecipes) {
            recipesByType.computeIfAbsent(injected.jeiType(), k -> new ArrayList<>()).add(injected.holder());
        }

        for (var entry : recipesByType.entrySet()) {
            RecipeType recipeType = entry.getKey();
            List<RecipeHolder<?>> recipes = entry.getValue();
            try {
                recipeManager.hideRecipes(recipeType, recipes);
                LOGGER.info("Removed {} injected recipes from JEI category {}", recipes.size(), recipeType.getUid());
            } catch (Exception e) {
                LOGGER.error("Failed to remove injected recipes from JEI category {}", recipeType.getUid(), e);
            }
        }

        DisabledRecipesManager.clearClientInjectedRecipes();
    }

    private static void rebuildIngredientFilter() {
        try {
            if (jeiRuntime.getIngredientFilter() instanceof IngredientFilterApiAccessor accessor) {
                accessor.jeirecipemanager_getIngredientFilter().rebuildItemFilter();
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to rebuild JEI ingredient filter", e);
        }
    }

    private static Optional<ResourceLocation> getRecipeResultItemId(Recipe<?> recipe) {
        try {
            Level level = net.minecraft.client.Minecraft.getInstance().level;
            ItemStack result = recipe.getResultItem(level != null ? level.registryAccess() : net.minecraft.core.RegistryAccess.EMPTY);
            if (result.isEmpty()) {
                return Optional.empty();
            }
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(result.getItem());
            return itemId != null ? Optional.of(itemId) : Optional.empty();
        } catch (Exception e) {
            LOGGER.debug("Failed to get result item for disabled recipe", e);
            return Optional.empty();
        }
    }

    private static RecipeType<?> findJeiRecipeType(IRecipeManager recipeManager, RecipeHolder<?> holder) {
        net.minecraft.world.item.crafting.RecipeType<?> mcType = holder.value().getType();
        ResourceLocation mcTypeId = BuiltInRegistries.RECIPE_TYPE.getKey(mcType);
        if (mcTypeId == null) {
            return null;
        }

        List<IRecipeCategory<?>> categories = recipeManager.createRecipeCategoryLookup().get().toList();
        for (IRecipeCategory<?> category : categories) {
            if (category.getRecipeType().getUid().equals(mcTypeId)) {
                return category.getRecipeType();
            }
        }
        return null;
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