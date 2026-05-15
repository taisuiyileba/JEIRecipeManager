package com.jeirecipemanager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class DisabledRecipesManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("JEIRecipeManager");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type SET_TYPE = new TypeToken<Set<String>>(){}.getType();

    private static final CopyOnWriteArraySet<String> disabledRecipes = new CopyOnWriteArraySet<>();
    private static Path configPath;
    private static boolean serverInitialized = false;

    public static void serverInit() {
        if (!serverInitialized) {
            configPath = FMLPaths.GAMEDIR.get().resolve("config").resolve("disabled_recipes.json");
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

    public static void clientUpdateDisabledRecipes(List<String> recipes) {
        disabledRecipes.clear();
        disabledRecipes.addAll(recipes);
        LOGGER.info("Client received {} disabled recipes from server", disabledRecipes.size());
    }
}
