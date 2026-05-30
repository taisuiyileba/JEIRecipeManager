package com.jeirecipemanager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;

public class DisabledRecipesManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("JEIRecipeManager");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type SET_TYPE = new TypeToken<Set<String>>(){}.getType();

    private static final CopyOnWriteArraySet<String> disabledRecipes = new CopyOnWriteArraySet<>();
    private static Path configPath;
    private static boolean serverInitialized = false;

    private static final Map<String, String> serverRecipeJsonCache = new ConcurrentHashMap<>();
    private static final Map<String, String> serverAllRecipeJsonCache = new ConcurrentHashMap<>();
    private static final Map<String, String> clientRecipeJsonCache = new ConcurrentHashMap<>();
    private static final CopyOnWriteArraySet<ResourceLocation> clientDisabledRecipeOutputs = new CopyOnWriteArraySet<>();
    private static final CopyOnWriteArraySet<ResourceLocation> clientGeneratedRecipeOutputs = new CopyOnWriteArraySet<>();
    private static final List<InjectedRecipe> clientInjectedRecipes = new CopyOnWriteArrayList<>();
    private static volatile boolean clientRecipeStateSynced = false;

    public static void serverInit() {
        if (!serverInitialized) {
            configPath = FMLPaths.GAMEDIR.get().resolve("config").resolve(Jeirecipemanager.MODID).resolve("disabled_recipes.json");
            load();
            serverInitialized = true;
            LOGGER.info("Server initialized DisabledRecipesManager with {} disabled recipes", disabledRecipes.size());
        }
    }

    private static void load() {
        if (configPath != null && Files.exists(configPath)) {
            try {
                String json = Files.readString(configPath);
                Set<String> loaded = GSON.fromJson(json, SET_TYPE);
                if (loaded != null) {
                    disabledRecipes.clear();
                    disabledRecipes.addAll(loaded);
                }
                LOGGER.info("Loaded {} disabled recipes", disabledRecipes.size());
            } catch (IOException e) {
                LOGGER.error("Failed to load disabled recipes config", e);
            }
        }
    }

    public static void save() {
        if (configPath == null) {
            LOGGER.error("Cannot save disabled recipes: config path is null");
            return;
        }
        try {
            String json = GSON.toJson(disabledRecipes);
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, json);
            LOGGER.info("Saved {} disabled recipes", disabledRecipes.size());
        } catch (IOException e) {
            LOGGER.error("Failed to save disabled recipes config", e);
        }
    }

    public static void serverReload() {
        if (serverInitialized) {
            load();
            LOGGER.info("Reloaded config: {} disabled recipes", disabledRecipes.size());
        }
    }

    public static boolean isRecipeDisabled(String recipeId) {
        return disabledRecipes.contains(recipeId);
    }

    public static void serverDisableRecipe(String recipeId) {
        disabledRecipes.add(recipeId);
        save();
        LOGGER.info("Disabled recipe: {}", recipeId);
    }

    public static void serverEnableRecipe(String recipeId) {
        disabledRecipes.remove(recipeId);
        save();
        LOGGER.info("Enabled recipe: {}", recipeId);
    }

    public static Set<String> getDisabledRecipes() {
        return new HashSet<>(disabledRecipes);
    }

    public static void clientUpdateDisabledRecipes(List<String> recipes, Map<String, String> recipeJsonMap) {
        disabledRecipes.clear();
        disabledRecipes.addAll(recipes);
        clientRecipeJsonCache.clear();
        clientRecipeJsonCache.putAll(recipeJsonMap);
        clientRecipeStateSynced = true;
        LOGGER.info("Client received {} disabled recipes from server ({} with JSON data)",
            disabledRecipes.size(), recipeJsonMap.size());
    }

    public static boolean hasClientRecipeStateSynced() {
        return clientRecipeStateSynced;
    }

    public static Map<String, String> getClientRecipeJsonCache() {
        return new HashMap<>(clientRecipeJsonCache);
    }

    public static void setClientDisabledRecipeOutputs(Set<ResourceLocation> outputs) {
        clientDisabledRecipeOutputs.clear();
        clientDisabledRecipeOutputs.addAll(outputs);
    }

    public static Set<ResourceLocation> getClientDisabledRecipeOutputs() {
        return new HashSet<>(clientDisabledRecipeOutputs);
    }

    public static boolean isClientDisabledRecipeOutput(ResourceLocation output) {
        return clientDisabledRecipeOutputs.contains(output);
    }

    public static void setClientGeneratedRecipeOutputs(Set<ResourceLocation> outputs) {
        clientGeneratedRecipeOutputs.clear();
        clientGeneratedRecipeOutputs.addAll(outputs);
    }

    public static Set<ResourceLocation> getClientGeneratedRecipeOutputs() {
        return new HashSet<>(clientGeneratedRecipeOutputs);
    }

    public static boolean isClientGeneratedRecipeOutput(ResourceLocation output) {
        return clientGeneratedRecipeOutputs.contains(output);
    }

    public static void serverCacheRecipeJson(String recipeId, String recipeJson) {
        serverRecipeJsonCache.put(recipeId, recipeJson);
    }

    public static void serverCacheAllRecipeJson(Map<String, String> recipes) {
        serverAllRecipeJsonCache.clear();
        serverAllRecipeJsonCache.putAll(recipes);
    }

    public static String getServerRecipeJson(String recipeId) {
        String disabledJson = serverRecipeJsonCache.get(recipeId);
        if (disabledJson != null) {
            return disabledJson;
        }
        return serverAllRecipeJsonCache.get(recipeId);
    }

    public static Map<String, String> getServerRecipeJsonCache() {
        return new HashMap<>(serverRecipeJsonCache);
    }

    public static void addClientInjectedRecipe(mezz.jei.api.recipe.RecipeType<?> jeiType, RecipeHolder<?> holder) {
        clientInjectedRecipes.add(new InjectedRecipe(jeiType, holder));
    }

    public static List<InjectedRecipe> getClientInjectedRecipes() {
        return new ArrayList<>(clientInjectedRecipes);
    }

    public static void clearClientInjectedRecipes() {
        clientInjectedRecipes.clear();
    }

    public record InjectedRecipe(mezz.jei.api.recipe.RecipeType<?> jeiType, RecipeHolder<?> holder) {}
}
