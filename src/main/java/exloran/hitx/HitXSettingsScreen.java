package exloran.hitx;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class HitXSettingsScreen extends Screen {
    public HitXSettingsScreen() { super(Text.literal("HitX Menu")); }

    @Override
    protected void init() {
        int x = width / 2 - 60;
        int y = height / 2 - 30;

        // HitBox Toggle
        addDrawableChild(ButtonWidget.builder(Text.literal("HitBox: " + (HitX.hitBoxActive ? "§aON" : "§cOFF")), b -> {
            HitX.hitBoxActive = !HitX.hitBoxActive;
            b.setMessage(Text.literal("HitBox: " + (HitX.hitBoxActive ? "§aON" : "§cOFF")));
        }).dimensions(x, y, 120, 20).build());

        // XZ Slider (Basit)
        addDrawableChild(ButtonWidget.builder(Text.literal("XZ Genişlik: §d" + String.format("%.1f", HitX.xzExpand)), b -> {
            HitX.xzExpand = (HitX.xzExpand >= 1.0f) ? 0.1f : HitX.xzExpand + 0.1f;
            b.setMessage(Text.literal("XZ Genişlik: §d" + String.format("%.1f", HitX.xzExpand)));
        }).dimensions(x, y + 25, 120, 20).build());

        // Kapat
        addDrawableChild(ButtonWidget.builder(Text.literal("§fTamam"), b -> close()).dimensions(x, y + 55, 120, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0x99000000);
        int color = HitX.getPinkWhiteFlop(0, 1.0f);
        ctx.fill(width/2 - 70, height/2 - 50, width/2 + 70, height/2 + 85, 0xCC050505);
        ctx.fill(width/2 - 70, height/2 - 51, width/2 + 70, height/2 - 50, color);
        ctx.drawCenteredTextWithShadow(textRenderer, "§dHit§fX §7V2", width / 2, height / 2 - 45, -1);
        super.render(ctx, mouseX, mouseY, delta);
    }
}
