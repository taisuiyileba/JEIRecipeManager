package com.jeirecipemanager.network;

import com.jeirecipemanager.GeneratedRecipesManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

public record SyncPendingRecipeDeletesPayload(List<String> recipeIds) implements CustomPacketPayload {
    public static final Type<SyncPendingRecipeDeletesPayload> TYPE = new Type<>(
        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("jeirecipemanager", "sync_pending_recipe_deletes")
    );

    public static final StreamCodec<ByteBuf, SyncPendingRecipeDeletesPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), SyncPendingRecipeDeletesPayload::recipeIds,
        SyncPendingRecipeDeletesPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> GeneratedRecipesManager.clientUpdatePendingGeneratedRecipeDeletes(recipeIds));
    }
}
