package com.jeirecipemanager.mixin;

import com.jeirecipemanager.RecipeDisableButtonController;
import com.jeirecipemanager.RecipeAddButtonController;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.buttons.IIconButtonController;
import mezz.jei.api.recipe.advanced.IRecipeButtonControllerFactory;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.RecipeBookmark;
import mezz.jei.gui.elements.IconButton;
import mezz.jei.gui.recipes.IRecipeLayoutWithButtons;
import mezz.jei.gui.recipes.RecipeBookmarkButtonController;
import mezz.jei.gui.recipes.RecipeLayoutWithButtons;
import mezz.jei.gui.recipes.RecipeTransferButtonController;
import mezz.jei.gui.recipes.RecipesGui;
import mezz.jei.library.gui.recipes.RecipeLayout;
import net.minecraft.client.renderer.Rect2i;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.List;

@Mixin(RecipeLayoutWithButtons.class)
public class RecipeLayoutWithButtonsMixin {
    @Shadow
    @Final
    private IRecipeLayoutDrawable<?> recipeLayout;

    @Shadow
    @Final
    private List<IconButton> buttons;

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
        RecipeAddButtonController addController = new RecipeAddButtonController(recipeLayoutDrawable);
        if (addController.hasValidRecipeId()) {
            buttons.add(new IconButton(addController));
        }
    }

    @Inject(method = "updateBounds", at = @At("TAIL"))
    private void jeirecipemanager_updateButtonBounds(int recipeXOffset, int recipeYOffset, CallbackInfo ci) {
        if (buttons.size() <= 2) {
            return;
        }

        Rect2i recipeLayoutRect = recipeLayout.getRect();
        
        Rect2i transferButtonArea = recipeLayout.getRecipeTransferButtonArea();
        Rect2i bookmarkButtonArea = recipeLayout.getRecipeBookmarkButtonArea();
        
        int buttonWidth = RecipeLayout.RECIPE_BUTTON_SIZE;
        int buttonHeight = RecipeLayout.RECIPE_BUTTON_SIZE;
        int spacing = RecipeLayout.RECIPE_BUTTON_SPACING;

       if (buttons.size() > 2) {
           IconButton disableButton = buttons.get(2);
           if (disableButton.isVisible()) {
               int x = transferButtonArea.getX() + transferButtonArea.getWidth() + spacing;
               int y = transferButtonArea.getY();

               disableButton.updateBounds(new ImmutableRect2i(
                   recipeLayoutRect.getX() + x,
                   recipeLayoutRect.getY() + y,
                   buttonWidth,
                   buttonHeight
               ));
           }
       }

       if (buttons.size() > 3) {
           IconButton editButton = buttons.get(3);
           if (editButton.isVisible()) {
               int x = bookmarkButtonArea.getX() + bookmarkButtonArea.getWidth() + spacing;
               int y = bookmarkButtonArea.getY();

               editButton.updateBounds(new ImmutableRect2i(
                   recipeLayoutRect.getX() + x,
                   recipeLayoutRect.getY() + y,
                   buttonWidth,
                   buttonHeight
               ));
           }
       }
    }

    @Inject(method = "totalWidth", at = @At("RETURN"), cancellable = true)
    private void jeirecipemanager_totalWidth(CallbackInfoReturnable<Integer> cir) {
        Rect2i area = recipeLayout.getRect();
        Rect2i areaWithBorder = recipeLayout.getRectWithBorder();
        int leftBorderWidth = area.getX() - areaWithBorder.getX();
        
        int maxRight = areaWithBorder.getWidth() - leftBorderWidth;
        
        Rect2i transferButtonArea = recipeLayout.getRecipeTransferButtonArea();
        Rect2i bookmarkButtonArea = recipeLayout.getRecipeBookmarkButtonArea();
        int buttonWidth = RecipeLayout.RECIPE_BUTTON_SIZE;
        int spacing = RecipeLayout.RECIPE_BUTTON_SPACING;

        if (buttons.size() > 2 && buttons.get(2).isVisible()) {
            int rightEdge = transferButtonArea.getX() + transferButtonArea.getWidth() + spacing + buttonWidth;
            maxRight = Math.max(maxRight, rightEdge);
        }

        if (buttons.size() > 3 && buttons.get(3).isVisible()) {
            int rightEdge = bookmarkButtonArea.getX() + bookmarkButtonArea.getWidth() + spacing + buttonWidth;
            maxRight = Math.max(maxRight, rightEdge);
        }

        cir.setReturnValue(leftBorderWidth + maxRight);
    }
}
