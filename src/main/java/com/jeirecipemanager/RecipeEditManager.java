package com.jeirecipemanager;

import com.jeirecipemanager.network.NetworkHandler;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
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
            Math.max(1, stack.getCount())
        ));

        applyReplacementToSlot(targetSlot, draft.replacements().get(address.key()));
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
                return Optional.of(new SlotAddress(role, roleIndex));
            }
        }
        return Optional.empty();
    }

    private static boolean isSupportedRole(RecipeIngredientRole role) {
        return role == RecipeIngredientRole.INPUT || role == RecipeIngredientRole.OUTPUT;
    }

    private static void applyReplacementToSlot(IRecipeSlotDrawable slot, SlotReplacement replacement) {
        ResourceLocation itemId = ResourceLocation.tryParse(replacement.itemId());
        if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) {
            return;
        }
        ItemStack displayStack = new ItemStack(BuiltInRegistries.ITEM.get(itemId), Math.max(1, replacement.count()));
        slot.clearDisplayOverrides();
        slot.createDisplayOverrides().addIngredient(VanillaTypes.ITEM_STACK, displayStack);
    }

    public record SlotReplacement(String role, int slotIndex, String itemId, int count) {}

    private record Draft(String recipeId, Map<String, SlotReplacement> replacements) {
        private Draft(String recipeId) {
            this(recipeId, new LinkedHashMap<>());
        }
    }

    private record SlotAddress(RecipeIngredientRole role, int roleIndex) {
        private String key() {
            return role.name() + ":" + roleIndex;
        }
    }
}
