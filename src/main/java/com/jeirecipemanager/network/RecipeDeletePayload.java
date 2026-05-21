package com.jeirecipemanager.network;

import com.jeirecipemanager.GeneratedRecipesManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RecipeDeletePayload(String recipeId) implements CustomPacketPayload {
    public static final Type<RecipeDeletePayload> TYPE = new Type<>(
        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("jeirecipemanager", "recipe_delete")
    );

    public static final StreamCodec<ByteBuf, RecipeDeletePayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, RecipeDeletePayload::recipeId,
        RecipeDeletePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public void handle(IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (!serverPlayer.hasPermissions(2)) {
            serverPlayer.sendSystemMessage(Component.translatable("jeirecipemanager.message.recipe_delete.no_permission"));
            return;
        }
        ctx.enqueueWork(() -> {
            boolean success = GeneratedRecipesManager.deleteGeneratedRecipe(recipeId);
            serverPlayer.sendSystemMessage(Component.translatable(success
                ? "jeirecipemanager.message.recipe_delete.success"
                : "jeirecipemanager.message.recipe_delete.failure"));
        });
    }
}
