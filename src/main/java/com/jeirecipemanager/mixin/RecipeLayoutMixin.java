package com.jeirecipemanager.mixin;

import com.jeirecipemanager.RecipeEditManager;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.library.gui.recipes.RecipeLayout;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RecipeLayout.class)
public class RecipeLayoutMixin {
    @Inject(method = "tick", at = @At("TAIL"), remap = false)
    private void jeirecipemanager_applyDraftSlotOverrides(CallbackInfo ci) {
        RecipeEditManager.applyDraftToLayout((IRecipeLayoutDrawable<?>) (Object) this);
    }
}
