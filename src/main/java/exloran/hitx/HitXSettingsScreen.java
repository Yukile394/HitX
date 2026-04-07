package com.exloran.hitx;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class HitXSettingsScreen extends Screen {
    protected HitXSettingsScreen() { super(Text.literal("HitX Settings")); }

    @Override
    protected void init() {
        int x = width / 2 - 60;
        int y = height / 2 - 40;

        addDrawableChild(ButtonWidget.builder(Text.literal("HitBox: " + (HitX.hitBoxActive ? "ON" : "OFF")), b -> {
            HitX.hitBoxActive = !HitX.hitBoxActive;
            b.setMessage(Text.literal("HitBox: " + (HitX.hitBoxActive ? "ON" : "OFF")));
        }).dimensions(x, y, 120, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("XZ Genişlik: " + HitX.xzExpand), b -> {
            HitX.xzExpand = (HitX.xzExpand >= 1.0f) ? 0.1f : HitX.xzExpand + 0.1f;
            b.setMessage(Text.literal("XZ Genişlik: " + String.format("%.1f", HitX.xzExpand)));
        }).dimensions(x, y + 25, 120, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, "§dHit§fX §7Premium Settings", width / 2, height / 2 - 60, -1);
        super.render(ctx, mouseX, mouseY, delta);
    }
}
