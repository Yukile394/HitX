package com.exloran.hitx;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class HitX implements ClientModInitializer {

    public static final List<Module> modules = new ArrayList<>();
    private static KeyBinding guiKey;

    @Override
    public void onInitializeClient() {
        registerModules();

        // 🎹 Sağ Shift Menüyü Açar
        guiKey = new KeyBinding("HitX GUI", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "HitX");

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (guiKey.wasPressed() && client.player != null) {
                // Ekranı dondurur ve mouse çıkartır 🖱️
                client.setScreen(new ClickGUI());
            }
            
            for (Module m : modules) {
                if (m.isEnabled()) m.onUpdate(client);
            }
        });

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> renderArrayList(drawContext));
    }

    private void registerModules() {
        // ⚔️ COMBAT
        modules.add(new Module("Killaura", "Otomatik saldırı", true));
        modules.add(new Module("TargetStrafe", "Hedef etrafında dönme", false));
        modules.add(new Module("AutoCrit", "Sürekli kritik", false));
        
        // 🕊️ MOVEMENT
        modules.add(new Module("ElytraFly", "Sonsuz uçuş", false));
        modules.add(new Module("Speed", "Hızlı hareket", false));
        modules.add(new Module("Flight", "Yaratıcı mod uçuşu", false));
        modules.add(new Module("NoFall", "Hasar engelleme", false));
        
        // 👁️ VISUAL
        modules.add(new Module("ESP", "Wallhack", true));
        modules.add(new Module("Tracers", "Oyuncu çizgileri", false));
        modules.add(new Module("FullBright", "Gece görüşü", false));
        
        // Uzunluk sırasına göre diz (Şık görünmesi için)
        modules.sort(Comparator.comparingInt(m -> -MinecraftClient.getInstance().textRenderer.getWidth(m.getName())));
    }

    // 🌈 Sağ Üst Şık Liste (ArrayList)
    private void renderArrayList(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.currentScreen instanceof ClickGUI) return;

        int y = 5;
        for (Module m : modules) {
            if (m.isEnabled()) {
                int textWidth = client.textRenderer.getWidth(m.getName());
                int x = client.getWindow().getScaledWidth() - textWidth - 10;
                
                // RGB Efekti
                float hue = (System.currentTimeMillis() % 4000L) / 4000f;
                int color = Color.HSBtoRGB(hue, 0.8f, 1f);
                
                context.fill(x - 4, y - 2, client.getWindow().getScaledWidth(), y + 10, 0x90000000);
                context.drawText(client.textRenderer, m.getName(), x, y, color, true);
                y += 11;
            }
        }
    }

    // 🖱️ Mouse Destekli Tıklanabilir Menü
    public static class ClickGUI extends Screen {
        protected ClickGUI() { super(Text.of("HitX Menu")); }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            this.renderBackground(context, mouseX, mouseY, delta);
            int x = 40, y = 40;
            
            // Başlık
            context.fill(x - 5, y - 10, x + 150, y + 200, 0xCC101010);
            context.drawText(this.textRenderer, "§d§lHitX §fElite v2", x + 5, y, 0xFFFFFF, true);
            
            int modY = y + 20;
            for (Module m : modules) {
                int color = m.isEnabled() ? 0x00FF00 : 0xFFFFFF;
                String prefix = m.isEnabled() ? "§a[X] " : "§c[ ] ";
                
                // Mouse üzerine gelince parlasın
                if (mouseX >= x && mouseX <= x + 140 && mouseY >= modY && mouseY <= modY + 10) {
                    context.fill(x, modY - 2, x + 140, modY + 10, 0x44FFFFFF);
                }

                context.drawText(this.textRenderer, prefix + m.getName(), x + 5, modY, color, false);
                modY += 12;
            }
            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int x = 40, modY = 60;
            for (Module m : modules) {
                if (mouseX >= x && mouseX <= x + 140 && mouseY >= modY && mouseY <= modY + 12) {
                    m.toggle();
                    MinecraftClient.getInstance().player.playSound(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value(), 0.5f, 1f);
                    return true;
                }
                modY += 12;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean shouldPause() { return false; }
    }

    // 📦 Gelişmiş Modül Yapısı
    public static class Module {
        private String name, desc;
        private boolean enabled;

        public Module(String name, String desc, boolean enabled) {
            this.name = name; this.desc = desc; this.enabled = enabled;
        }

        public String getName() { return name; }
        public boolean isEnabled() { return enabled; }
        public void toggle() { this.enabled = !this.enabled; }

        public void onUpdate(MinecraftClient client) {
            if (name.equals("FullBright")) client.options.getGamma().setValue(100.0);
            if (name.equals("Flight")) client.player.getAbilities().flying = true;
            if (name.equals("NoFall")) {
                if (client.player.fallDistance > 2) client.player.networkHandler.sendPacket(new net.minecraft.network.packet.c2s.play.PlayerMoveC2S.OnGroundOnly(true, false));
            }
        }
    }
}
