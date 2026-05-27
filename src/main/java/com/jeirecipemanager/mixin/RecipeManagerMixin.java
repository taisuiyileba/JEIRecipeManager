package com.jeirecipemanager.mixin;

import com.google.gson.JsonElement;
import com.jeirecipemanager.DisabledRecipesManager;
import com.jeirecipemanager.GeneratedRecipesManager;
import com.jeirecipemanager.network.NetworkHandler;
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

import java.util.HashMap;
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
        DisabledRecipesManager.serverInit();
        GeneratedRecipesManager.serverInit();
        DisabledRecipesManager.serverReload();
        GeneratedRecipesManager.serverApplyPendingDeletes(map);
        Map<String, String> allRecipeJsonMap = new HashMap<>();
        for (var entry : map.entrySet()) {
            allRecipeJsonMap.put(entry.getKey().toString(), entry.getValue().toString());
        }
        DisabledRecipesManager.serverCacheAllRecipeJson(allRecipeJsonMap);

        Set<String> disabledRecipes = DisabledRecipesManager.getDisabledRecipes();
        if (disabledRecipes.isEmpty()) {
            trySyncEmptyToClients();
            return;
        }

        Map<String, String> recipeJsonMap = new HashMap<>();
        for (String recipeId : disabledRecipes) {
            ResourceLocation id = ResourceLocation.tryParse(recipeId);
            if (id != null) {
                JsonElement json = map.get(id);
                if (json != null) {
                    String jsonStr = json.toString();
                    recipeJsonMap.put(recipeId, jsonStr);
                    DisabledRecipesManager.serverCacheRecipeJson(recipeId, jsonStr);
                }
            }
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

        trySyncToClients(recipeJsonMap);
    }

    private void trySyncEmptyToClients() {
        try {
            NetworkHandler.syncEmptyToAllPlayers();
        } catch (Exception e) {
            LOGGER.debug("Skipping empty sync (not in server environment): {}", e.getMessage());
        }
    }

    private void trySyncToClients(Map<String, String> recipeJsonMap) {
        try {
            NetworkHandler.syncToAllPlayers(recipeJsonMap);
        } catch (Exception e) {
            LOGGER.debug("Skipping network sync (not in server environment): {}", e.getMessage());
        }
    }
}
