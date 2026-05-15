package com.jeirecipemanager.network;

import com.jeirecipemanager.DisabledRecipesManager;
import com.jeirecipemanager.JeiRecipeManagerPlugin;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

public record SyncDisabledRecipesPayload(List<String> disabledRecipes) implements CustomPacketPayload {
    public static final Type<SyncDisabledRecipesPayload> TYPE = new Type<>(
        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("jeirecipemanager", "sync_disabled_recipes")
    );

    public static final StreamCodec<ByteBuf, SyncDisabledRecipesPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), SyncDisabledRecipesPayload::disabledRecipes,
        SyncDisabledRecipesPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            DisabledRecipesManager.clientUpdateDisabledRecipes(disabledRecipes);
        });
    }
}
