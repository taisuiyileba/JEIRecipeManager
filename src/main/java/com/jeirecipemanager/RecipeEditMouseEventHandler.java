package com.jeirecipemanager;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = Jeirecipemanager.MODID, value = Dist.CLIENT)
public class RecipeEditMouseEventHandler {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMousePressedPre(ScreenEvent.MouseButtonPressed.Pre event) {
        if (RecipeIngredientTextEditHandler.handleEditClick(
            event.getScreen(),
            event.getMouseX(),
            event.getMouseY(),
            event.getButton()
        )) {
            event.setCanceled(true);
        }
    }

}
