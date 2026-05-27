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
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

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

    public enum IngredientKind {
        ITEM,
        FLUID
    }

    public record IngredientEditValue(IngredientKind kind, String ingredientId, int amount, String detailId) {
        public IngredientEditValue(IngredientKind kind, String ingredientId, int amount) {
            this(kind, ingredientId, amount, "");
        }

        public boolean hasPotionDetail() {
            return kind == IngredientKind.FLUID && !detailId.isBlank();
        }
    }

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
            address.gridHeight(),
            IngredientKind.ITEM.name()
        ));

        applyReplacementToSlot(targetSlot, draft.replacements().get(address.key()));
    }

    public static void replaceSlot(String recipeId, List<IRecipeSlotView> slots, IRecipeSlotDrawable targetSlot, FluidStack stack) {
        if (stack.isEmpty()) {
            return;
        }

        SlotAddress address = findSlotAddress(slots, targetSlot).orElse(null);
        if (address == null || !isSupportedRole(address.role())) {
            return;
        }

        ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(stack.getFluid());
        if (fluidId == null) {
            return;
        }

        Draft draft = drafts.computeIfAbsent(recipeId, Draft::new);
        draft.replacements().put(address.key(), new SlotReplacement(
            address.role().name(),
            address.roleIndex(),
            fluidId.toString(),
            Math.max(1, stack.getAmount()),
            address.gridWidth(),
            address.gridHeight(),
            IngredientKind.FLUID.name(),
            getPotionId(stack).orElse("")
        ));

        applyReplacementToSlot(targetSlot, draft.replacements().get(address.key()));
    }

    public static void replaceInputSlotText(String recipeId, List<IRecipeSlotView> slots, IRecipeSlotDrawable targetSlot, String inputText) {
        replaceSlotText(recipeId, slots, targetSlot, inputText, 1, "");
    }

    public static void replaceInputSlotText(String recipeId, List<IRecipeSlotView> slots, IRecipeSlotDrawable targetSlot, String inputText, int amount) {
        replaceSlotText(recipeId, slots, targetSlot, inputText, amount, "");
    }

    public static void replaceSlotText(String recipeId, List<IRecipeSlotView> slots, IRecipeSlotDrawable targetSlot, String inputText, int amount, String detailText) {
        IngredientKind kind = getSlotIngredientKind(recipeId, null, slots, targetSlot);
        String normalized = normalizeInputText(inputText, kind);
        if (normalized == null) {
            return;
        }
        String normalizedDetail = normalizeDetailText(detailText, normalized, kind);
        if (normalizedDetail == null) {
            return;
        }

        SlotAddress address = findSlotAddress(slots, targetSlot).orElse(null);
        if (address == null || !isSupportedRole(address.role())) {
            return;
        }

        Draft draft = drafts.computeIfAbsent(recipeId, Draft::new);
        draft.replacements().put(address.key(), new SlotReplacement(
            address.role().name(),
            address.roleIndex(),
            normalized,
            kind == IngredientKind.FLUID ? Math.max(1, amount) : 1,
            address.gridWidth(),
            address.gridHeight(),
            kind.name(),
            normalizedDetail
        ));

        applyReplacementToSlot(targetSlot, draft.replacements().get(address.key()));
    }

    public static boolean isValidInputText(String inputText) {
        return isValidInputText(inputText, IngredientKind.ITEM);
    }

    public static boolean isValidInputText(String inputText, IngredientKind kind) {
        return normalizeInputText(inputText, kind) != null;
    }

    public static boolean isValidDetailText(String detailText, String ingredientId, IngredientKind kind) {
        String normalized = normalizeInputText(ingredientId, kind);
        return normalized != null && normalizeDetailText(detailText, normalized, kind) != null;
    }

    public static boolean canClearSlots(String recipeId, Object recipeObject) {
        String recipeJson = getRecipeJson(recipeId, recipeObject);
        if (recipeJson == null || recipeJson.isBlank()) {
            return false;
        }

        try {
            JsonObject recipe = JsonParser.parseString(recipeJson).getAsJsonObject();
            return isCraftingTableRecipe(recipe);
        } catch (Exception ignored) {
            return false;
        }
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
        IngredientKind kind = getSlotIngredientKind(recipeId, null, slots, targetSlot);
        draft.replacements().put(address.key(), new SlotReplacement(
            address.role().name(),
            address.roleIndex(),
            "",
            1,
            address.gridWidth(),
            address.gridHeight(),
            kind.name()
        ));

        applyReplacementToSlot(targetSlot, draft.replacements().get(address.key()));
        return true;
    }

    public static Optional<String> getInputSlotText(String recipeId, Object recipeObject, List<IRecipeSlotView> slots, IRecipeSlotDrawable targetSlot) {
        return getSlotEditValue(recipeId, recipeObject, slots, targetSlot)
            .map(IngredientEditValue::ingredientId);
    }

    public static Optional<IngredientEditValue> getInputSlotEditValue(String recipeId, Object recipeObject, List<IRecipeSlotView> slots, IRecipeSlotDrawable targetSlot) {
        return getSlotEditValue(recipeId, recipeObject, slots, targetSlot)
            .filter(value -> findSlotAddress(slots, targetSlot)
                .map(address -> address.role() == RecipeIngredientRole.INPUT)
                .orElse(false));
    }

    public static Optional<IngredientEditValue> getSlotEditValue(String recipeId, Object recipeObject, List<IRecipeSlotView> slots, IRecipeSlotDrawable targetSlot) {
        SlotAddress address = findSlotAddress(slots, targetSlot).orElse(null);
        if (address == null || !isSupportedRole(address.role())) {
            return Optional.empty();
        }

        IngredientKind kind = getSlotIngredientKind(recipeId, recipeObject, slots, targetSlot);
        Draft draft = drafts.get(recipeId);
        if (draft != null) {
            SlotReplacement replacement = draft.replacements().get(address.key());
            if (replacement != null) {
                return Optional.of(new IngredientEditValue(replacement.kind(), replacement.itemId(), Math.max(1, replacement.count()), replacement.extraId()));
            }
        }

        return getSlotEditValueFromRecipeJson(recipeId, recipeObject, address, kind)
            .or(() -> getInputEditValueFromSlotTag(targetSlot, kind))
            .or(() -> getDisplayedInputEditValue(targetSlot, kind));
    }

    public static IngredientKind getSlotIngredientKind(String recipeId, Object recipeObject, List<IRecipeSlotView> slots, IRecipeSlotDrawable targetSlot) {
        if (targetSlot.getIngredients(NeoForgeTypes.FLUID_STACK).findAny().isPresent()) {
            return IngredientKind.FLUID;
        }
        if (targetSlot.getItemStacks().findAny().isPresent()) {
            return IngredientKind.ITEM;
        }

        SlotAddress address = findSlotAddress(slots, targetSlot).orElse(null);
        if (address != null && isSupportedRole(address.role())) {
            Optional<IngredientKind> jsonKind = getSlotKindFromRecipeJson(recipeId, recipeObject, address);
            if (jsonKind.isPresent()) {
                return jsonKind.get();
            }
        }
        return IngredientKind.ITEM;
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

    public static void clearAll() {
        drafts.clear();
        activeRecipeId = null;
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

    private static Optional<IngredientKind> getSlotKindFromRecipeJson(String recipeId, Object recipeObject, SlotAddress address) {
        return getSlotEditValueFromRecipeJson(recipeId, recipeObject, address, null)
            .map(IngredientEditValue::kind);
    }

    private static Optional<IngredientEditValue> getSlotEditValueFromRecipeJson(String recipeId, Object recipeObject, SlotAddress address, IngredientKind preferredKind) {
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

        if (address.role() == RecipeIngredientRole.OUTPUT) {
            return getOutputEditValueFromRecipeJson(recipe, address, preferredKind);
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
            return ingredientEditValue(ingredient, preferredKind);
        }

        if (recipe.has("ingredients") && recipe.get("ingredients").isJsonArray()) {
            JsonArray ingredients = recipe.getAsJsonArray("ingredients");
            if (address.roleIndex() >= 0 && address.roleIndex() < ingredients.size()) {
                return ingredientEditValue(ingredients.get(address.roleIndex()), preferredKind);
            }
        }

        return Optional.empty();
    }

    private static Optional<IngredientEditValue> getOutputEditValueFromRecipeJson(JsonObject recipe, SlotAddress address, IngredientKind preferredKind) {
        if (recipe.has("result") && address.roleIndex() == 0) {
            return resultEditValue(recipe.get("result"), preferredKind);
        }

        if (recipe.has("results") && recipe.get("results").isJsonArray()) {
            JsonArray results = recipe.getAsJsonArray("results");
            if (address.roleIndex() >= 0 && address.roleIndex() < results.size()) {
                return resultEditValue(results.get(address.roleIndex()), preferredKind);
            }
        }
        return Optional.empty();
    }

    private static boolean isCraftingTableRecipe(JsonObject recipe) {
        String type = recipe.has("type") ? recipe.get("type").getAsString() : "";
        return (type.endsWith("crafting_shaped") || type.endsWith("crafting_shapeless")) ||
            (recipe.has("pattern") && recipe.has("key"));
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

    private static Optional<IngredientEditValue> ingredientEditValue(JsonElement ingredient, IngredientKind preferredKind) {
        if (ingredient == null || !ingredient.isJsonObject()) {
            return Optional.empty();
        }
        JsonObject object = ingredient.getAsJsonObject();

        IngredientKind kind = preferredKind != null ? preferredKind : inferIngredientKind(object).orElse(IngredientKind.ITEM);
        if (kind == IngredientKind.FLUID) {
            Optional<String> fluidId = fluidIngredientIdText(object);
            if (fluidId.isPresent()) {
                return Optional.of(new IngredientEditValue(
                    IngredientKind.FLUID,
                    fluidId.get(),
                    ingredientAmount(object),
                    potionIdFromCreatePotionIngredient(object).orElse("")
                ));
            }
            return Optional.empty();
        }

        if (object.has("tag")) {
            return Optional.of(new IngredientEditValue(IngredientKind.ITEM, "#" + object.get("tag").getAsString(), 1));
        }
        if (object.has("item")) {
            return Optional.of(new IngredientEditValue(IngredientKind.ITEM, object.get("item").getAsString(), 1));
        }
        return Optional.empty();
    }

    private static Optional<IngredientEditValue> resultEditValue(JsonElement result, IngredientKind preferredKind) {
        if (result == null || !result.isJsonObject()) {
            return Optional.empty();
        }
        JsonObject object = result.getAsJsonObject();
        IngredientKind kind = preferredKind != null ? preferredKind : inferResultKind(object).orElse(IngredientKind.ITEM);
        if (kind == IngredientKind.FLUID) {
            Optional<String> fluidId = fluidIngredientIdText(object);
            return fluidId.map(id -> new IngredientEditValue(
                IngredientKind.FLUID,
                id,
                ingredientAmount(object),
                potionIdFromCreatePotionIngredient(object).orElse("")
            ));
        }
        if (object.has("id")) {
            return Optional.of(new IngredientEditValue(IngredientKind.ITEM, object.get("id").getAsString(), resultCount(object)));
        }
        if (object.has("item")) {
            return Optional.of(new IngredientEditValue(IngredientKind.ITEM, object.get("item").getAsString(), resultCount(object)));
        }
        return Optional.empty();
    }

    private static Optional<IngredientKind> inferIngredientKind(JsonObject object) {
        if (object.has("item")) {
            return Optional.of(IngredientKind.ITEM);
        }
        if (object.has("fluid") || object.has("fluids") || object.has("amount")) {
            return Optional.of(IngredientKind.FLUID);
        }
        return Optional.empty();
    }

    private static Optional<IngredientKind> inferResultKind(JsonObject object) {
        if (object.has("fluid") || object.has("fluids") || object.has("amount")) {
            return Optional.of(IngredientKind.FLUID);
        }
        if (object.has("id") || object.has("item")) {
            return Optional.of(IngredientKind.ITEM);
        }
        return Optional.empty();
    }

    private static Optional<String> fluidIngredientIdText(JsonObject object) {
        if (object.has("tag")) {
            return Optional.of("#" + object.get("tag").getAsString());
        }
        if (object.has("fluid")) {
            return Optional.of(object.get("fluid").getAsString());
        }
        if (object.has("fluids")) {
            return Optional.of(object.get("fluids").getAsString());
        }
        return Optional.empty();
    }

    private static int ingredientAmount(JsonObject object) {
        if (object.has("amount") && object.get("amount").isJsonPrimitive()) {
            try {
                return Math.max(1, object.get("amount").getAsInt());
            } catch (NumberFormatException ignored) {
                return 1;
            }
        }
        return 1;
    }

    private static int resultCount(JsonObject object) {
        if (object.has("count") && object.get("count").isJsonPrimitive()) {
            try {
                return Math.max(1, object.get("count").getAsInt());
            } catch (NumberFormatException ignored) {
                return 1;
            }
        }
        return 1;
    }

    private static Optional<String> potionIdFromCreatePotionIngredient(JsonObject object) {
        if (!isCreatePotionFluidId(fluidIngredientIdText(object).orElse("")) || !object.has("components")) {
            return Optional.empty();
        }
        JsonElement componentsElement = object.get("components");
        if (!componentsElement.isJsonObject()) {
            return Optional.empty();
        }
        JsonObject components = componentsElement.getAsJsonObject();
        JsonElement potionContentsElement = components.get("minecraft:potion_contents");
        if (potionContentsElement == null || !potionContentsElement.isJsonObject()) {
            return Optional.empty();
        }
        JsonObject potionContents = potionContentsElement.getAsJsonObject();
        if (potionContents.has("potion") && potionContents.get("potion").isJsonPrimitive()) {
            return Optional.of(potionContents.get("potion").getAsString());
        }
        return Optional.empty();
    }

    private static Optional<IngredientEditValue> getInputEditValueFromSlotTag(IRecipeSlotView slot, IngredientKind kind) {
        if (kind == IngredientKind.FLUID) {
            return getInputTextFromFluidSlotTag(slot)
                .map(tag -> new IngredientEditValue(IngredientKind.FLUID, tag, getDisplayedFluidAmount(slot), ""));
        }
        return getInputTextFromItemSlotTag(slot)
            .map(tag -> new IngredientEditValue(IngredientKind.ITEM, tag, 1));
    }

    private static Optional<IngredientEditValue> getDisplayedInputEditValue(IRecipeSlotView slot, IngredientKind kind) {
        if (kind == IngredientKind.FLUID) {
            return slot.getDisplayedIngredient(NeoForgeTypes.FLUID_STACK)
                .map(stack -> new IngredientEditValue(
                    IngredientKind.FLUID,
                    fluidIdText(stack),
                    Math.max(1, stack.getAmount()),
                    getPotionId(stack).orElse("")
                ));
        }
        return slot.getDisplayedItemStack()
            .map(stack -> new IngredientEditValue(IngredientKind.ITEM, itemIdText(stack), 1));
    }

    private static Optional<String> getInputTextFromItemSlotTag(IRecipeSlotView slot) {
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

    private static Optional<String> getInputTextFromFluidSlotTag(IRecipeSlotView slot) {
        List<FluidStack> stacks = slot.getIngredients(NeoForgeTypes.FLUID_STACK).toList();
        if (stacks.size() <= 1) {
            return Optional.empty();
        }

        List<Fluid> fluids = stacks.stream()
            .map(FluidStack::getFluid)
            .toList();

        return BuiltInRegistries.FLUID.getTags()
            .filter(tagEntry -> tagMatchesFluids(tagEntry.getSecond(), fluids))
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

    private static boolean tagMatchesFluids(HolderSet.Named<Fluid> tag, List<Fluid> fluids) {
        if (tag.size() != fluids.size()) {
            return false;
        }
        for (int i = 0; i < tag.size(); i++) {
            if (!tag.get(i).value().equals(fluids.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static int getDisplayedFluidAmount(IRecipeSlotView slot) {
        return slot.getDisplayedIngredient(NeoForgeTypes.FLUID_STACK)
            .map(FluidStack::getAmount)
            .filter(amount -> amount > 0)
            .orElse(1);
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
            if (replacement.isFluid()) {
                slot.createDisplayOverrides().addIngredients(NeoForgeTypes.FLUID_STACK, Collections.singletonList(null));
            } else {
                slot.createDisplayOverrides().addIngredients(VanillaTypes.ITEM_STACK, Collections.singletonList(null));
            }
            return;
        }

        if (replacement.isFluid()) {
            int amount = Math.max(1, replacement.count());
            if (replacement.itemId().startsWith("#")) {
                List<FluidStack> stacks = getFluidTagStacks(replacement.itemId().substring(1), amount);
                if (!stacks.isEmpty()) {
                    slot.clearDisplayOverrides();
                    slot.createDisplayOverrides().addIngredients(NeoForgeTypes.FLUID_STACK, stacks);
                }
                return;
            }

            ResourceLocation fluidId = ResourceLocation.tryParse(replacement.itemId());
            if (fluidId == null || !BuiltInRegistries.FLUID.containsKey(fluidId)) {
                return;
            }
            FluidStack displayStack = new FluidStack(BuiltInRegistries.FLUID.get(fluidId), amount);
            applyPotionDisplayData(displayStack, replacement.extraId());
            slot.clearDisplayOverrides();
            slot.createDisplayOverrides().addIngredient(NeoForgeTypes.FLUID_STACK, displayStack);
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

    private static List<FluidStack> getFluidTagStacks(String tagIdText, int amount) {
        ResourceLocation tagId = ResourceLocation.tryParse(tagIdText);
        if (tagId == null) {
            return List.of();
        }
        TagKey<Fluid> tagKey = TagKey.create(Registries.FLUID, tagId);
        return BuiltInRegistries.FLUID.getTag(tagKey)
            .map(tag -> tag.stream()
                .map(holder -> new FluidStack(holder, amount))
                .toList())
            .orElse(List.of());
    }

    private static String itemIdText(ItemStack stack) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return itemId == null ? "" : itemId.toString();
    }

    private static String fluidIdText(FluidStack stack) {
        ResourceLocation fluidId = BuiltInRegistries.FLUID.getKey(stack.getFluid());
        return fluidId == null ? "" : fluidId.toString();
    }

    private static String normalizeInputText(String inputText, IngredientKind kind) {
        String value = inputText == null ? "" : inputText.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (value.startsWith("#")) {
            ResourceLocation tagId = ResourceLocation.tryParse(value.substring(1));
            return tagId == null ? null : "#" + tagId;
        }
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            return null;
        }
        if (kind == IngredientKind.FLUID) {
            return BuiltInRegistries.FLUID.containsKey(id) ? id.toString() : null;
        }
        return BuiltInRegistries.ITEM.containsKey(id) ? id.toString() : null;
    }

    private static String normalizeDetailText(String detailText, String ingredientId, IngredientKind kind) {
        String value = detailText == null ? "" : detailText.trim();
        if (value.isEmpty()) {
            return "";
        }
        if (kind != IngredientKind.FLUID || !isCreatePotionFluidId(ingredientId)) {
            return "";
        }
        ResourceLocation id = ResourceLocation.tryParse(value);
        return id == null ? null : id.toString();
    }

    private static boolean isCreatePotionFluidId(String ingredientId) {
        return "create:potion".equals(ingredientId);
    }

    private static Optional<String> getPotionId(FluidStack stack) {
        try {
            PotionContents contents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
            return contents.potion()
                .flatMap(holder -> holder.unwrapKey().map(key -> key.location().toString()));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static void applyPotionDisplayData(FluidStack stack, String potionIdText) {
        ResourceLocation potionId = ResourceLocation.tryParse(potionIdText);
        if (potionId == null) {
            return;
        }
        try {
            BuiltInRegistries.POTION.getHolder(potionId)
                .ifPresent(holder -> stack.set(DataComponents.POTION_CONTENTS, new PotionContents(holder)));
        } catch (Exception ignored) {
        }
    }

    public record SlotReplacement(String role, int slotIndex, String itemId, int count, int gridWidth, int gridHeight, String ingredientKind, String extraId) {
        public SlotReplacement(String role, int slotIndex, String itemId, int count, int gridWidth, int gridHeight, String ingredientKind) {
            this(role, slotIndex, itemId, count, gridWidth, gridHeight, ingredientKind, "");
        }

        public SlotReplacement(String role, int slotIndex, String itemId, int count, int gridWidth, int gridHeight) {
            this(role, slotIndex, itemId, count, gridWidth, gridHeight, IngredientKind.ITEM.name(), "");
        }

        public boolean clearsSlot() {
            return itemId == null || itemId.isBlank();
        }

        public IngredientKind kind() {
            return IngredientKind.FLUID.name().equals(ingredientKind) ? IngredientKind.FLUID : IngredientKind.ITEM;
        }

        public boolean isFluid() {
            return kind() == IngredientKind.FLUID;
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
