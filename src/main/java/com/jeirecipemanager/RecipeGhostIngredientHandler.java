package com.jeirecipemanager;

import com.jeirecipemanager.mixin.RecipeGuiLayoutsAccessor;
import com.jeirecipemanager.mixin.RecipesGuiAccessor;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.gui.recipes.IRecipeLayoutWithButtons;
import mezz.jei.gui.recipes.RecipeGuiLayouts;
import mezz.jei.gui.recipes.RecipesGui;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RecipeGhostIngredientHandler implements IGhostIngredientHandler<RecipesGui> {
    @Override
    public <I> List<Target<I>> getTargetsTyped(RecipesGui gui, mezz.jei.api.ingredients.ITypedIngredient<I> ingredient, boolean doStart) {
        Optional<ItemStack> itemStack = ingredient.getIngredient(mezz.jei.api.constants.VanillaTypes.ITEM_STACK);
        if (itemStack.isEmpty() || itemStack.get().isEmpty()) {
            return List.of();
        }

        RecipeGuiLayouts layouts = ((RecipesGuiAccessor) gui).jeirecipemanager_getLayouts();
        List<IRecipeLayoutWithButtons<?>> visibleLayouts = ((RecipeGuiLayoutsAccessor) layouts).jeirecipemanager_getRecipeLayoutsWithButtons();
        List<Target<I>> targets = new ArrayList<>();

        for (IRecipeLayoutWithButtons<?> layoutWithButtons : visibleLayouts) {
            IRecipeLayoutDrawable<?> layout = layoutWithButtons.getRecipeLayout();
            String recipeId = getRecipeId(layout);
            if (recipeId == null || !RecipeEditManager.isEditing(recipeId)) {
                continue;
            }

            List<IRecipeSlotView> slots = layout.getRecipeSlotsView().getSlotViews();
            for (IRecipeSlotView slotView : slots) {
                if (!(slotView instanceof mezz.jei.api.gui.ingredient.IRecipeSlotDrawable slot)) {
                    continue;
                }
                if (slot.getRole() != mezz.jei.api.recipe.RecipeIngredientRole.INPUT &&
                    slot.getRole() != mezz.jei.api.recipe.RecipeIngredientRole.OUTPUT) {
                    continue;
                }

                Rect2i rect = slot.getRect();
                RecipeSlotUnderMouse underMouse = new RecipeSlotUnderMouse(slot, layout.getRect().getX(), layout.getRect().getY());
                Rect2i area = new Rect2i(
                    rect.getX() + underMouse.offset().x(),
                    rect.getY() + underMouse.offset().y(),
                    rect.getWidth(),
                    rect.getHeight()
                );
                targets.add(new ReplacementTarget<>(recipeId, slots, slot, area));
            }
        }

        return targets;
    }

    @Nullable
    private static String getRecipeId(IRecipeLayoutDrawable<?> layout) {
        ResourceLocation id = ((mezz.jei.api.recipe.category.IRecipeCategory) layout.getRecipeCategory()).getRegistryName(layout.getRecipe());
        return id == null ? null : id.toString();
    }

    @Override
    public void onComplete() {
    }

    private static class ReplacementTarget<I> implements Target<I> {
        private final String recipeId;
        private final List<IRecipeSlotView> slots;
        private final mezz.jei.api.gui.ingredient.IRecipeSlotDrawable slot;
        private final Rect2i area;

        private ReplacementTarget(String recipeId, List<IRecipeSlotView> slots, mezz.jei.api.gui.ingredient.IRecipeSlotDrawable slot, Rect2i area) {
            this.recipeId = recipeId;
            this.slots = slots;
            this.slot = slot;
            this.area = area;
        }

        @Override
        public Rect2i getArea() {
            return area;
        }

        @Override
        public void accept(I ingredient) {
            if (ingredient instanceof ItemStack stack) {
                RecipeEditManager.replaceSlot(recipeId, slots, slot, stack);
            }
        }
    }
}
