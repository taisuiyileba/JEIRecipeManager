package com.jeirecipemanager.mixin;

import com.google.gson.JsonElement;
import com.jeirecipemanager.DisabledRecipesManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.Set;

@Mixin(RecipeManager.class)
public class RecipeManagerMixin {

    private static final Logger LOGGER = LoggerFactory.getLogger("JEIRecipeManager");

    @Inject(
        method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
        at = @At("HEAD")
    )
    private void jeirecipemanager_removeDisabledRecipes(Map<ResourceLocation, JsonElement> map, ResourceManager resourceManager, ProfilerFiller profiler, CallbackInfo ci) {
        DisabledRecipesManager.reload();
        Set<String> disabledRecipes = DisabledRecipesManager.getDisabledRecipes();
        if (disabledRecipes.isEmpty()) {
            return;
        }

        int removed = 0;
        for (String recipeId : disabledRecipes) {
            ResourceLocation id = ResourceLocation.tryParse(recipeId);
            if (id != null && map.remove(id) != null) {
                removed++;
            }
        }

        if (removed > 0) {
            LOGGER.info("Removed {} disabled recipes from recipe manager", removed);
        }
    }
}