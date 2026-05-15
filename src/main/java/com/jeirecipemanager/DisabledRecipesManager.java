package com.jeirecipemanager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DisabledRecipesManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("JEIRecipeManager");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type SET_TYPE = new TypeToken<Set<String>>(){}.getType();

    private static final Set<String> disabledRecipes = new HashSet<>();
    private static final Map<String, String> recipeCategoryMap = new HashMap<>();
    private static Path configPath;
    private static boolean initialized = false;

    private static void ensureInitialized() {
        if (!initialized) {
            try {
                Path gameDir = Minecraft.getInstance().gameDirectory.toPath();
                configPath = gameDir.resolve("config").resolve("disabled_recipes.json");
                load();
                initialized = true;
            } catch (Exception e) {
                LOGGER.error("Failed to initialize DisabledRecipesManager", e);
            }
        }
    }

    public static void init(Path configDir) {
        if (!initialized) {
            configPath = configDir.resolve("disabled_recipes.json");
            load();
            initialized = true;
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
        ensureInitialized();
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

    public static void reload() {
        ensureInitialized();
        load();
        LOGGER.info("Reloaded config: {} disabled recipes", disabledRecipes.size());
    }

    public static boolean isRecipeDisabled(String recipeId) {
        ensureInitialized();
        return disabledRecipes.contains(recipeId);
    }

    public static void disableRecipe(String recipeId, String categoryUid) {
        ensureInitialized();
        disabledRecipes.add(recipeId);
        if (categoryUid != null) {
            recipeCategoryMap.put(recipeId, categoryUid);
        }
        save();
    }

    public static void enableRecipe(String recipeId) {
        ensureInitialized();
        disabledRecipes.remove(recipeId);
        recipeCategoryMap.remove(recipeId);
        save();
    }

    public static Set<String> getDisabledRecipes() {
        ensureInitialized();
        return new HashSet<>(disabledRecipes);
    }

    public static boolean isCategoryFullyDisabled(String categoryUid) {
        ensureInitialized();
        if (categoryUid == null || disabledRecipes.isEmpty()) {
            return false;
        }
        for (Map.Entry<String, String> entry : recipeCategoryMap.entrySet()) {
            if (categoryUid.equals(entry.getValue()) && disabledRecipes.contains(entry.getKey())) {
                return true;
            }
        }
        return false;
    }

    public static void rebuildCategoryMap(Map<String, String> recipeToCategory) {
        recipeCategoryMap.clear();
        for (Map.Entry<String, String> entry : recipeToCategory.entrySet()) {
            if (disabledRecipes.contains(entry.getKey())) {
                recipeCategoryMap.put(entry.getKey(), entry.getValue());
            }
        }
        LOGGER.info("Rebuilt category map with {} entries", recipeCategoryMap.size());
    }
}