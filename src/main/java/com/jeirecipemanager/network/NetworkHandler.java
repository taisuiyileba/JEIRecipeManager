package com.jeirecipemanager.network;

import com.jeirecipemanager.DisabledRecipesManager;
import com.jeirecipemanager.Jeirecipemanager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

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
        PacketDistributor.sendToAllPlayers(new SyncDisabledRecipesPayload(disabledRecipes.stream().toList()));
    }

    public static void syncEmptyToAllPlayers() {
        PacketDistributor.sendToAllPlayers(new SyncDisabledRecipesPayload(java.util.List.of()));
    }
}
