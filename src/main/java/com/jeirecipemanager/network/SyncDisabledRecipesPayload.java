package com.jeirecipemanager.network;

import com.jeirecipemanager.DisabledRecipesManager;
import com.jeirecipemanager.JeiRecipeManagerPlugin;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record SyncDisabledRecipesPayload(List<DisabledRecipeEntry> recipes) implements CustomPacketPayload {
    public static final Type<SyncDisabledRecipesPayload> TYPE = new Type<>(
        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("jeirecipemanager", "sync_disabled_recipes")
    );

    public static final StreamCodec<ByteBuf, SyncDisabledRecipesPayload> STREAM_CODEC = StreamCodec.composite(
        DisabledRecipeEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), SyncDisabledRecipesPayload::recipes,
        SyncDisabledRecipesPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            List<String> recipeIds = new ArrayList<>();
            Map<String, String> recipeJsonMap = new HashMap<>();
            for (DisabledRecipeEntry entry : recipes) {
                recipeIds.add(entry.recipeId());
                if (!entry.recipeJson().isEmpty()) {
                    recipeJsonMap.put(entry.recipeId(), entry.recipeJson());
                }
            }
            DisabledRecipesManager.clientUpdateDisabledRecipes(recipeIds, recipeJsonMap);
            // 触发JEI更新配方可见性
            JeiRecipeManagerPlugin.updateRecipeVisibility();
        });
    }

    public record DisabledRecipeEntry(String recipeId, String recipeJson) {
        public static final StreamCodec<ByteBuf, DisabledRecipeEntry> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, DisabledRecipeEntry::recipeId,
            ByteBufCodecs.STRING_UTF8, DisabledRecipeEntry::recipeJson,
            DisabledRecipeEntry::new
        );
    }
}