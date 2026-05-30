package com.jeirecipemanager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class RecipeAdapterManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("JEIRecipeManager");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String ADAPTER_PATH = "jeirecipemanager/recipe_adapters";
    private static final Map<String, RecipeAdapter> adaptersByRecipeType = new ConcurrentHashMap<>();

    public static void serverReload(ResourceManager resourceManager) {
        Map<String, RecipeAdapter> loaded = new ConcurrentHashMap<>();
        Map<ResourceLocation, Resource> resources = resourceManager.listResources(
            ADAPTER_PATH,
            id -> id.getPath().endsWith(".json")
        );

        resources.entrySet().stream()
            .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
            .forEach(entry -> loadAdapter(entry.getKey(), entry.getValue()).ifPresent(adapter -> {
                RecipeAdapter previous = loaded.put(adapter.recipeType(), adapter);
                if (previous != null) {
                    LOGGER.warn("Recipe adapter {} replaced earlier adapter for recipe type {}",
                        entry.getKey(), adapter.recipeType());
                }
            }));

        adaptersByRecipeType.clear();
        adaptersByRecipeType.putAll(loaded);
        LOGGER.info("Loaded {} JEIRecipeManager recipe adapters", adaptersByRecipeType.size());
    }

    public static void clientUpdateAdapters(List<String> adapterJsons) {
        Map<String, RecipeAdapter> loaded = new ConcurrentHashMap<>();
        for (String adapterJson : adapterJsons) {
            parseAdapter(ResourceLocation.fromNamespaceAndPath(Jeirecipemanager.MODID, "network"), adapterJson)
                .ifPresent(adapter -> loaded.put(adapter.recipeType(), adapter));
        }
        adaptersByRecipeType.clear();
        adaptersByRecipeType.putAll(loaded);
        LOGGER.info("Client received {} JEIRecipeManager recipe adapters", adaptersByRecipeType.size());
    }

    public static List<String> getSerializedAdapters() {
        return adaptersByRecipeType.values().stream()
            .sorted(Comparator.comparing(RecipeAdapter::recipeType))
            .map(RecipeAdapterManager::serializeAdapter)
            .toList();
    }

    public static Optional<Boolean> applyReplacements(JsonObject recipeJson, List<RecipeEditManager.SlotReplacement> replacements) {
        RecipeAdapter adapter = getAdapter(recipeJson).orElse(null);
        if (adapter == null) {
            return Optional.empty();
        }

        boolean changed = false;
        for (RecipeEditManager.SlotReplacement replacement : replacements) {
            Optional<SlotMapping> mapping = adapter.findSlot(replacement.role(), replacement.slotIndex());
            if (mapping.isEmpty()) {
                continue;
            }
            SlotMapping slot = mapping.get();
            SlotPath path = slot.pathForWrite(recipeJson, replacement).orElse(null);
            if (path == null) {
                LOGGER.warn("Adapter for {} points to missing path {}", adapter.recipeType(), slot.paths());
                return Optional.of(false);
            }

            if (replacement.clearsSlot()) {
                if (!JsonPointers.remove(recipeJson, path.path())) {
                    return Optional.of(false);
                }
            } else {
                JsonElement updated = slot.write(replacement, path);
                if (!JsonPointers.set(recipeJson, path.path(), updated)) {
                    return Optional.of(false);
                }
                slot.removeAlternates(recipeJson, path.path());
                if (path.amountPath().isPresent()) {
                    String amountPath = path.amountPath().get();
                    if (!JsonPointers.set(recipeJson, amountPath, new JsonPrimitive(Math.max(1, replacement.count())))) {
                        LOGGER.warn("Adapter for {} points to missing amount path {}", adapter.recipeType(), amountPath);
                        return Optional.of(false);
                    }
                }
            }
            changed = true;
        }
        return Optional.of(changed);
    }

    public static Optional<RecipeEditManager.IngredientEditValue> getSlotEditValue(JsonObject recipeJson, String role, int index) {
        RecipeAdapter adapter = getAdapter(recipeJson).orElse(null);
        if (adapter == null) {
            return Optional.empty();
        }
        return getSlotEditValue(recipeJson, role, index, null);
    }

    public static Optional<RecipeEditManager.IngredientEditValue> getSlotEditValue(
        JsonObject recipeJson,
        String role,
        int index,
        RecipeEditManager.IngredientKind preferredKind
    ) {
        RecipeAdapter adapter = getAdapter(recipeJson).orElse(null);
        if (adapter == null) {
            return Optional.empty();
        }
        return adapter.findSlot(role, index).flatMap(slot ->
            slot.pathForRead(recipeJson)
                .flatMap(path -> slot.read(path.original(), path.path(), preferredKind)
                    .map(value -> path.amountPath()
                        .map(amountPath -> new RecipeEditManager.IngredientEditValue(
                            value.kind(),
                            value.ingredientId(),
                            amountAt(recipeJson, amountPath).orElse(value.amount()),
                            value.detailId(),
                            true
                        ))
                        .orElse(value)))
        );
    }

    public static Optional<RecipeEditManager.IngredientKind> getSlotKind(JsonObject recipeJson, String role, int index) {
        RecipeAdapter adapter = getAdapter(recipeJson).orElse(null);
        if (adapter == null) {
            return Optional.empty();
        }
        return adapter.findSlot(role, index)
            .flatMap(SlotMapping::ingredientKind);
    }

    public static Optional<Boolean> isSlotAmountEditable(JsonObject recipeJson, String role, int index) {
        RecipeAdapter adapter = getAdapter(recipeJson).orElse(null);
        if (adapter == null) {
            return Optional.empty();
        }
        return adapter.findSlot(role, index)
            .map(SlotMapping::hasAmountPath);
    }

    public static Optional<Integer> getSlotAmountDisplayScale(JsonObject recipeJson, String role, int index) {
        RecipeAdapter adapter = getAdapter(recipeJson).orElse(null);
        if (adapter == null) {
            return Optional.empty();
        }
        return adapter.findSlot(role, index)
            .map(slot -> slot.pathForRead(recipeJson)
                .map(SlotPath::amountDisplayScale)
                .orElse(slot.amountDisplayScale()));
    }

    private static Optional<RecipeAdapter> getAdapter(JsonObject recipeJson) {
        JsonElement type = recipeJson.get("type");
        if (type == null || !type.isJsonPrimitive()) {
            return Optional.empty();
        }
        return Optional.ofNullable(adaptersByRecipeType.get(type.getAsString()));
    }

    private static Optional<RecipeAdapter> loadAdapter(ResourceLocation id, Resource resource) {
        try (Reader reader = resource.openAsReader()) {
            return parseAdapter(id, JsonParser.parseReader(reader).toString());
        } catch (Exception e) {
            LOGGER.warn("Failed to load recipe adapter {}", id, e);
            return Optional.empty();
        }
    }

    private static Optional<RecipeAdapter> parseAdapter(ResourceLocation id, String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            String recipeType = getRequiredString(root, "recipe_type");
            ResourceLocation recipeTypeId = ResourceLocation.tryParse(recipeType);
            if (recipeTypeId == null) {
                LOGGER.warn("Recipe adapter {} has invalid recipe_type {}", id, recipeType);
                return Optional.empty();
            }

            JsonArray slotsJson = root.getAsJsonArray("slots");
            if (slotsJson == null || slotsJson.isEmpty()) {
                LOGGER.warn("Recipe adapter {} has no slots", id);
                return Optional.empty();
            }

            List<SlotMapping> slots = new ArrayList<>();
            for (JsonElement slotElement : slotsJson) {
                JsonObject slotJson = slotElement.getAsJsonObject();
                String role = getRequiredString(slotJson, "role").toUpperCase(Locale.ROOT);
                int index = slotJson.get("index").getAsInt();
                Optional<String> amountPath = getOptionalString(slotJson, "amount_path");
                int amountDisplayScale = getOptionalInt(slotJson, "amount_display_scale").orElse(1);
                List<PathMapping> paths = getPaths(slotJson, amountPath, amountDisplayScale);
                Optional<String> valueName = getOptionalString(slotJson, "value");
                Optional<ValueKind> valueKind = valueName.map(ValueKind::fromJson).orElse(Optional.empty());
                boolean removeAlternates = getOptionalBoolean(slotJson, "remove_alternates").orElse(true);
                if (valueName.isPresent() && valueKind.isEmpty()) {
                    LOGGER.warn("Recipe adapter {} has unknown slot value kind {}", id, valueName.get());
                    return Optional.empty();
                }
                if (index < 0 || paths.isEmpty() ||
                    paths.stream().anyMatch(path -> path.path().isBlank() || !path.path().startsWith("/") ||
                        path.amountPath().filter(value -> value.isBlank() || !value.startsWith("/")).isPresent() ||
                        path.amountDisplayScale() < 1)) {
                    LOGGER.warn("Recipe adapter {} has invalid slot {}", id, slotJson);
                    return Optional.empty();
                }
                slots.add(new SlotMapping(role, index, paths, amountPath, valueKind, removeAlternates));
            }
            return Optional.of(new RecipeAdapter(recipeTypeId.toString(), slots));
        } catch (Exception e) {
            LOGGER.warn("Failed to parse recipe adapter {}", id, e);
            return Optional.empty();
        }
    }

    private static String serializeAdapter(RecipeAdapter adapter) {
        JsonObject root = new JsonObject();
        root.addProperty("recipe_type", adapter.recipeType());
        JsonArray slots = new JsonArray();
        for (SlotMapping slot : adapter.slots()) {
            JsonObject slotJson = new JsonObject();
            slotJson.addProperty("role", slot.role());
            slotJson.addProperty("index", slot.index());
            boolean simplePaths = slot.paths().stream()
                .allMatch(path -> path.amountPath().equals(slot.amountPath()));
            if (slot.paths().size() == 1 && simplePaths) {
                slotJson.addProperty("path", slot.paths().getFirst().path());
            } else {
                JsonArray paths = new JsonArray();
                for (PathMapping path : slot.paths()) {
                    if (path.amountPath().equals(slot.amountPath()) && path.amountDisplayScale() == 1) {
                        paths.add(path.path());
                    } else {
                        JsonObject pathJson = new JsonObject();
                        pathJson.addProperty("path", path.path());
                        path.amountPath().ifPresent(amountPath -> pathJson.addProperty("amount_path", amountPath));
                        if (path.amountDisplayScale() != 1) {
                            pathJson.addProperty("amount_display_scale", path.amountDisplayScale());
                        }
                        paths.add(pathJson);
                    }
                }
                slotJson.add("paths", paths);
            }
            slot.amountPath().ifPresent(amountPath -> slotJson.addProperty("amount_path", amountPath));
            if (slot.amountDisplayScale() != 1) {
                slotJson.addProperty("amount_display_scale", slot.amountDisplayScale());
            }
            if (!slot.removeAlternates()) {
                slotJson.addProperty("remove_alternates", false);
            }
            slot.valueKind().ifPresent(valueKind -> slotJson.addProperty("value", valueKind.jsonName()));
            slots.add(slotJson);
        }
        root.add("slots", slots);
        return GSON.toJson(root);
    }

    private static String getRequiredString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            throw new IllegalArgumentException("Missing string key " + key);
        }
        return element.getAsString();
    }

    private static Optional<String> getOptionalString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return Optional.empty();
        }
        String value = element.getAsString();
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private static Optional<Boolean> getOptionalBoolean(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return Optional.empty();
        }
        return Optional.of(element.getAsBoolean());
    }

    private static Optional<Integer> getOptionalInt(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return Optional.empty();
        }
        return Optional.of(element.getAsInt());
    }

    private static List<PathMapping> getPaths(JsonObject object, Optional<String> defaultAmountPath, int defaultAmountDisplayScale) {
        JsonArray paths = object.getAsJsonArray("paths");
        if (paths != null) {
            List<PathMapping> result = new ArrayList<>();
            for (JsonElement path : paths) {
                if (path == null) {
                    continue;
                }
                if (path.isJsonPrimitive()) {
                    result.add(new PathMapping(path.getAsString(), defaultAmountPath, defaultAmountDisplayScale));
                } else if (path.isJsonObject()) {
                    JsonObject pathJson = path.getAsJsonObject();
                    result.add(new PathMapping(
                        getRequiredString(pathJson, "path"),
                        getOptionalString(pathJson, "amount_path").or(() -> defaultAmountPath),
                        getOptionalInt(pathJson, "amount_display_scale").orElse(defaultAmountDisplayScale)
                    ));
                }
            }
            return result;
        }
        return List.of(new PathMapping(getRequiredString(object, "path"), defaultAmountPath, defaultAmountDisplayScale));
    }

    private static Optional<Integer> amountAt(JsonElement root, String path) {
        return JsonPointers.get(root, path)
            .filter(JsonElement::isJsonPrimitive)
            .map(JsonElement::getAsInt)
            .map(amount -> Math.max(1, amount));
    }

    private enum ValueKind {
        ITEM_INGREDIENT("item_ingredient", RecipeEditManager.IngredientKind.ITEM) {
            @Override
            JsonElement write(RecipeEditManager.SlotReplacement replacement, JsonElement original, boolean includeAmount) {
                JsonObject object = itemOrTag(replacement.itemId(), "item");
                if (includeAmount) {
                    copyAmount(original, object, "count", replacement.count());
                }
                return object;
            }

            @Override
            Optional<RecipeEditManager.IngredientEditValue> read(JsonElement element) {
                return idOrTag(element, "item")
                    .map(id -> new RecipeEditManager.IngredientEditValue(ingredientKind(), id, amount(element, "count")));
            }
        },
        ITEM_STACK("item_stack", RecipeEditManager.IngredientKind.ITEM) {
            @Override
            JsonElement write(RecipeEditManager.SlotReplacement replacement, JsonElement original, boolean includeAmount) {
                JsonObject object = new JsonObject();
                object.addProperty("id", replacement.itemId());
                if (includeAmount) {
                    copyAmount(original, object, "count", replacement.count());
                }
                return object;
            }

            @Override
            Optional<RecipeEditManager.IngredientEditValue> read(JsonElement element) {
                return idOrTag(element, "id")
                    .map(id -> new RecipeEditManager.IngredientEditValue(ingredientKind(), id, amount(element, "count")));
            }
        },
        FLUID_INGREDIENT("fluid_ingredient", RecipeEditManager.IngredientKind.FLUID) {
            @Override
            JsonElement write(RecipeEditManager.SlotReplacement replacement, JsonElement original, boolean includeAmount) {
                JsonObject object = itemOrTag(replacement.itemId(), "fluid");
                if (includeAmount) {
                    copyAmount(original, object, "amount", replacement.count());
                }
                return object;
            }

            @Override
            Optional<RecipeEditManager.IngredientEditValue> read(JsonElement element) {
                return idOrTag(element, "fluid")
                    .map(id -> new RecipeEditManager.IngredientEditValue(ingredientKind(), id, amount(element, "amount")));
            }
        },
        RESOURCE_STACK("resource_stack", RecipeEditManager.IngredientKind.RESOURCE) {
            @Override
            JsonElement write(RecipeEditManager.SlotReplacement replacement, JsonElement original, boolean includeAmount) {
                JsonObject object = itemOrTag(replacement.itemId(), "id");
                if (includeAmount) {
                    copyAmount(original, object, "amount", replacement.count());
                }
                return object;
            }

            @Override
            Optional<RecipeEditManager.IngredientEditValue> read(JsonElement element) {
                return idOrTag(element, "id")
                    .map(id -> new RecipeEditManager.IngredientEditValue(ingredientKind(), id, amount(element, "amount")));
            }
        };

        private final String jsonName;
        private final RecipeEditManager.IngredientKind ingredientKind;

        ValueKind(String jsonName, RecipeEditManager.IngredientKind ingredientKind) {
            this.jsonName = jsonName;
            this.ingredientKind = ingredientKind;
        }

        abstract JsonElement write(RecipeEditManager.SlotReplacement replacement, JsonElement original, boolean includeAmount);

        abstract Optional<RecipeEditManager.IngredientEditValue> read(JsonElement element);

        String jsonName() {
            return jsonName;
        }

        RecipeEditManager.IngredientKind ingredientKind() {
            return ingredientKind;
        }

        private static Optional<ValueKind> fromJson(String name) {
            for (ValueKind value : values()) {
                if (value.jsonName.equals(name)) {
                    return Optional.of(value);
                }
            }
            return Optional.empty();
        }

        private static JsonObject itemOrTag(String id, String idKey) {
            JsonObject object = new JsonObject();
            if (id.startsWith("#")) {
                object.addProperty("tag", id.substring(1));
            } else {
                object.addProperty(idKey, id);
            }
            return object;
        }

        private static Optional<String> idOrTag(JsonElement element, String idKey) {
            if (element == null || !element.isJsonObject()) {
                return Optional.empty();
            }
            JsonObject object = element.getAsJsonObject();
            if (object.has("tag")) {
                return Optional.of("#" + object.get("tag").getAsString());
            }
            if (object.has(idKey)) {
                return Optional.of(object.get(idKey).getAsString());
            }
            if (!"id".equals(idKey) && object.has("id")) {
                return Optional.of(object.get("id").getAsString());
            }
            return Optional.empty();
        }

        private static void copyAmount(JsonElement original, JsonObject target, String key, int replacementAmount) {
            int amount = replacementAmount > 1 ? replacementAmount : amount(original, key);
            if (amount > 1 || hasAmount(original, key)) {
                target.addProperty(key, Math.max(1, amount));
            }
        }

        private static int amount(JsonElement element, String key) {
            if (element != null && element.isJsonObject()) {
                JsonElement amount = element.getAsJsonObject().get(key);
                if (amount != null && amount.isJsonPrimitive()) {
                    try {
                        return Math.max(1, amount.getAsInt());
                    } catch (NumberFormatException ignored) {
                        return 1;
                    }
                }
            }
            return 1;
        }

        private static boolean hasAmount(JsonElement element, String key) {
            return element != null && element.isJsonObject() && element.getAsJsonObject().has(key);
        }
    }

    private record RecipeAdapter(String recipeType, List<SlotMapping> slots) {
        private Optional<SlotMapping> findSlot(String role, int index) {
            String normalizedRole = role.toUpperCase(Locale.ROOT);
            return slots.stream()
                .filter(slot -> slot.role().equals(normalizedRole) && slot.index() == index)
                .findFirst();
        }
    }

    private record SlotMapping(
        String role,
        int index,
        List<PathMapping> paths,
        Optional<String> amountPath,
        Optional<ValueKind> valueKind,
        boolean removeAlternates
    ) {
        private int amountDisplayScale() {
            return paths.stream()
                .mapToInt(PathMapping::amountDisplayScale)
                .filter(scale -> scale > 1)
                .findFirst()
                .orElse(1);
        }

        private JsonElement write(RecipeEditManager.SlotReplacement replacement, SlotPath path) {
            return valueKind
                .map(kind -> kind.write(replacement, path.original(), path.amountPath().isEmpty()))
                .orElseGet(() -> new JsonPrimitive(scalarValue(replacement.itemId(), path.path())));
        }

        private Optional<RecipeEditManager.IngredientEditValue> read(
            JsonElement element,
            String path,
            RecipeEditManager.IngredientKind preferredKind
        ) {
            if (valueKind.isPresent()) {
                return valueKind.get().read(element);
            }
            if (element == null || !element.isJsonPrimitive()) {
                return Optional.empty();
            }
            RecipeEditManager.IngredientKind kind = preferredKind == null
                ? RecipeEditManager.IngredientKind.ITEM
                : preferredKind;
            return Optional.of(new RecipeEditManager.IngredientEditValue(
                kind,
                scalarId(element.getAsString(), path),
                1
            ));
        }

        private Optional<RecipeEditManager.IngredientKind> ingredientKind() {
            return valueKind.map(ValueKind::ingredientKind);
        }

        private boolean hasAmountPath() {
            return amountPath.isPresent() || paths.stream().anyMatch(path -> path.amountPath().isPresent());
        }

        private Optional<SlotPath> pathForRead(JsonElement root) {
            return paths.stream()
                .flatMap(path -> JsonPointers.get(root, path.path())
                    .map(element -> new SlotPath(path.path(), path.amountPath(), path.amountDisplayScale(), element))
                    .stream())
                .findFirst();
        }

        private Optional<SlotPath> pathForWrite(JsonElement root, RecipeEditManager.SlotReplacement replacement) {
            PathMapping preferredPath = selectPathForValue(replacement.itemId());
            Optional<JsonElement> preferredOriginal = JsonPointers.get(root, preferredPath.path());
            if (preferredOriginal.isPresent() || JsonPointers.parent(root, preferredPath.path()).isPresent()) {
                return Optional.of(new SlotPath(preferredPath.path(), preferredPath.amountPath(), preferredPath.amountDisplayScale(), preferredOriginal
                    .or(() -> pathForRead(root).map(SlotPath::original))
                    .orElse(null)));
            }
            return paths.stream()
                .map(path -> new SlotPath(path.path(), path.amountPath(), path.amountDisplayScale(), JsonPointers.get(root, path.path()).orElse(null)))
                .filter(path -> path.original() != null || JsonPointers.parent(root, path.path()).isPresent())
                .findFirst();
        }

        private void removeAlternates(JsonElement root, String selectedPath) {
            if (!removeAlternates) {
                return;
            }
            for (PathMapping path : paths) {
                if (!path.path().equals(selectedPath)) {
                    JsonPointers.remove(root, path.path());
                }
            }
        }

        private PathMapping selectPathForValue(String id) {
            if (id != null && id.startsWith("#")) {
                return paths.stream()
                    .filter(path -> path.path().endsWith("/tag"))
                    .findFirst()
                    .orElse(paths.getFirst());
            }
            return paths.stream()
                .filter(path -> !path.path().endsWith("/tag"))
                .findFirst()
                .orElse(paths.getFirst());
        }

        private static String scalarValue(String id, String path) {
            if (id != null && id.startsWith("#") && path.endsWith("/tag")) {
                return id.substring(1);
            }
            return id;
        }

        private static String scalarId(String id, String path) {
            if (path.endsWith("/tag")) {
                return "#" + id;
            }
            return id;
        }
    }

    private record PathMapping(String path, Optional<String> amountPath, int amountDisplayScale) {}

    private record SlotPath(String path, Optional<String> amountPath, int amountDisplayScale, JsonElement original) {}

    private static class JsonPointers {
        private static Optional<JsonElement> get(JsonElement root, String pointer) {
            JsonElement current = root;
            for (String token : tokens(pointer)) {
                if (current == null) {
                    return Optional.empty();
                }
                if (current.isJsonObject()) {
                    current = current.getAsJsonObject().get(token);
                } else if (current.isJsonArray()) {
                    int index = parseIndex(token);
                    JsonArray array = current.getAsJsonArray();
                    if (index < 0 || index >= array.size()) {
                        return Optional.empty();
                    }
                    current = array.get(index);
                } else {
                    return Optional.empty();
                }
            }
            return Optional.ofNullable(current);
        }

        private static boolean set(JsonElement root, String pointer, JsonElement value) {
            Parent parent = parent(root, pointer).orElse(null);
            if (parent == null) {
                return false;
            }
            if (parent.element().isJsonObject()) {
                parent.element().getAsJsonObject().add(parent.leaf(), value);
                return true;
            }
            if (parent.element().isJsonArray()) {
                int index = parseIndex(parent.leaf());
                JsonArray array = parent.element().getAsJsonArray();
                if (index < 0 || index >= array.size()) {
                    return false;
                }
                array.set(index, value);
                return true;
            }
            return false;
        }

        private static boolean remove(JsonElement root, String pointer) {
            Parent parent = parent(root, pointer).orElse(null);
            if (parent == null) {
                return false;
            }
            if (parent.element().isJsonObject()) {
                return parent.element().getAsJsonObject().remove(parent.leaf()) != null;
            }
            if (parent.element().isJsonArray()) {
                int index = parseIndex(parent.leaf());
                JsonArray array = parent.element().getAsJsonArray();
                if (index < 0 || index >= array.size()) {
                    return false;
                }
                array.remove(index);
                return true;
            }
            return false;
        }

        private static Optional<Parent> parent(JsonElement root, String pointer) {
            List<String> tokens = tokens(pointer);
            if (tokens.isEmpty()) {
                return Optional.empty();
            }
            JsonElement current = root;
            for (int i = 0; i < tokens.size() - 1; i++) {
                Optional<JsonElement> next = get(current, "/" + escape(tokens.get(i)));
                if (next.isEmpty()) {
                    return Optional.empty();
                }
                current = next.get();
            }
            return Optional.of(new Parent(current, tokens.get(tokens.size() - 1)));
        }

        private static List<String> tokens(String pointer) {
            if (pointer == null || pointer.isEmpty() || !pointer.startsWith("/")) {
                return List.of();
            }
            String[] rawTokens = pointer.substring(1).split("/", -1);
            List<String> result = new ArrayList<>(rawTokens.length);
            for (String token : rawTokens) {
                result.add(token.replace("~1", "/").replace("~0", "~"));
            }
            return result;
        }

        private static String escape(String token) {
            return token.replace("~", "~0").replace("/", "~1");
        }

        private static int parseIndex(String token) {
            try {
                return Integer.parseInt(token);
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }

        private record Parent(JsonElement element, String leaf) {}
    }
}
