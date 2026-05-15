package com.jeirecipemanager.mixin;

import com.google.gson.JsonElement;
import com.jeirecipemanager.DisabledRecipesManager;
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
        
        // 在重新加载配置之前，先同步空列表到客户端
//        trySyncEmptyToClients();
        
        DisabledRecipesManager.serverReload();
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
        
        // 重新加载完成后，同步最新的禁用列表到客户端
        trySyncToClients();
    }
    
    /**
     * 尝试同步空列表到客户端，如果失败则忽略
     */
    private void trySyncEmptyToClients() {
        try {
            NetworkHandler.syncEmptyToAllPlayers();
        } catch (Exception e) {
            LOGGER.debug("Skipping empty sync (not in server environment): {}", e.getMessage());
        }
    }
    
    /**
     * 尝试同步最新禁用列表到客户端，如果失败则忽略
     */
    private void trySyncToClients() {
        try {
            NetworkHandler.syncToAllPlayers();
        } catch (Exception e) {
            LOGGER.debug("Skipping network sync (not in server environment): {}", e.getMessage());
        }
    }
}
