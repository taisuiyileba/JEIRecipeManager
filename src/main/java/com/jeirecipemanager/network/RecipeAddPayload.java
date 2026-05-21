package com.jeirecipemanager.network;

import com.jeirecipemanager.GeneratedRecipesManager;
import com.jeirecipemanager.RecipeEditManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

public record RecipeAddPayload(String templateRecipeId, List<RecipeEditManager.SlotReplacement> replacements) implements CustomPacketPayload {
    public static final Type<RecipeAddPayload> TYPE = new Type<>(
        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("jeirecipemanager", "recipe_add")
    );

    public static final StreamCodec<ByteBuf, RecipeAddPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, RecipeAddPayload::templateRecipeId,
        SlotReplacementCodec.STREAM_CODEC.apply(ByteBufCodecs.list()), RecipeAddPayload::replacements,
        RecipeAddPayload::new
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
            serverPlayer.sendSystemMessage(Component.translatable("jeirecipemanager.message.recipe_add.no_permission"));
            return;
        }
        ctx.enqueueWork(() -> {
            GeneratedRecipesManager.Result result = GeneratedRecipesManager.addRecipeFromTemplate(templateRecipeId, replacements);
            if (result.success()) {
                serverPlayer.sendSystemMessage(Component.translatable(result.modified()
                    ? "jeirecipemanager.message.recipe_add.modify_success"
                    : "jeirecipemanager.message.recipe_add.success"));
            } else {
                serverPlayer.sendSystemMessage(Component.translatable("jeirecipemanager.message.recipe_add.failure"));
            }
        });
    }

    private record SlotReplacementCodec(String role, int slotIndex, String itemId, int count) {
        private static final StreamCodec<ByteBuf, RecipeEditManager.SlotReplacement> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RecipeEditManager.SlotReplacement::role,
            ByteBufCodecs.VAR_INT, RecipeEditManager.SlotReplacement::slotIndex,
            ByteBufCodecs.STRING_UTF8, RecipeEditManager.SlotReplacement::itemId,
            ByteBufCodecs.VAR_INT, RecipeEditManager.SlotReplacement::count,
            RecipeEditManager.SlotReplacement::new
        );
    }
}
