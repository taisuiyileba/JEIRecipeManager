package com.jeirecipemanager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.jeirecipemanager.mixin.IngredientFilterApiAccessor;
import com.mojang.serialization.JsonOps;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
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
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

@JeiPlugin
public class JeiRecipeManagerPlugin implements IModPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("JEIRecipeManager");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type CATEGORY_CACHE_TYPE = new TypeToken<Map<String, Set<String>>>(){}.getType();
    private static final Path CATEGORY_CACHE_PATH = FMLPaths.GAMEDIR.get()
        .resolve("config")
        .resolve(Jeirecipemanager.MODID)
        .resolve("jeirecipemanager_recipe_categories.json");
    private static IJeiRuntime jeiRuntime;
    private static boolean showDisabledRecipes = true;
    private static AppliedVisibilityState appliedVisibilityState;
    private static final Map<String, Set<ResourceLocation>> knownRecipeCategoryUids = new HashMap<>();
    private static boolean loadedRecipeCategoryCache = false;

    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath("jeirecipemanager", "main");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        JeiRecipeManagerPlugin.jeiRuntime = jeiRuntime;
        RecipeManagerState.setRecipeManager(jeiRuntime.getRecipeManager());
        loadRecipeCategoryCache();
        updateRecipeVisibility();
        collectGeneratedRecipeOutputs();
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(mezz.jei.gui.recipes.RecipesGui.class, new RecipeGhostIngredientHandler());
    }


    public static void updateRecipeVisibility() {
        if (jeiRuntime == null) {
            return;
        }
        AppliedVisibilityState currentState = AppliedVisibilityState.current(jeiRuntime, showDisabledRecipes);
        if (currentState.equals(appliedVisibilityState)) {
            return;
        }
        rememberVisibleRecipeCategories(jeiRuntime.getRecipeManager());
        removeInjectedDisabledRecipesFromJei();
        injectDisabledRecipesIntoJei();
        if(showDisabledRecipes){
            unhideAllRecipesInJei();
        }else {
            hideDisabledRecipesInJei();
        }
        collectGeneratedRecipeOutputs();
        rebuildIngredientFilter();
        appliedVisibilityState = currentState;
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
            List<RecipeType<?>> jeiTypes = findJeiRecipeTypes(recipeManager, holder);
            if (jeiTypes.isEmpty()) {
                LOGGER.debug("No JEI category found for recipe: {}", recipeId);
                continue;
            }

            for (RecipeType<?> jeiType : jeiTypes) {
                recipesByType.computeIfAbsent(jeiType, k -> new ArrayList<>()).add(holder);
            }
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

    @SuppressWarnings("unchecked")
    private static void collectGeneratedRecipeOutputs() {
        if (jeiRuntime == null) {
            return;
        }

        IRecipeManager recipeManager = jeiRuntime.getRecipeManager();
        Set<ResourceLocation> generatedRecipeOutputs = new HashSet<>();

        List<IRecipeCategory<?>> categories = recipeManager.createRecipeCategoryLookup().get().toList();
        for (IRecipeCategory<?> category : categories) {
            RecipeType<?> recipeType = category.getRecipeType();
            recipeManager.createRecipeLookup((RecipeType) recipeType).includeHidden().get().forEach(recipe -> {
                ResourceLocation registryName = ((IRecipeCategory) category).getRegistryName(recipe);
                if (registryName != null && GeneratedRecipesManager.isGeneratedRecipeId(registryName.toString())) {
                    if (recipe instanceof RecipeHolder<?> holder) {
                        getRecipeResultItemId(holder.value()).ifPresent(generatedRecipeOutputs::add);
                    }
                }
            });
        }

        DisabledRecipesManager.setClientGeneratedRecipeOutputs(generatedRecipeOutputs);
        LOGGER.info("Collected {} generated recipe outputs for + search", generatedRecipeOutputs.size());
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

    private static void loadRecipeCategoryCache() {
        if (loadedRecipeCategoryCache) {
            return;
        }
        loadedRecipeCategoryCache = true;
        if (!Files.exists(CATEGORY_CACHE_PATH)) {
            return;
        }
        try {
            Map<String, Set<String>> loaded = GSON.fromJson(Files.readString(CATEGORY_CACHE_PATH), CATEGORY_CACHE_TYPE);
            if (loaded == null) {
                return;
            }
            knownRecipeCategoryUids.clear();
            for (var entry : loaded.entrySet()) {
                Set<ResourceLocation> categoryUids = new HashSet<>();
                for (String categoryUid : entry.getValue()) {
                    ResourceLocation parsed = ResourceLocation.tryParse(categoryUid);
                    if (parsed != null) {
                        categoryUids.add(parsed);
                    }
                }
                if (!categoryUids.isEmpty()) {
                    knownRecipeCategoryUids.put(entry.getKey(), categoryUids);
                }
            }
            LOGGER.info("Loaded JEI category cache for {} recipes", knownRecipeCategoryUids.size());
        } catch (IOException e) {
            LOGGER.warn("Failed to load JEI recipe category cache", e);
        }
    }

    private static void saveRecipeCategoryCache(Map<String, Set<ResourceLocation>> cache) {
        try {
            Map<String, Set<String>> serialized = new TreeMap<>();
            for (var entry : cache.entrySet()) {
                Set<String> categoryUids = new TreeSet<>();
                for (ResourceLocation categoryUid : entry.getValue()) {
                    categoryUids.add(categoryUid.toString());
                }
                if (!categoryUids.isEmpty()) {
                    serialized.put(entry.getKey(), categoryUids);
                }
            }
            Files.createDirectories(CATEGORY_CACHE_PATH.getParent());
            Files.writeString(CATEGORY_CACHE_PATH, GSON.toJson(serialized));
        } catch (IOException e) {
            LOGGER.warn("Failed to save JEI recipe category cache", e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void rememberVisibleRecipeCategories(IRecipeManager recipeManager) {
        Map<String, Set<ResourceLocation>> visibleRecipeCategoryUids = new HashMap<>();
        List<IRecipeCategory<?>> categories = recipeManager.createRecipeCategoryLookup().get().toList();
        for (IRecipeCategory<?> category : categories) {
            RecipeType<?> recipeType = category.getRecipeType();
            recipeManager.createRecipeLookup((RecipeType) recipeType)
                .includeHidden()
                .get()
                .forEach(recipe -> {
                    ResourceLocation registryName = ((IRecipeCategory) category).getRegistryName(recipe);
                    if (registryName != null) {
                        visibleRecipeCategoryUids
                            .computeIfAbsent(registryName.toString(), key -> new HashSet<>())
                            .add(recipeType.getUid());
                    }
                });
        }

        Set<String> disabledRecipes = DisabledRecipesManager.getDisabledRecipes();
        Map<String, Set<ResourceLocation>> persistedRecipeCategoryUids = new HashMap<>();
        for (String recipeId : disabledRecipes) {
            Set<ResourceLocation> currentUids = visibleRecipeCategoryUids.get(recipeId);
            if (currentUids != null && !currentUids.isEmpty()) {
                persistedRecipeCategoryUids.put(recipeId, new HashSet<>(currentUids));
                continue;
            }

            Set<ResourceLocation> cachedUids = knownRecipeCategoryUids.get(recipeId);
            if (cachedUids != null && !cachedUids.isEmpty()) {
                persistedRecipeCategoryUids.put(recipeId, new HashSet<>(cachedUids));
            }
        }

        knownRecipeCategoryUids.clear();
        knownRecipeCategoryUids.putAll(visibleRecipeCategoryUids);
        for (var entry : persistedRecipeCategoryUids.entrySet()) {
            knownRecipeCategoryUids.putIfAbsent(entry.getKey(), new HashSet<>(entry.getValue()));
        }

        if (DisabledRecipesManager.hasClientRecipeStateSynced() || !disabledRecipes.isEmpty()) {
            saveRecipeCategoryCache(persistedRecipeCategoryUids);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<RecipeType<?>> findJeiRecipeTypes(IRecipeManager recipeManager, RecipeHolder<?> holder) {
        ResourceLocation holderId = holder.id();
        List<IRecipeCategory<?>> categories = recipeManager.createRecipeCategoryLookup().get().toList();
        List<RecipeType<?>> knownTypes = findKnownJeiRecipeTypes(categories, holderId);
        if (!knownTypes.isEmpty()) {
            return knownTypes;
        }

        net.minecraft.world.item.crafting.RecipeType<?> mcType = holder.value().getType();
        ResourceLocation mcTypeId = BuiltInRegistries.RECIPE_TYPE.getKey(mcType);
        List<RecipeType<?>> directTypes = new ArrayList<>();
        if (mcTypeId != null) {
            for (IRecipeCategory<?> category : categories) {
                if (category.getRecipeType().getUid().equals(mcTypeId) && acceptsRecipeHolder(category, holder)) {
                    directTypes.add(category.getRecipeType());
                }
            }
        }
        if (!directTypes.isEmpty()) {
            return directTypes;
        }

        List<RecipeType<?>> compatibleTypes = new ArrayList<>();
        for (IRecipeCategory<?> category : categories) {
            if (!acceptsRecipeHolder(category, holder)) {
                continue;
            }
            if (categoryContainsMinecraftRecipeType(recipeManager, category, mcType)) {
                compatibleTypes.add(category.getRecipeType());
            }
        }
        return compatibleTypes;
    }

    private static List<RecipeType<?>> findKnownJeiRecipeTypes(List<IRecipeCategory<?>> categories, ResourceLocation recipeId) {
        Set<ResourceLocation> knownUids = knownRecipeCategoryUids.get(recipeId.toString());
        if (knownUids == null || knownUids.isEmpty()) {
            return List.of();
        }

        List<RecipeType<?>> result = new ArrayList<>();
        for (IRecipeCategory<?> category : categories) {
            if (knownUids.contains(category.getRecipeType().getUid())) {
                result.add(category.getRecipeType());
            }
        }
        return result;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean acceptsRecipeHolder(IRecipeCategory<?> category, RecipeHolder<?> holder) {
        if (!category.getRecipeType().getRecipeClass().isInstance(holder)) {
            return false;
        }
        try {
            return ((IRecipeCategory) category).isHandled(holder);
        } catch (Exception e) {
            LOGGER.debug("JEI category {} rejected recipe holder {}", category.getRecipeType().getUid(), holder.id(), e);
            return false;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean categoryContainsMinecraftRecipeType(
        IRecipeManager recipeManager,
        IRecipeCategory<?> category,
        net.minecraft.world.item.crafting.RecipeType<?> mcType
    ) {
        RecipeType<?> recipeType = category.getRecipeType();
        try {
            return recipeManager.createRecipeLookup((RecipeType) recipeType)
                .includeHidden()
                .get()
                .filter(RecipeHolder.class::isInstance)
                .anyMatch(existing -> ((RecipeHolder<?>) existing).value().getType() == mcType);
        } catch (Exception e) {
            LOGGER.debug("Failed to inspect JEI recipes in category {}", recipeType.getUid(), e);
            return false;
        }
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

    @SuppressWarnings({"unchecked", "rawtypes"})
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

    private record AppliedVisibilityState(int runtimeIdentity, boolean showDisabledRecipes, Set<String> disabledRecipes, Map<String, String> recipeJsonCache) {
        private static AppliedVisibilityState current(IJeiRuntime runtime, boolean showDisabledRecipes) {
            return new AppliedVisibilityState(
                System.identityHashCode(runtime),
                showDisabledRecipes,
                DisabledRecipesManager.getDisabledRecipes(),
                DisabledRecipesManager.getClientRecipeJsonCache()
            );
        }
    }
}
