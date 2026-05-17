package com.jeirecipemanager.network;

import com.jeirecipemanager.DisabledRecipesManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record RecipeTogglePayload(String recipeId, boolean disable) implements CustomPacketPayload {
    public static final Type<RecipeTogglePayload> TYPE = new Type<>(
        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("jeirecipemanager", "recipe_toggle")
    );

    public static final StreamCodec<ByteBuf, RecipeTogglePayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, RecipeTogglePayload::recipeId,
        ByteBufCodecs.BOOL, RecipeTogglePayload::disable,
        RecipeTogglePayload::new
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
            serverPlayer.sendSystemMessage(Component.translatable("jeirecipemanager.message.recipe_toggle.no_permission"));
            NetworkHandler.syncToPlayer(serverPlayer);
            return;
        }
        ctx.enqueueWork(() -> {
            if (disable) {
                DisabledRecipesManager.serverDisableRecipe(recipeId);
            } else {
                DisabledRecipesManager.serverEnableRecipe(recipeId);
            }
            NetworkHandler.syncToAllPlayers();
        });
    }
}
