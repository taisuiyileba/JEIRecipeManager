package com.jeirecipemanager.mixin;

import com.jeirecipemanager.RecipeDisableButtonController;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.buttons.IIconButtonController;
import mezz.jei.api.recipe.advanced.IRecipeButtonControllerFactory;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.RecipeBookmark;
import mezz.jei.gui.elements.IconButton;
import mezz.jei.gui.recipes.IRecipeLayoutWithButtons;
import mezz.jei.gui.recipes.RecipeBookmarkButtonController;
import mezz.jei.gui.recipes.RecipeLayoutWithButtons;
import mezz.jei.gui.recipes.RecipeTransferButtonController;
import mezz.jei.gui.recipes.RecipesGui;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.List;

@Mixin(RecipeLayoutWithButtons.class)
public class RecipeLayoutWithButtonsMixin {

    @Inject(
        method = "create",
        at = @At(
            value = "INVOKE",
            target = "Ljava/util/List;add(Ljava/lang/Object;)Z",
            ordinal = 1,
            shift = At.Shift.AFTER
        ),
        locals = LocalCapture.CAPTURE_FAILHARD
    )
    private static <T> void onTransferButtonAdded(
        IRecipeLayoutDrawable<T> recipeLayoutDrawable,
        @Nullable RecipeBookmark<?, ?> recipeBookmark,
        BookmarkList bookmarks,
        RecipesGui recipesGui,
        List<IRecipeButtonControllerFactory> extraButtonControllerFactories,
        CallbackInfoReturnable<IRecipeLayoutWithButtons<T>> cir,
        RecipeTransferButtonController transferButton,
        RecipeBookmarkButtonController bookmarkButton,
        List<IconButton> buttons
    ) {
        RecipeDisableButtonController controller = new RecipeDisableButtonController(recipeLayoutDrawable);
        if (controller.hasValidRecipeId()) {
            buttons.add(new IconButton(controller));
        }
    }
}