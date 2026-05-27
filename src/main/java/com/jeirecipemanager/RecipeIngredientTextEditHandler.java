package com.jeirecipemanager;

import com.jeirecipemanager.mixin.RecipeGuiLayoutsAccessor;
import com.jeirecipemanager.mixin.RecipesGuiAccessor;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.gui.recipes.IRecipeLayoutWithButtons;
import mezz.jei.gui.recipes.RecipeGuiLayouts;
import mezz.jei.gui.recipes.RecipesGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public class RecipeIngredientTextEditHandler {
    public static boolean handleEditClick(RecipesGui gui, double mouseX, double mouseY, int mouseButton) {
        if (mouseButton != 0 && mouseButton != 2) {
            return false;
        }

        RecipeGuiLayouts layouts = ((RecipesGuiAccessor) gui).jeirecipemanager_getLayouts();
        List<IRecipeLayoutWithButtons<?>> visibleLayouts = ((RecipeGuiLayoutsAccessor) layouts).jeirecipemanager_getRecipeLayoutsWithButtons();
        for (IRecipeLayoutWithButtons<?> layoutWithButtons : visibleLayouts) {
            IRecipeLayoutDrawable<?> layout = layoutWithButtons.getRecipeLayout();
            String recipeId = getRecipeId(layout);
            if (recipeId == null || !RecipeEditManager.isEditing(recipeId)) {
                continue;
            }

            List<IRecipeSlotView> slots = layout.getRecipeSlotsView().getSlotViews();
            for (IRecipeSlotView slotView : slots) {
                if (!(slotView instanceof IRecipeSlotDrawable slot)) {
                    continue;
                }
                if (mouseButton == 2 &&
                    slot.getRole() != RecipeIngredientRole.INPUT &&
                    slot.getRole() != RecipeIngredientRole.OUTPUT) {
                    continue;
                }
                if (mouseButton == 0 &&
                    slot.getRole() != RecipeIngredientRole.INPUT &&
                    slot.getRole() != RecipeIngredientRole.OUTPUT) {
                    continue;
                }
                if (isMouseOver(layout, slot, mouseX, mouseY)) {
                    if (mouseButton == 0) {
                        if (!RecipeEditManager.canClearSlots(recipeId, layout.getRecipe())) {
                            return true;
                        }
                        if (hasCarriedItem()) {
                            return false;
                        }
                        return RecipeEditManager.clearSlot(recipeId, slots, slot);
                    }

                    RecipeEditManager.IngredientEditValue initialValue = RecipeEditManager.getSlotEditValue(recipeId, layout.getRecipe(), slots, slot)
                        .orElseGet(() -> new RecipeEditManager.IngredientEditValue(
                            RecipeEditManager.getSlotIngredientKind(recipeId, layout.getRecipe(), slots, slot),
                            "",
                            1
                        ));
                    Minecraft.getInstance().setScreen(new RecipeIngredientTextEditScreen(
                        (Screen) gui,
                        recipeId,
                        slots,
                        slot,
                        initialValue
                    ));
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasCarriedItem() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return false;
        }
        return !minecraft.player.containerMenu.getCarried().isEmpty();
    }

    private static void showClearUnsupportedMessage() {
        Minecraft minecraft = Minecraft.getInstance();
        // 使用简洁的消息提示，在屏幕底部显示，不遮挡配方界面
        Component message = Component.translatableWithFallback(
            "jeirecipemanager.message.clear_slot.unsupported",
            "非工作台配方无法清空槽位"
        );
        if (minecraft.player != null) {
            minecraft.player.displayClientMessage(message, false);
        }
    }

    public static boolean handleEditClick(Screen screen, double mouseX, double mouseY, int mouseButton) {
        if (screen instanceof RecipesGui recipesGui) {
            return handleEditClick(recipesGui, mouseX, mouseY, mouseButton);
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static String getRecipeId(IRecipeLayoutDrawable<?> layout) {
        ResourceLocation id = ((mezz.jei.api.recipe.category.IRecipeCategory<Object>) layout.getRecipeCategory())
            .getRegistryName(layout.getRecipe());
        return id == null ? null : id.toString();
    }

    @SuppressWarnings("removal")
    private static boolean isMouseOver(IRecipeLayoutDrawable<?> layout, IRecipeSlotDrawable slot, double mouseX, double mouseY) {
        Rect2i layoutRect = layout.getRect();
        Rect2i slotRect = slot.getRect();
        int x = layoutRect.getX() + slotRect.getX();
        int y = layoutRect.getY() + slotRect.getY();
        return mouseX >= x &&
            mouseY >= y &&
            mouseX < x + slotRect.getWidth() &&
            mouseY < y + slotRect.getHeight();
    }
}
