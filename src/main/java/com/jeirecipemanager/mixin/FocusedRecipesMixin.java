package com.jeirecipemanager.mixin;

import com.jeirecipemanager.DisabledRecipesManager;
import com.jeirecipemanager.GeneratedRecipesManager;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.gui.recipes.lookups.FocusedRecipes;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Mixin(FocusedRecipes.class)
public class FocusedRecipesMixin<T> {

    @Shadow
    @Final
    private IRecipeCategory<T> recipeCategory;

    @SuppressWarnings("unchecked")
    @Inject(method = "getRecipes", at = @At("RETURN"), cancellable = true, remap = false)
    private void jeirecipemanager_sortRecipes(CallbackInfoReturnable<List<T>> cir) {
        List<T> original = cir.getReturnValue();
        if (original == null || original.size() <= 1) {
            return;
        }

        Set<String> disabledRecipes = DisabledRecipesManager.getDisabledRecipes();
        IRecipeCategory<T> category = this.recipeCategory;

        List<T> sorted = new ArrayList<>(original);
        sorted.sort(Comparator.comparingInt(recipe -> {
            ResourceLocation id = category.getRegistryName(recipe);
            if (id == null) {
                return 2;
            }
            String idStr = id.toString();
            if (GeneratedRecipesManager.isGeneratedRecipeId(idStr)) {
                return 0;
            }
            if (disabledRecipes.contains(idStr)) {
                return 1;
            }
            return 2;
        }));

        cir.setReturnValue(sorted);
    }
}
