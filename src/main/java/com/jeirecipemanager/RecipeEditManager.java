package com.jeirecipemanager;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jeirecipemanager.network.NetworkHandler;
import com.mojang.serialization.JsonOps;
import com.mojang.datafixers.util.Pair;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class RecipeEditManager {
    private static final Map<String, Draft> drafts = new LinkedHashMap<>();
    private static String activeRecipeId;

    public static boolean isEditing(String recipeId) {
        return Objects.equals(activeRecipeId, recipeId);
    }

    public static boolean hasDraft(String recipeId) {
        Draft draft = drafts.get(recipeId);
        return draft != null && !draft.replacements().isEmpty();
    }

    public static void toggleEditing(String recipeId) {
        if (Objects.equals(activeRecipeId, recipeId)) {
            activeRecipeId = null;
        } else {
            activeRecipeId = recipeId;
            drafts.computeIfAbsent(recipeId, Draft::new);
        }
    }

    public static void replaceSlot(String recipeId, List<IRecipeSlotView> slots, IRecipeSlotDrawable targetSlot, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }

        SlotAddress address = findSlotAddress(slots, targetSlot).orElse(null);
        if (address == null || !isSupportedRole(address.role())) {
            return;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId == null) {
            return;
        }

        Draft draft = drafts.computeIfAbsent(recipeId, Draft::new);
        draft.replacements().put(address.key(), new SlotReplacement(
            address.role().name(),
            address.roleIndex(),
            itemId.toString(),
            Math.max(1, stack.getCount()),
            address.gridWidth(),
            address.gridHeight()
        ));

        applyReplacementToSlot(targetSlot, draft.replacements().get(address.key()));
    }

    public static void replaceInputSlotText(String recipeId, List<IRecipeSlotView> slots, IRecipeSlotDrawable targetSlot, String inputText) {
        String normalized = normalizeInputText(inputText);
        if (normalized == null) {
            return;
        }

        SlotAddress address = findSlotAddress(slots, targetSlot).orElse(null);
        if (address == null || address.role() != RecipeIngredientRole.INPUT) {
            return;
        }

        Draft draft = drafts.computeIfAbsent(recipeId, Draft::new);
        draft.replacements().put(address.key(), new SlotReplacement(
            address.role().name(),
            address.roleIndex(),
            normalized,
            1,
            address.gridWidth(),
            address.gridHeight()
        ));

        applyReplacementToSlot(targetSlot, draft.replacements().get(address.key()));
    }

    public static boolean isValidInputText(String inputText) {
        return normalizeInputText(inputText) != null;
    }

    public static boolean clearSlot(String recipeId, List<IRecipeSlotView> slots, IRecipeSlotDrawable targetSlot) {
        SlotAddress address = findSlotAddress(slots, targetSlot).orElse(null);
        if (address == null || !isSupportedRole(address.role())) {
            return false;
        }

        Draft existingDraft = drafts.get(recipeId);
        if (targetSlot.isEmpty() && (existingDraft == null || !existingDraft.replacements().containsKey(address.key()))) {
            return true;
        }

        Draft draft = drafts.computeIfAbsent(recipeId, Draft::new);
        draft.replacements().put(address.key(), new SlotReplacement(
            address.role().name(),
            address.roleIndex(),
            "",
            1,
            address.gridWidth(),
            address.gridHeight()
        ));

        applyReplacementToSlot(targetSlot, draft.replacements().get(address.key()));
        return true;
    }

    public static Optional<String> getInputSlotText(String recipeId, Object recipeObject, List<IRecipeSlotView> slots, IRecipeSlotDrawable targetSlot) {
        SlotAddress address = findSlotAddress(slots, targetSlot).orElse(null);
        if (address == null || address.role() != RecipeIngredientRole.INPUT) {
            return Optional.empty();
        }

        Draft draft = drafts.get(recipeId);
        if (draft != null) {
            SlotReplacement replacement = draft.replacements().get(address.key());
            if (replacement != null) {
                return Optional.of(replacement.itemId());
            }
        }

        return getInputTextFromRecipeJson(recipeId, recipeObject, address)
            .or(() -> getInputTextFromSlotTag(targetSlot))
            .or(() -> targetSlot.getDisplayedItemStack().map(RecipeEditManager::itemIdText));
    }

    public static void applyDraftToLayout(IRecipeLayoutDrawable<?> layout) {
        ResourceLocation id = ((mezz.jei.api.recipe.category.IRecipeCategory) layout.getRecipeCategory()).getRegistryName(layout.getRecipe());
        if (id == null) {
            return;
        }
        Draft draft = drafts.get(id.toString());
        if (draft == null || draft.replacements().isEmpty()) {
            return;
        }

        List<IRecipeSlotView> slots = layout.getRecipeSlotsView().getSlotViews();
        for (IRecipeSlotView slotView : slots) {
            if (!(slotView instanceof IRecipeSlotDrawable slot)) {
                continue;
            }
            SlotAddress address = findSlotAddress(slots, slot).orElse(null);
            if (address == null) {
                continue;
            }
            SlotReplacement replacement = draft.replacements().get(address.key());
            if (replacement != null) {
                applyReplacementToSlot(slot, replacement);
            }
        }
    }

    public static void submit(String recipeId) {
        Draft draft = drafts.get(recipeId);
        if (draft == null || draft.replacements().isEmpty()) {
            activeRecipeId = recipeId;
            drafts.computeIfAbsent(recipeId, Draft::new);
            return;
        }

        NetworkHandler.sendRecipeAdd(recipeId, new ArrayList<>(draft.replacements().values()));
        drafts.remove(recipeId);
        activeRecipeId = null;
    }

    public static void clear(String recipeId) {
        drafts.remove(recipeId);
        if (Objects.equals(activeRecipeId, recipeId)) {
            activeRecipeId = null;
        }
    }

    private static Optional<SlotAddress> findSlotAddress(List<IRecipeSlotView> slots, IRecipeSlotDrawable targetSlot) {
        GridSize inputGridSize = inferInputGridSize(slots);
        int inputIndex = 0;
        int outputIndex = 0;
        for (IRecipeSlotView slot : slots) {
            RecipeIngredientRole role = slot.getRole();
            int roleIndex = switch (role) {
                case INPUT -> inputIndex++;
                case OUTPUT -> outputIndex++;
                default -> -1;
            };

            if (slot == targetSlot) {
                GridSize gridSize = role == RecipeIngredientRole.INPUT ? inputGridSize : GridSize.UNKNOWN;
                return Optional.of(new SlotAddress(role, roleIndex, gridSize.width(), gridSize.height()));
            }
        }
        return Optional.empty();
    }

    private static Optional<String> getInputTextFromRecipeJson(String recipeId, Object recipeObject, SlotAddress address) {
        String recipeJson = getRecipeJson(recipeId, recipeObject);
        if (recipeJson == null || recipeJson.isBlank()) {
            return Optional.empty();
        }

        JsonObject recipe;
        try {
            recipe = JsonParser.parseString(recipeJson).getAsJsonObject();
        } catch (Exception ignored) {
            return Optional.empty();
        }

        if (recipe.has("pattern") && recipe.has("key")) {
            JsonArray pattern = recipe.getAsJsonArray("pattern");
            int patternHeight = pattern.size();
            int patternWidth = patternHeight == 0 ? 0 : pattern.get(0).getAsString().length();
            int gridWidth = address.gridWidth() > 0 ? address.gridWidth() : Math.max(3, patternWidth);
            int gridHeight = address.gridHeight() > 0 ? address.gridHeight() : Math.max(3, patternHeight);
            int rowOffset = Math.max(0, (gridHeight - patternHeight) / 2);
            int colOffset = Math.max(0, (gridWidth - patternWidth) / 2);
            int row = address.roleIndex() / gridWidth - rowOffset;
            int col = address.roleIndex() % gridWidth - colOffset;
            if (row < 0 || row >= patternHeight) {
                return Optional.empty();
            }
            String rowText = pattern.get(row).getAsString();
            if (col < 0 || col >= rowText.length()) {
                return Optional.empty();
            }
            char keyChar = rowText.charAt(col);
            if (keyChar == ' ') {
                return Optional.empty();
            }
            JsonElement ingredient = recipe.getAsJsonObject("key").get(String.valueOf(keyChar));
            return ingredientText(ingredient);
        }

        if (recipe.has("ingredients") && recipe.get("ingredients").isJsonArray()) {
            JsonArray ingredients = recipe.getAsJsonArray("ingredients");
            if (address.roleIndex() >= 0 && address.roleIndex() < ingredients.size()) {
                return ingredientText(ingredients.get(address.roleIndex()));
            }
        }

        return Optional.empty();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String getRecipeJson(String recipeId, Object recipeObject) {
        String cachedJson = DisabledRecipesManager.getClientRecipeJsonCache().get(recipeId);
        if (cachedJson != null) {
            return cachedJson;
        }
        Recipe<?> recipe = null;
        if (recipeObject instanceof RecipeHolder<?> holder) {
            recipe = holder.value();
        } else if (recipeObject instanceof Recipe<?> directRecipe) {
            recipe = directRecipe;
        }
        if (recipe == null) {
            return null;
        }
        try {
            JsonElement encoded = Recipe.CODEC.encodeStart(JsonOps.INSTANCE, (Recipe) recipe).getOrThrow();
            return encoded.toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Optional<String> ingredientText(JsonElement ingredient) {
        if (ingredient == null || !ingredient.isJsonObject()) {
            return Optional.empty();
        }
        JsonObject object = ingredient.getAsJsonObject();
        if (object.has("tag")) {
            return Optional.of("#" + object.get("tag").getAsString());
        }
        if (object.has("item")) {
            return Optional.of(object.get("item").getAsString());
        }
        return Optional.empty();
    }

    private static Optional<String> getInputTextFromSlotTag(IRecipeSlotView slot) {
        List<ItemStack> stacks = slot.getItemStacks().toList();
        if (stacks.size() <= 1) {
            return Optional.empty();
        }

        List<Item> items = stacks.stream()
            .map(ItemStack::getItem)
            .toList();

        return BuiltInRegistries.ITEM.getTags()
            .filter(tagEntry -> tagMatchesItems(tagEntry.getSecond(), items))
            .map(Pair::getFirst)
            .map(tagKey -> "#" + tagKey.location())
            .findFirst();
    }

    private static boolean tagMatchesItems(HolderSet.Named<Item> tag, List<Item> items) {
        if (tag.size() != items.size()) {
            return false;
        }
        for (int i = 0; i < tag.size(); i++) {
            if (!tag.get(i).value().equals(items.get(i))) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("removal")
    private static GridSize inferInputGridSize(List<IRecipeSlotView> slots) {
        List<Rect2i> inputRects = slots.stream()
            .filter(slot -> slot.getRole() == RecipeIngredientRole.INPUT)
            .filter(IRecipeSlotDrawable.class::isInstance)
            .map(IRecipeSlotDrawable.class::cast)
            .map(IRecipeSlotDrawable::getRect)
            .sorted(Comparator.comparingInt(Rect2i::getY).thenComparingInt(Rect2i::getX))
            .toList();
        if (inputRects.isEmpty()) {
            return GridSize.UNKNOWN;
        }

        int columns = countDistinctPositions(inputRects.stream().map(Rect2i::getX).sorted().toList());
        int rows = countDistinctPositions(inputRects.stream().map(Rect2i::getY).sorted().toList());
        if (columns <= 0 || rows <= 0 || columns * rows < inputRects.size()) {
            return GridSize.UNKNOWN;
        }
        return new GridSize(columns, rows);
    }

    private static int countDistinctPositions(List<Integer> positions) {
        int count = 0;
        Integer previous = null;
        for (Integer position : positions) {
            if (previous == null || Math.abs(position - previous) > 2) {
                count++;
                previous = position;
            }
        }
        return count;
    }

    private static boolean isSupportedRole(RecipeIngredientRole role) {
        return role == RecipeIngredientRole.INPUT || role == RecipeIngredientRole.OUTPUT;
    }

    private static void applyReplacementToSlot(IRecipeSlotDrawable slot, SlotReplacement replacement) {
        if (replacement.clearsSlot()) {
            slot.clearDisplayOverrides();
            slot.createDisplayOverrides().addIngredients(VanillaTypes.ITEM_STACK, Collections.singletonList(null));
            return;
        }

        if (replacement.itemId().startsWith("#")) {
            List<ItemStack> stacks = getTagStacks(replacement.itemId().substring(1), Math.max(1, replacement.count()));
            if (!stacks.isEmpty()) {
                slot.clearDisplayOverrides();
                slot.createDisplayOverrides().addIngredients(VanillaTypes.ITEM_STACK, stacks);
            }
            return;
        }

        ResourceLocation itemId = ResourceLocation.tryParse(replacement.itemId());
        if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) {
            return;
        }
        ItemStack displayStack = new ItemStack(BuiltInRegistries.ITEM.get(itemId), Math.max(1, replacement.count()));
        slot.clearDisplayOverrides();
        slot.createDisplayOverrides().addIngredient(VanillaTypes.ITEM_STACK, displayStack);
    }

    private static List<ItemStack> getTagStacks(String tagIdText, int count) {
        ResourceLocation tagId = ResourceLocation.tryParse(tagIdText);
        if (tagId == null) {
            return List.of();
        }
        TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagId);
        return BuiltInRegistries.ITEM.getTag(tagKey)
            .map(tag -> tag.stream()
                .map(holder -> new ItemStack(holder.value(), count))
                .toList())
            .orElse(List.of());
    }

    private static String itemIdText(ItemStack stack) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return itemId == null ? "" : itemId.toString();
    }

    private static String normalizeInputText(String inputText) {
        String value = inputText == null ? "" : inputText.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.startsWith("#")) {
            ResourceLocation tagId = ResourceLocation.tryParse(value.substring(1));
            return tagId == null ? null : "#" + tagId;
        }
        ResourceLocation itemId = ResourceLocation.tryParse(value);
        if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) {
            return null;
        }
        return itemId.toString();
    }

    public record SlotReplacement(String role, int slotIndex, String itemId, int count, int gridWidth, int gridHeight) {
        public boolean clearsSlot() {
            return itemId == null || itemId.isBlank();
        }
    }

    private record Draft(String recipeId, Map<String, SlotReplacement> replacements) {
        private Draft(String recipeId) {
            this(recipeId, new LinkedHashMap<>());
        }
    }

    private record SlotAddress(RecipeIngredientRole role, int roleIndex, int gridWidth, int gridHeight) {
        private String key() {
            return role.name() + ":" + roleIndex;
        }
    }

    private record GridSize(int width, int height) {
        private static final GridSize UNKNOWN = new GridSize(0, 0);
    }
}
