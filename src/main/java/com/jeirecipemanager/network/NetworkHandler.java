package com.jeirecipemanager.network;

import com.jeirecipemanager.DisabledRecipesManager;
import com.jeirecipemanager.Jeirecipemanager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@EventBusSubscriber(modid = Jeirecipemanager.MODID)
public class NetworkHandler {

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        var reg = event.registrar("1").optional();

        reg.playToServer(RecipeTogglePayload.TYPE, RecipeTogglePayload.STREAM_CODEC, RecipeTogglePayload::handle);
        reg.playToClient(SyncDisabledRecipesPayload.TYPE, SyncDisabledRecipesPayload.STREAM_CODEC, SyncDisabledRecipesPayload::handle);
    }

    public static void sendRecipeToggle(String recipeId, boolean disable) {
        PacketDistributor.sendToServer(new RecipeTogglePayload(recipeId, disable));
    }

    public static void syncToAllPlayers() {
        var disabledRecipes = DisabledRecipesManager.getDisabledRecipes();
        Map<String, String> recipeJsonMap = DisabledRecipesManager.getServerRecipeJsonCache();
        List<SyncDisabledRecipesPayload.DisabledRecipeEntry> entries = new ArrayList<>();
        for (String recipeId : disabledRecipes) {
            String json = recipeJsonMap.getOrDefault(recipeId, "");
            entries.add(new SyncDisabledRecipesPayload.DisabledRecipeEntry(recipeId, json));
        }
        PacketDistributor.sendToAllPlayers(new SyncDisabledRecipesPayload(entries));
    }

    public static void syncToAllPlayers(Map<String, String> recipeJsonMap) {
        for (var entry : recipeJsonMap.entrySet()) {
            DisabledRecipesManager.serverCacheRecipeJson(entry.getKey(), entry.getValue());
        }
        syncToAllPlayers();
    }

    public static void syncEmptyToAllPlayers() {
        PacketDistributor.sendToAllPlayers(new SyncDisabledRecipesPayload(List.of()));
    }
}