package com.jeirecipemanager.network;

import com.jeirecipemanager.DisabledRecipesManager;
import com.jeirecipemanager.GeneratedRecipesManager;
import com.jeirecipemanager.Jeirecipemanager;
import com.jeirecipemanager.RecipeAdapterManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
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
        reg.playToServer(RecipeAddPayload.TYPE, RecipeAddPayload.STREAM_CODEC, RecipeAddPayload::handle);
        reg.playToServer(RecipeDeletePayload.TYPE, RecipeDeletePayload.STREAM_CODEC, RecipeDeletePayload::handle);
        reg.playToClient(SyncDisabledRecipesPayload.TYPE, SyncDisabledRecipesPayload.STREAM_CODEC, SyncDisabledRecipesPayload::handle);
        reg.playToClient(SyncPendingRecipeDeletesPayload.TYPE, SyncPendingRecipeDeletesPayload.STREAM_CODEC, SyncPendingRecipeDeletesPayload::handle);
        reg.playToClient(SyncRecipeAdaptersPayload.TYPE, SyncRecipeAdaptersPayload.STREAM_CODEC, SyncRecipeAdaptersPayload::handle);
    }

    public static void sendRecipeToggle(String recipeId, boolean disable) {
        PacketDistributor.sendToServer(new RecipeTogglePayload(recipeId, disable));
    }

    public static void sendRecipeAdd(String templateRecipeId, List<com.jeirecipemanager.RecipeEditManager.SlotReplacement> replacements) {
        PacketDistributor.sendToServer(new RecipeAddPayload(templateRecipeId, replacements));
    }

    public static void sendRecipeDelete(String recipeId, boolean delete) {
        PacketDistributor.sendToServer(new RecipeDeletePayload(recipeId, delete));
    }

    public static void syncToAllPlayers() {
        SyncDisabledRecipesPayload payload = createSyncPayload();
        SyncPendingRecipeDeletesPayload pendingDeletesPayload = createPendingDeletesPayload();
        SyncRecipeAdaptersPayload adaptersPayload = createRecipeAdaptersPayload();
        for (ServerPlayer player : net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers()) {
            sendToPlayerIfSupported(player, payload);
            sendToPlayerIfSupported(player, pendingDeletesPayload);
            sendToPlayerIfSupported(player, adaptersPayload);
        }
    }

    public static void syncToAllPlayers(Map<String, String> recipeJsonMap) {
        for (var entry : recipeJsonMap.entrySet()) {
            DisabledRecipesManager.serverCacheRecipeJson(entry.getKey(), entry.getValue());
        }
        syncToAllPlayers();
    }

    public static void syncEmptyToAllPlayers() {
        SyncDisabledRecipesPayload payload = new SyncDisabledRecipesPayload(List.of());
        SyncPendingRecipeDeletesPayload pendingDeletesPayload = createPendingDeletesPayload();
        SyncRecipeAdaptersPayload adaptersPayload = createRecipeAdaptersPayload();
        for (ServerPlayer player : net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers()) {
            sendToPlayerIfSupported(player, payload);
            sendToPlayerIfSupported(player, pendingDeletesPayload);
            sendToPlayerIfSupported(player, adaptersPayload);
        }
    }

    @SubscribeEvent
    static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            syncToPlayer(serverPlayer);
        }
    }

    public static void syncToPlayer(ServerPlayer player) {
        sendToPlayerIfSupported(player, createSyncPayload());
        sendToPlayerIfSupported(player, createPendingDeletesPayload());
        sendToPlayerIfSupported(player, createRecipeAdaptersPayload());
    }

    private static SyncDisabledRecipesPayload createSyncPayload() {
        var disabledRecipes = DisabledRecipesManager.getDisabledRecipes();
        Map<String, String> recipeJsonMap = DisabledRecipesManager.getServerRecipeJsonCache();
        List<SyncDisabledRecipesPayload.DisabledRecipeEntry> entries = new ArrayList<>();
        for (String recipeId : disabledRecipes) {
            String json = recipeJsonMap.getOrDefault(recipeId, "");
            entries.add(new SyncDisabledRecipesPayload.DisabledRecipeEntry(recipeId, json));
        }
        return new SyncDisabledRecipesPayload(entries);
    }

    private static SyncPendingRecipeDeletesPayload createPendingDeletesPayload() {
        return new SyncPendingRecipeDeletesPayload(new ArrayList<>(GeneratedRecipesManager.getPendingGeneratedRecipeDeletes()));
    }

    private static SyncRecipeAdaptersPayload createRecipeAdaptersPayload() {
        return new SyncRecipeAdaptersPayload(RecipeAdapterManager.getSerializedAdapters());
    }

    private static void sendToPlayerIfSupported(ServerPlayer player, CustomPacketPayload payload) {
        if (NetworkRegistry.hasChannel(player.connection, payload.type().id())) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }
}
