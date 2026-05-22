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
    private static final String KEY_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!#$%&()*+,-./:;<=>?@[]^_`{|}~";

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
        String type = getType(recipeJson);
        if (isShapedRecipe(recipeJson, type)) {
            changed |= applyShapedInputs(recipeJson, replacements.stream()
                .filter(replacement -> "INPUT".equals(replacement.role()))
                .toList());
            replacements = replacements.stream()
                .filter(replacement -> !"INPUT".equals(replacement.role()))
                .toList();
        }

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
        if (isShapelessRecipe(recipeJson, type)) {
            return applyShapelessInput(recipeJson, replacement);
        }
        if (isCooking(type)) {
            if (replacement.clearsSlot()) {
                return recipeJson.remove("ingredient") != null;
            }
            recipeJson.add("ingredient", ingredientJson(replacement.itemId()));
            return true;
        }
        return applyGenericInput(recipeJson, replacement);
    }

    private static boolean applyOutput(JsonObject recipeJson, RecipeEditManager.SlotReplacement replacement) {
        if (replacement.clearsSlot()) {
            return false;
        }
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

    private static boolean applyShapedInputs(JsonObject recipeJson, List<RecipeEditManager.SlotReplacement> replacements) {
        if (replacements.isEmpty()) {
            return false;
        }
        if (!recipeJson.has("pattern") || !recipeJson.has("key")) {
            return false;
        }
        JsonArray pattern = recipeJson.getAsJsonArray("pattern");
        int height = pattern.size();
        int width = height == 0 ? 0 : pattern.get(0).getAsString().length();
        GridSize gridSize = determineGridSize(width, height, replacements);
        JsonObject originalKey = recipeJson.getAsJsonObject("key");
        JsonElement[] grid = new JsonElement[gridSize.width() * gridSize.height()];
        int rowOffset = Math.max(0, (gridSize.height() - height) / 2);
        int colOffset = Math.max(0, (gridSize.width() - width) / 2);
        for (int i = 0; i < width * height; i++) {
            int row = i / width;
            int col = i % width;
            String rowText = pattern.get(row).getAsString();
            if (col >= rowText.length()) {
                continue;
            }
            char keyChar = rowText.charAt(col);
            if (keyChar == ' ') {
                continue;
            }
            JsonElement ingredient = originalKey.get(String.valueOf(keyChar));
            if (ingredient != null && !ingredient.isJsonNull()) {
                int gridIndex = (row + rowOffset) * gridSize.width() + col + colOffset;
                if (gridIndex >= 0 && gridIndex < grid.length) {
                    grid[gridIndex] = ingredient.deepCopy();
                }
            }
        }

        boolean changed = false;
        for (RecipeEditManager.SlotReplacement replacement : replacements) {
            int gridIndex = replacement.slotIndex();
            if (gridIndex < 0 || gridIndex >= grid.length) {
                continue;
            }
            grid[gridIndex] = replacement.clearsSlot() ? null : ingredientJson(replacement.itemId());
            changed = true;
        }
        if (!changed) {
            return false;
        }

        int minRow = gridSize.height();
        int minCol = gridSize.width();
        int maxRow = -1;
        int maxCol = -1;
        for (int i = 0; i < grid.length; i++) {
            if (grid[i] == null) {
                continue;
            }
            int row = i / gridSize.width();
            int col = i % gridSize.width();
            minRow = Math.min(minRow, row);
            minCol = Math.min(minCol, col);
            maxRow = Math.max(maxRow, row);
            maxCol = Math.max(maxCol, col);
        }
        if (maxRow < 0 || maxCol < 0) {
            return false;
        }

        JsonArray newPattern = new JsonArray();
        JsonObject newKey = new JsonObject();
        int nextKeyIndex = 0;
        for (int row = minRow; row <= maxRow; row++) {
            StringBuilder rowPattern = new StringBuilder();
            for (int col = minCol; col <= maxCol; col++) {
                JsonElement ingredient = grid[row * gridSize.width() + col];
                if (ingredient == null) {
                    rowPattern.append(' ');
                    continue;
                }
                if (nextKeyIndex >= KEY_CHARS.length()) {
                    LOGGER.warn("Cannot encode shaped recipe with more than {} occupied inputs", KEY_CHARS.length());
                    return false;
                }
                String keyName = String.valueOf(KEY_CHARS.charAt(nextKeyIndex++));
                rowPattern.append(keyName);
                newKey.add(keyName, ingredient);
            }
            newPattern.add(rowPattern.toString());
        }
        recipeJson.add("pattern", newPattern);
        recipeJson.add("key", newKey);
        return true;
    }

    private static boolean applyShapelessInput(JsonObject recipeJson, RecipeEditManager.SlotReplacement replacement) {
        if (!recipeJson.has("ingredients") || !recipeJson.get("ingredients").isJsonArray()) {
            return false;
        }
        JsonArray ingredients = recipeJson.getAsJsonArray("ingredients");
        int gridIndex = replacement.slotIndex();
        if (gridIndex >= 0 && gridIndex < ingredients.size()) {
            if (replacement.clearsSlot()) {
                ingredients.remove(gridIndex);
            } else {
                ingredients.set(gridIndex, ingredientJson(replacement.itemId()));
            }
            return true;
        }
        return false;
    }

    private static boolean applyGenericInput(JsonObject recipeJson, RecipeEditManager.SlotReplacement replacement) {
        if (recipeJson.has("ingredients") && recipeJson.get("ingredients").isJsonArray()) {
            JsonArray ingredients = recipeJson.getAsJsonArray("ingredients");
            int slotIndex = replacement.slotIndex();
            if (slotIndex >= 0 && slotIndex < ingredients.size()) {
                if (replacement.clearsSlot()) {
                    ingredients.remove(slotIndex);
                } else {
                    ingredients.set(slotIndex, ingredientJson(replacement.itemId()));
                }
                return true;
            }
        }
        if (replacement.slotIndex() == 0 && recipeJson.has("ingredient")) {
            if (replacement.clearsSlot()) {
                recipeJson.remove("ingredient");
            } else {
                recipeJson.add("ingredient", ingredientJson(replacement.itemId()));
            }
            return true;
        }
        return false;
    }

    private static JsonObject ingredientJson(String itemId) {
        JsonObject object = new JsonObject();
        if (itemId.startsWith("#")) {
            object.addProperty("tag", itemId.substring(1));
        } else {
            object.addProperty("item", itemId);
        }
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

    private static boolean isShapedRecipe(JsonObject recipeJson, String type) {
        return (type.endsWith("crafting_shaped") || recipeJson.has("pattern")) &&
            recipeJson.has("pattern") &&
            recipeJson.has("key");
    }

    private static boolean isShapelessRecipe(JsonObject recipeJson, String type) {
        return (type.endsWith("crafting_shapeless") || type.contains("shapeless")) &&
            recipeJson.has("ingredients") &&
            recipeJson.get("ingredients").isJsonArray();
    }

    private static GridSize determineGridSize(int patternWidth, int patternHeight, List<RecipeEditManager.SlotReplacement> replacements) {
        int width = Math.max(3, patternWidth);
        int height = Math.max(3, patternHeight);
        for (RecipeEditManager.SlotReplacement replacement : replacements) {
            if (replacement.gridWidth() > 0 && replacement.gridHeight() > 0) {
                width = Math.max(width, replacement.gridWidth());
                height = Math.max(height, replacement.gridHeight());
            }
        }
        return new GridSize(width, height);
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

    private record GridSize(int width, int height) {}
}
