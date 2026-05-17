package com.jeirecipemanager;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;

@Mod(Jeirecipemanager.MODID)
public class Jeirecipemanager
{
    public static final String MODID = "jeirecipemanager";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Jeirecipemanager(IEventBus modEventBus, ModContainer modContainer)
    {
        modEventBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.register(this);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
        LOGGER.info("JEIRecipeManager common setup");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
        DisabledRecipesManager.serverInit();
        LOGGER.info("JEIRecipeManager server starting, loaded {} disabled recipes",
            DisabledRecipesManager.getDisabledRecipes().size());
    }

    @EventBusSubscriber(modid = MODID, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            LOGGER.info("HELLO FROM CLIENT SETUP");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        }
        
        @SubscribeEvent
        public static void registerCommands(RegisterClientCommandsEvent event) {
            CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
            
            // 注册 /jrm showDisabled <true|false> 命令
            dispatcher.register(
                LiteralArgumentBuilder.<CommandSourceStack>literal("jrm")
                    .then(LiteralArgumentBuilder.<CommandSourceStack>literal("showDisabled")
                        .then(RequiredArgumentBuilder.<CommandSourceStack, Boolean>argument("value", BoolArgumentType.bool())
                            .executes(context -> {
                                boolean value = BoolArgumentType.getBool(context, "value");
                                JeiRecipeManagerPlugin.setShowDisabledRecipes(value);
                                String message = value ? "已启用显示禁用配方" : "已隐藏禁用配方";
                                context.getSource().sendSystemMessage(Component.literal(message));
                                return 1;
                            })
                        )
                    )
            );
        }
    }
}
