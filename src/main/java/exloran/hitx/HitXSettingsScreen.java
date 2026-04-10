package exloran.hitx;

import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class HitXSettingsScreen extends Screen {
    
    public HitXSettingsScreen() { 
        super(Text.literal("HitX Menu")); 
    }

    @Override
    protected void init() {
        // Config dosyasını çekiyoruz
        HitXConfig config = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();
        
        int x = width / 2 - 60;
        int y = height / 2 - 40;

        // 1. Hitbox Aktif/Pasif Butonu
        addDrawableChild(ButtonWidget.builder(Text.literal("HitBox: " + (HitX.hitBoxActive ? "§aON" : "§cOFF")), b -> {
            HitX.hitBoxActive = !HitX.hitBoxActive;
            b.setMessage(Text.literal("HitBox: " + (HitX.hitBoxActive ? "§aON" : "§cOFF")));
        }).dimensions(x, y, 120, 20).build());

        // 2. Genişlik Ayarı (xzExpand artık config içinde)
        addDrawableChild(ButtonWidget.builder(Text.literal("Genişlik: " + String.format("%.1f", config.xzExpand)), b -> {
            config.xzExpand = (config.xzExpand >= 5.0f) ? 0.5f : config.xzExpand + 0.5f;
            // Config'i otomatik kaydet
            AutoConfig.getConfigHolder(HitXConfig.class).save();
            b.setMessage(Text.literal("Genişlik: " + String.format("%.1f", config.xzExpand)));
        }).dimensions(x, y + 25, 120, 20).build());
        
        // 3. Fake Hitbox Butonu (Yeni eklediğimiz özellik için)
        addDrawableChild(ButtonWidget.builder(Text.literal("Fake Mod: " + (config.fakeHitbox ? "§aON" : "§cOFF")), b -> {
            config.fakeHitbox = !config.fakeHitbox;
            AutoConfig.getConfigHolder(HitXConfig.class).save();
            b.setMessage(Text.literal("Fake Mod: " + (config.fakeHitbox ? "§aON" : "§cOFF")));
        }).dimensions(x, y + 50, 120, 20).build());

        // 4. Kapat Butonu
        addDrawableChild(ButtonWidget.builder(Text.literal("Kapat"), b -> close())
            .dimensions(x, y + 80, 120, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Arka plan karartma
        ctx.fill(0, 0, width, height, 0x99000000);
        
        // Başlık
        ctx.drawCenteredTextWithShadow(textRenderer, "§lHitX Settings", width / 2, height / 2 - 60, 0xFFFFFF);
        
        super.render(ctx, mouseX, mouseY, delta);
    }
    
    @Override
    public boolean shouldPause() {
        return false; // Menü açıldığında oyun durmasın (Serverlarda sıkıntı çıkmaz)
    }
}
