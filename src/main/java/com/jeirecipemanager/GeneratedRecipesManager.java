package com.jeirecipemanager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class GeneratedRecipesManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("JEIRecipeManager");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String PACK_DIR = "jeirecipemanager_generated";
    private static final String PACK_ID = "file/" + PACK_DIR;

    public static Result addRecipeFromTemplate(String templateRecipeId, List<RecipeEditManager.SlotReplacement> replacements) {
        String templateJson = DisabledRecipesManager.getServerRecipeJson(templateRecipeId);
        if (templateJson == null || templateJson.isBlank()) {
            LOGGER.warn("Cannot add recipe from template {}, JSON was not cached", templateRecipeId);
            return Result.failure();
        }

        ResourceLocation templateId = ResourceLocation.tryParse(templateRecipeId);
        if (templateId == null) {
            return Result.failure();
        }

        JsonObject recipeJson = JsonParser.parseString(templateJson).getAsJsonObject().deepCopy();
        if (!applyReplacements(recipeJson, replacements)) {
            LOGGER.warn("No replacements were applied for template {}", templateRecipeId);
            return Result.failure();
        }

        boolean modifyingGeneratedRecipe = isGeneratedRecipeId(templateRecipeId);
        ResourceLocation targetId = modifyingGeneratedRecipe
            ? templateId
            : ResourceLocation.fromNamespaceAndPath(
                Jeirecipemanager.MODID,
                "generated/" + sanitize(templateId.getNamespace()) + "/" + sanitize(templateId.getPath()) + "_" + System.currentTimeMillis()
            );

        try {
            Path recipePath = getRecipePath(targetId);
            Files.createDirectories(recipePath.getParent());
            Files.writeString(recipePath, GSON.toJson(recipeJson));
            ensurePackMcmeta();
            reloadWithGeneratedPack();
            if (modifyingGeneratedRecipe) {
                LOGGER.info("Modified generated recipe {}", targetId);
            } else {
                LOGGER.info("Generated new recipe {} from template {}", targetId, templateRecipeId);
            }
            return Result.success(modifyingGeneratedRecipe);
        } catch (IOException e) {
            LOGGER.error("Failed to write generated recipe from template {}", templateRecipeId, e);
            return Result.failure();
        }
    }

    public static boolean isGeneratedRecipeId(String recipeId) {
        ResourceLocation id = ResourceLocation.tryParse(recipeId);
        return id != null && id.getNamespace().equals(Jeirecipemanager.MODID) && id.getPath().startsWith("generated/");
    }

    public static boolean deleteGeneratedRecipe(String recipeId) {
        if (!isGeneratedRecipeId(recipeId)) {
            return false;
        }
        ResourceLocation id = ResourceLocation.tryParse(recipeId);
        if (id == null) {
            return false;
        }
        try {
            Path recipePath = getRecipePath(id);
            boolean deleted = Files.deleteIfExists(recipePath);
            if (deleted) {
                reloadWithGeneratedPack();
                LOGGER.info("Deleted generated recipe {}", recipeId);
            } else {
                LOGGER.warn("Generated recipe file did not exist: {}", recipePath);
            }
            return deleted;
        } catch (IOException e) {
            LOGGER.error("Failed to delete generated recipe {}", recipeId, e);
            return false;
        }
    }

    private static boolean applyReplacements(JsonObject recipeJson, List<RecipeEditManager.SlotReplacement> replacements) {
        boolean changed = false;
        for (RecipeEditManager.SlotReplacement replacement : replacements) {
            boolean output = "OUTPUT".equals(replacement.role());
            boolean input = "INPUT".equals(replacement.role());
            if (!output && !input) {
                continue;
            }

            if (output) {
                changed |= applyOutput(recipeJson, replacement);
            } else {
                changed |= applyInput(recipeJson, replacement);
            }
        }
        return changed;
    }

    private static boolean applyInput(JsonObject recipeJson, RecipeEditManager.SlotReplacement replacement) {
        String type = getType(recipeJson);
        if (type.endsWith("crafting_shaped")) {
            return applyShapedInput(recipeJson, replacement.slotIndex(), ingredientJson(replacement.itemId()));
        }
        if (type.endsWith("crafting_shapeless")) {
            return applyShapelessInput(recipeJson, replacement.slotIndex(), ingredientJson(replacement.itemId()));
        }
        if (isCooking(type)) {
            recipeJson.add("ingredient", ingredientJson(replacement.itemId()));
            return true;
        }
        return applyGenericInput(recipeJson, replacement.slotIndex(), ingredientJson(replacement.itemId()));
    }

    private static boolean applyOutput(JsonObject recipeJson, RecipeEditManager.SlotReplacement replacement) {
        JsonObject itemStack = itemStackJson(replacement.itemId(), replacement.count());
        if (recipeJson.has("result")) {
            recipeJson.add("result", itemStack);
            return true;
        }
        if (recipeJson.has("results") && recipeJson.get("results").isJsonArray()) {
            JsonArray results = recipeJson.getAsJsonArray("results");
            if (replacement.slotIndex() >= 0 && replacement.slotIndex() < results.size()) {
                results.set(replacement.slotIndex(), itemStack);
                return true;
            }
        }
        return false;
    }

    private static boolean applyShapedInput(JsonObject recipeJson, int gridIndex, JsonObject ingredient) {
        if (!recipeJson.has("pattern") || !recipeJson.has("key")) {
            return false;
        }
        JsonArray pattern = recipeJson.getAsJsonArray("pattern");
        int height = pattern.size();
        int width = height == 0 ? 0 : pattern.get(0).getAsString().length();
        int ingredientIndex = -1;
        for (int i = 0; i < width * height; i++) {
            if (getCraftingIndex(i, width, height) == gridIndex) {
                ingredientIndex = i;
                break;
            }
        }
        if (ingredientIndex < 0) {
            return false;
        }

        int row = ingredientIndex / width;
        int col = ingredientIndex % width;
        String rowText = pattern.get(row).getAsString();
        if (col >= rowText.length()) {
            return false;
        }
        JsonObject key = recipeJson.getAsJsonObject("key");
        char keyChar = nextKey(key);
        StringBuilder builder = new StringBuilder(rowText);
        builder.setCharAt(col, keyChar);
        pattern.set(row, new com.google.gson.JsonPrimitive(builder.toString()));
        key.add(String.valueOf(keyChar), ingredient);
        removeUnusedKeys(recipeJson);
        return true;
    }

    private static boolean applyShapelessInput(JsonObject recipeJson, int gridIndex, JsonObject ingredient) {
        if (!recipeJson.has("ingredients") || !recipeJson.get("ingredients").isJsonArray()) {
            return false;
        }
        JsonArray ingredients = recipeJson.getAsJsonArray("ingredients");
        int size = getShapelessSize(ingredients.size());
        for (int i = 0; i < ingredients.size(); i++) {
            if (getCraftingIndex(i, size, size) == gridIndex) {
                ingredients.set(i, ingredient);
                return true;
            }
        }
        return false;
    }

    private static boolean applyGenericInput(JsonObject recipeJson, int slotIndex, JsonObject ingredient) {
        if (recipeJson.has("ingredients") && recipeJson.get("ingredients").isJsonArray()) {
            JsonArray ingredients = recipeJson.getAsJsonArray("ingredients");
            if (slotIndex >= 0 && slotIndex < ingredients.size()) {
                ingredients.set(slotIndex, ingredient);
                return true;
            }
        }
        if (slotIndex == 0 && recipeJson.has("ingredient")) {
            recipeJson.add("ingredient", ingredient);
            return true;
        }
        return false;
    }

    private static JsonObject ingredientJson(String itemId) {
        JsonObject object = new JsonObject();
        object.addProperty("item", itemId);
        return object;
    }

    private static JsonObject itemStackJson(String itemId, int count) {
        JsonObject object = new JsonObject();
        object.addProperty("id", itemId);
        if (count > 1) {
            object.addProperty("count", count);
        }
        return object;
    }

    private static String getType(JsonObject recipeJson) {
        JsonElement type = recipeJson.get("type");
        return type == null ? "" : type.getAsString();
    }

    private static boolean isCooking(String type) {
        return type.endsWith("smelting") ||
            type.endsWith("blasting") ||
            type.endsWith("smoking") ||
            type.endsWith("campfire_cooking");
    }

    private static char nextKey(JsonObject key) {
        for (char c = 'A'; c <= 'Z'; c++) {
            if (!key.has(String.valueOf(c))) {
                return c;
            }
        }
        for (char c = 'a'; c <= 'z'; c++) {
            if (!key.has(String.valueOf(c))) {
                return c;
            }
        }
        return '#';
    }

    private static void removeUnusedKeys(JsonObject recipeJson) {
        if (!recipeJson.has("pattern") || !recipeJson.has("key")) {
            return;
        }
        Set<String> usedKeys = new LinkedHashSet<>();
        for (JsonElement rowElement : recipeJson.getAsJsonArray("pattern")) {
            String row = rowElement.getAsString();
            for (int i = 0; i < row.length(); i++) {
                char c = row.charAt(i);
                if (c != ' ') {
                    usedKeys.add(String.valueOf(c));
                }
            }
        }
        JsonObject key = recipeJson.getAsJsonObject("key");
        List<String> definedKeys = new java.util.ArrayList<>(key.keySet());
        for (String definedKey : definedKeys) {
            if (!usedKeys.contains(definedKey)) {
                key.remove(definedKey);
            }
        }
    }

    private static int getShapelessSize(int total) {
        if (total > 4) {
            return 3;
        } else if (total > 1) {
            return 2;
        }
        return 1;
    }

    private static int getCraftingIndex(int i, int width, int height) {
        if (width == 1) {
            if (height == 3 || height == 2) {
                return (i * 3) + 1;
            }
            return 4;
        } else if (height == 1) {
            return i + 3;
        } else if (width == 2) {
            int index = i;
            if (i > 1) {
                index++;
                if (i > 3) {
                    index++;
                }
            }
            return index;
        } else if (height == 2) {
            return i + 3;
        }
        return i;
    }

    private static Path getRecipePath(ResourceLocation recipeId) {
        return getDatapacksPath()
            .resolve(PACK_DIR)
            .resolve("data")
            .resolve(recipeId.getNamespace())
            .resolve("recipe")
            .resolve(recipeId.getPath() + ".json");
    }

    private static void ensurePackMcmeta() throws IOException {
        Path packPath = getDatapacksPath().resolve(PACK_DIR);
        Files.createDirectories(packPath);
        migrateLegacyPack(packPath);
        Path mcmeta = packPath.resolve("pack.mcmeta");
        if (!Files.exists(mcmeta)) {
            JsonObject root = new JsonObject();
            JsonObject pack = new JsonObject();
            pack.addProperty("pack_format", 48);
            pack.addProperty("description", "Generated recipes from JEI Recipe Manager");
            root.add("pack", pack);
            Files.writeString(mcmeta, GSON.toJson(root));
        }
    }

    private static void reloadWithGeneratedPack() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        server.getPackRepository().reload();
        Set<String> selected = new LinkedHashSet<>((Collection<String>) server.getPackRepository().getSelectedIds());
        if (server.getPackRepository().isAvailable(PACK_ID)) {
            selected.add(PACK_ID);
        }
        server.reloadResources(selected);
    }

    private static Path getDatapacksPath() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            return server.getWorldPath(LevelResource.DATAPACK_DIR);
        }
        return FMLPaths.GAMEDIR.get().resolve("datapacks");
    }

    private static void migrateLegacyPack(Path targetPackPath) {
        Path legacyPackPath = FMLPaths.GAMEDIR.get().resolve("datapacks").resolve(PACK_DIR);
        if (legacyPackPath.equals(targetPackPath) || !Files.isDirectory(legacyPackPath)) {
            return;
        }
        try (var paths = Files.walk(legacyPackPath)) {
            paths.filter(Files::isRegularFile).forEach(source -> {
                Path relative = legacyPackPath.relativize(source);
                Path target = targetPackPath.resolve(relative);
                try {
                    Files.createDirectories(target.getParent());
                    if (!Files.exists(target)) {
                        Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
                    }
                } catch (IOException e) {
                    LOGGER.warn("Failed to migrate generated recipe file {}", source, e);
                }
            });
        } catch (IOException e) {
            LOGGER.warn("Failed to migrate legacy generated datapack from {}", legacyPackPath, e);
        }
    }

    private static String sanitize(String value) {
        return value.replaceAll("[^a-z0-9_./-]", "_");
    }

    public record Result(boolean success, boolean modified) {
        private static Result success(boolean modified) {
            return new Result(true, modified);
        }

        private static Result failure() {
            return new Result(false, false);
        }
    }
}
