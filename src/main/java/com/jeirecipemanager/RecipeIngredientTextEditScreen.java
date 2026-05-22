package com.jeirecipemanager;

import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class RecipeIngredientTextEditScreen extends Screen {
    private static final int PANEL_WIDTH = 280;
    private static final int PANEL_HEIGHT = 88;

    private final Screen parent;
    private final String recipeId;
    private final List<IRecipeSlotView> slots;
    private final IRecipeSlotDrawable slot;
    private final String initialValue;
    private EditBox input;
    private Button confirmButton;

    protected RecipeIngredientTextEditScreen(Screen parent, String recipeId, List<IRecipeSlotView> slots, IRecipeSlotDrawable slot, String initialValue) {
        super(Component.translatable("jeirecipemanager.screen.ingredient_edit.title"));
        this.parent = parent;
        this.recipeId = recipeId;
        this.slots = slots;
        this.slot = slot;
        this.initialValue = initialValue;
    }

    @Override
    protected void init() {
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;

        this.input = new EditBox(this.font, left + 12, top + 28, PANEL_WIDTH - 24, 20, Component.translatable("jeirecipemanager.screen.ingredient_edit.input"));
        this.input.setMaxLength(256);
        this.input.setValue(initialValue);
        this.input.setResponder(value -> updateConfirmButton());
        this.addRenderableWidget(this.input);

        this.confirmButton = Button.builder(Component.translatable("jeirecipemanager.screen.ingredient_edit.confirm"), button -> confirm())
            .bounds(left + PANEL_WIDTH - 164, top + 58, 72, 20)
            .build();
        this.addRenderableWidget(this.confirmButton);

        this.addRenderableWidget(Button.builder(Component.translatable("jeirecipemanager.screen.ingredient_edit.cancel"), button -> close())
            .bounds(left + PANEL_WIDTH - 84, top + 58, 72, 20)
            .build());

        this.setInitialFocus(this.input);
        updateConfirmButton();
    }

    private void updateConfirmButton() {
        if (confirmButton != null && input != null) {
            confirmButton.active = RecipeEditManager.isValidInputText(input.getValue());
        }
    }

    private void confirm() {
        RecipeEditManager.replaceInputSlotText(recipeId, slots, slot, input.getValue());
        close();
    }

    @Override
    public void onClose() {
        close();
    }

    private void close() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            if (RecipeEditManager.isValidInputText(input.getValue())) {
                confirm();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        int left = (this.width - PANEL_WIDTH) / 2;
        int top = (this.height - PANEL_HEIGHT) / 2;
        guiGraphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xF0101010);
        guiGraphics.fill(left, top, left + PANEL_WIDTH, top + 1, 0xFF707070);
        guiGraphics.fill(left, top + PANEL_HEIGHT - 1, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFF707070);
        guiGraphics.fill(left, top, left + 1, top + PANEL_HEIGHT, 0xFF707070);
        guiGraphics.fill(left + PANEL_WIDTH - 1, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xFF707070);
        guiGraphics.drawString(this.font, this.title, left + 12, top + 10, 0xFFE0E0E0, false);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
}
