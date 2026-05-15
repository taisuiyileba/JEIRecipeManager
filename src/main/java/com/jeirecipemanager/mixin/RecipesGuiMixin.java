package com.jeirecipemanager.mixin;

import com.jeirecipemanager.RecipeManagerState;
import mezz.jei.gui.recipes.RecipesGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RecipesGui.class)
public class RecipesGuiMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        RecipeManagerState.setRecipesGuiInstance((RecipesGui) (Object) this);
    }
}