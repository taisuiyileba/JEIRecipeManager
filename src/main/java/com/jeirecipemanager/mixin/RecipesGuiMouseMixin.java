package com.jeirecipemanager.mixin;

import com.jeirecipemanager.RecipeIngredientTextEditHandler;
import mezz.jei.gui.recipes.RecipesGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RecipesGui.class)
public class RecipesGuiMouseMixin {
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void jeirecipemanager_openIngredientTextEdit(double mouseX, double mouseY, int mouseButton, CallbackInfoReturnable<Boolean> cir) {
        if (RecipeIngredientTextEditHandler.handleEditClick((RecipesGui) (Object) this, mouseX, mouseY, mouseButton)) {
            cir.setReturnValue(true);
        }
    }

}
