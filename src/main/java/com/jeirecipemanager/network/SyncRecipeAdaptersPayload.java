package com.jeirecipemanager.network;

import com.jeirecipemanager.RecipeAdapterManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

public record SyncRecipeAdaptersPayload(List<String> adapters) implements CustomPacketPayload {
    public static final Type<SyncRecipeAdaptersPayload> TYPE = new Type<>(
        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("jeirecipemanager", "sync_recipe_adapters")
    );

    public static final StreamCodec<ByteBuf, SyncRecipeAdaptersPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), SyncRecipeAdaptersPayload::adapters,
        SyncRecipeAdaptersPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> RecipeAdapterManager.clientUpdateAdapters(adapters));
    }
}
