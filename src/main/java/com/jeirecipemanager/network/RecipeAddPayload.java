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

    private record SlotReplacementCodec(String role, int slotIndex, String itemId, int count, int gridWidth, int gridHeight, String ingredientKind, String extraId) {
        private static final StreamCodec<ByteBuf, RecipeEditManager.SlotReplacement> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public RecipeEditManager.SlotReplacement decode(ByteBuf buf) {
                return new RecipeEditManager.SlotReplacement(
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf)
                );
            }

            @Override
            public void encode(ByteBuf buf, RecipeEditManager.SlotReplacement replacement) {
                ByteBufCodecs.STRING_UTF8.encode(buf, replacement.role());
                ByteBufCodecs.VAR_INT.encode(buf, replacement.slotIndex());
                ByteBufCodecs.STRING_UTF8.encode(buf, replacement.itemId());
                ByteBufCodecs.VAR_INT.encode(buf, replacement.count());
                ByteBufCodecs.VAR_INT.encode(buf, replacement.gridWidth());
                ByteBufCodecs.VAR_INT.encode(buf, replacement.gridHeight());
                ByteBufCodecs.STRING_UTF8.encode(buf, replacement.ingredientKind());
                ByteBufCodecs.STRING_UTF8.encode(buf, replacement.extraId());
            }
        };
    }
}
