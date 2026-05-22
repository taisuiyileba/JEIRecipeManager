package com.jeirecipemanager;

import com.jeirecipemanager.network.NetworkHandler;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.buttons.IButtonState;
import mezz.jei.api.gui.buttons.IIconButtonController;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.common.gui.elements.DrawableResource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

public class RecipeDisableButtonController implements IIconButtonController {
    private static final ResourceLocation ENABLE_TEXTURE = ResourceLocation.fromNamespaceAndPath("jeirecipemanager", "textures/gui/recipe_enable.png");
    private static final ResourceLocation DISABLE_TEXTURE = ResourceLocation.fromNamespaceAndPath("jeirecipemanager", "textures/gui/recipe_disable.png");
    private static final ResourceLocation DELETE_TEXTURE = ResourceLocation.fromNamespaceAndPath("jeirecipemanager", "textures/gui/recipe_delete.png");

    private final IDrawable offIcon;
    private final IDrawable onIcon;
    private final IDrawable deleteIcon;
    private final String recipeId;
    private final boolean supportedRecipe;
    private final boolean generatedRecipe;
    private boolean enabled;

    public RecipeDisableButtonController(IRecipeLayoutDrawable<?> recipeLayout) {
        this.offIcon = new DrawableResource(DISABLE_TEXTURE, 0, 0, 9, 9, 0, 0, 0, 0, 9, 9);
        this.onIcon = new DrawableResource(ENABLE_TEXTURE, 0, 0, 9, 9, 0, 0, 0, 0, 9, 9);
        this.deleteIcon = new DrawableResource(DELETE_TEXTURE, 0, 0, 16, 16, 0, 0, 0, 0, 16, 16);

        IRecipeCategory<?> category = recipeLayout.getRecipeCategory();
        Object recipe = recipeLayout.getRecipe();
        ResourceLocation registryName = ((IRecipeCategory) category).getRegistryName(recipe);
        this.recipeId = registryName != null ? registryName.toString() : "";
        this.supportedRecipe = recipe instanceof RecipeHolder<?>;
        this.generatedRecipe = GeneratedRecipesManager.isGeneratedRecipeId(this.recipeId);

        this.enabled = this.recipeId.isEmpty() || !DisabledRecipesManager.isRecipeDisabled(this.recipeId);
    }

    public boolean hasValidRecipeId() {
        return this.supportedRecipe && !this.recipeId.isEmpty();
    }

    @Override
    public void updateState(IButtonState state) {
        if (generatedRecipe) {
            state.setForcePressed(false);
            state.setIcon(deleteIcon);
            return;
        }
        if (enabled) {
            state.setForcePressed(true);
            state.setIcon(onIcon);
        } else {
            state.setForcePressed(false);
            state.setIcon(offIcon);
        }
    }

    @Override
    public boolean onPress(IJeiUserInput input) {
        if (!input.isSimulate() && !this.recipeId.isEmpty()) {
            if (generatedRecipe) {
                NetworkHandler.sendRecipeDelete(recipeId);
                if (Minecraft.getInstance().screen != null) {
                    Minecraft.getInstance().screen.onClose();
                }
                return true;
            }
            this.enabled = !this.enabled;
            NetworkHandler.sendRecipeToggle(this.recipeId, !this.enabled);
        }
        return true;
    }

    @Override
    public void getTooltips(ITooltipBuilder tooltip) {
        if (generatedRecipe) {
            tooltip.add(Component.translatable("jeirecipemanager.tooltip.recipe_delete.generated"));
            return;
        }
        if (enabled) {
            tooltip.add(Component.translatable("jeirecipemanager.tooltip.recipe.enabled"));
        } else {
            tooltip.add(Component.translatable("jeirecipemanager.tooltip.recipe.disabled"));
        }
    }
}
