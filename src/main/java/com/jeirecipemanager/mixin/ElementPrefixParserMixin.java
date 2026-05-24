package com.jeirecipemanager.mixin;

import com.jeirecipemanager.DisabledRecipesManager;
import it.unimi.dsi.fastutil.chars.Char2ObjectMap;
import mezz.jei.api.helpers.IColorHelper;
import mezz.jei.api.helpers.IModIdHelper;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.config.IIngredientFilterConfig;
import mezz.jei.core.search.LimitedStringStorage;
import mezz.jei.core.search.PrefixInfo;
import mezz.jei.core.search.SearchMode;
import mezz.jei.gui.ingredients.IListElement;
import mezz.jei.gui.ingredients.IListElementInfo;
import mezz.jei.gui.search.ElementPrefixParser;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Optional;

@Mixin(ElementPrefixParser.class)
public class ElementPrefixParserMixin {
    private static final String DISABLED_RECIPE_OUTPUT_SEARCH_TOKEN = "jeirecipemanager_disabled_recipe_output";
    private static final String GENERATED_RECIPE_OUTPUT_SEARCH_TOKEN = "jeirecipemanager_generated_recipe_output";

    @Shadow
    @Final
    @Mutable
    private Char2ObjectMap<PrefixInfo<IListElementInfo<?>, IListElement<?>>> map;

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void jeirecipemanager_addDisabledRecipeOutputPrefix(IIngredientManager ingredientManager, IIngredientFilterConfig config, IColorHelper colorHelper, IModIdHelper modIdHelper, CallbackInfo ci) {
        this.map.put('-', new PrefixInfo<>(
            '-',
            () -> SearchMode.REQUIRE_PREFIX,
            info -> {
                ResourceLocation resourceLocation = info.getResourceLocation();
                if (DisabledRecipesManager.isClientDisabledRecipeOutput(resourceLocation)) {
                    return List.of(DISABLED_RECIPE_OUTPUT_SEARCH_TOKEN);
                }
                return List.of();
            },
            LimitedStringStorage::new
        ));
        this.map.put('+', new PrefixInfo<>(
            '+',
            () -> SearchMode.REQUIRE_PREFIX,
            info -> {
                ResourceLocation resourceLocation = info.getResourceLocation();
                if (DisabledRecipesManager.isClientGeneratedRecipeOutput(resourceLocation)) {
                    return List.of(GENERATED_RECIPE_OUTPUT_SEARCH_TOKEN);
                }
                return List.of();
            },
            LimitedStringStorage::new
        ));
    }

    @Inject(method = "parseToken", at = @At("HEAD"), cancellable = true, remap = false)
    private void jeirecipemanager_parseDisabledRecipeOutputPrefix(String token, CallbackInfoReturnable<Optional<ElementPrefixParser.TokenInfo>> cir) {
        if (token.equals("-")) {
            PrefixInfo<IListElementInfo<?>, IListElement<?>> prefixInfo = this.map.get('-');
            if (prefixInfo != null && prefixInfo.getMode() != SearchMode.DISABLED) {
                cir.setReturnValue(Optional.of(new ElementPrefixParser.TokenInfo(DISABLED_RECIPE_OUTPUT_SEARCH_TOKEN, prefixInfo)));
            }
        }
        if (token.equals("+")) {
            PrefixInfo<IListElementInfo<?>, IListElement<?>> prefixInfo = this.map.get('+');
            if (prefixInfo != null && prefixInfo.getMode() != SearchMode.DISABLED) {
                cir.setReturnValue(Optional.of(new ElementPrefixParser.TokenInfo(GENERATED_RECIPE_OUTPUT_SEARCH_TOKEN, prefixInfo)));
            }
        }
    }
}
