package com.exloran.hitx;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
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
        // 50+ Özellik Kapasiteli Modül Kaydı
        registerModules();

        // Sağ Shift (Menü Tuşu)
        guiKey = new KeyBinding("HitX GUI", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "HitX");

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            // Menüyü Açar (Ekranı dondurur, mouse çıkarır)
            if (guiKey.wasPressed()) {
                client.setScreen(new ClickGUI());
            }
            
            // Aktif modülleri çalıştır
            for (Module m : modules) {
                if (m.isEnabled()) m.onUpdate(client);
            }
        });

        // ArrayList (Sağ Üst Liste) Çizimi
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> renderArrayList(drawContext));
    }

    private void registerModules() {
        // --- COMBAT ---
        modules.add(new Module("Killaura", "Otomatik vuruş", true));
        modules.add(new Module("TargetStrafe", "Hedef etrafında dönme", false));
        modules.add(new Module("AutoCrit", "Kritik vuruş", false));
        modules.add(new Module("Reach", "Uzaktan vurma", false));

        // --- MOVEMENT ---
        modules.add(new Module("ElytraFly", "Sonsuz uçuş", false));
        modules.add(new Module("Speed", "Hızlı koşu", false));
        modules.add(new Module("Flight", "Uçma hilesi", false));
        modules.add(new Module("NoFall", "Düşme hasarı yok", false));
        modules.add(new Module("Spider", "Duvara tırmanma", false));
        modules.add(new Module("Jesus", "Suda yürüme", false));

        // --- VISUAL ---
        modules.add(new Module("ESP", "Wallhack", true));
        modules.add(new Module("FullBright", "Gece görüşü", false));
        modules.add(new Module("Tracers", "Oyuncu çizgileri", false));

        // --- MISC ---
        modules.add(new Module("AutoEat", "Otomatik yemek", false));
        modules.add(new Module("ChestStealer", "Sandık boşaltma", false));

        // Listeyi şık görünmesi için uzunluğa göre diz (ElitBox stili)
        modules.sort(Comparator.comparingInt(m -> -MinecraftClient.getInstance().textRenderer.getWidth(m.getName())));
    }

    private void renderArrayList(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.currentScreen instanceof ClickGUI) return;

        int y = 5;
        for (Module m : modules) {
            if (m.isEnabled()) {
                int textWidth = client.textRenderer.getWidth(m.getName());
                int x = client.getWindow().getScaledWidth() - textWidth - 10;
                
                // RGB Rainbow Efekti
                float hue = (System.currentTimeMillis() % 4000L) / 4000f;
                int color = Color.HSBtoRGB(hue, 0.7f, 1f);
                
                context.fill(x - 4, y - 2, client.getWindow().getScaledWidth(), y + 10, 0x90000000); // Arka plan barı
                context.drawText(client.textRenderer, m.getName(), x, y, color, true);
                y += 11;
            }
        }
    }

    // 🖱️ DOOMSDAY STİLİ CLICK GUI
    public static class ClickGUI extends Screen {
        protected ClickGUI() { super(Text.of("HitX Menu")); }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            this.renderBackground(context, mouseX, mouseY, delta);
            int x = 50, y = 50;
            
            // Ana Panel
            context.fill(x - 5, y - 10, x + 160, y + 250, 0xDD0A0A0A);
            context.drawText(this.textRenderer, "§d§lHitX §f- ELITE HCK", x + 5, y, 0xFFFFFF, true);
            context.fill(x, y + 12, x + 150, y + 13, 0xFFd32f2f); // Alt çizgi

            int modY = y + 25;
            for (Module m : modules) {
                boolean hovered = mouseX >= x && mouseX <= x + 150 && mouseY >= modY && mouseY <= modY + 11;
                int color = m.isEnabled() ? 0xFF55FF55 : 0xFFFFFFFF;
                
                if (hovered) {
                    context.fill(x, modY - 1, x + 150, modY + 10, 0x44FFFFFF); // Hover efekti
                }

                String toggleStatus = m.isEnabled() ? "§a[ON]" : "§c[OFF]";
                context.drawText(this.textRenderer, m.getName() + " " + toggleStatus, x + 10, modY, color, false);
                modY += 13;
            }
            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int x = 50, modY = 75;
            for (Module m : modules) {
                if (mouseX >= x && mouseX <= x + 150 && mouseY >= modY && mouseY <= modY + 12) {
                    m.toggle();
                    // Tıklama sesi
                    MinecraftClient.getInstance().player.playSound(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value(), 0.6f, 1.2f);
                    return true;
                }
                modY += 13;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override public boolean shouldPause() { return false; } // Oyunu durdurmaz
    }

    // 📦 MODÜL MANTIĞI
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
            if (client.player == null) return;

            // 🛠️ HİLE FONKSİYONLARI (1.21 UYUMLU)
            if (name.equals("FullBright")) client.options.getGamma().setValue(100.0);
            
            if (name.equals("Flight")) client.player.getAbilities().flying = true;

            if (name.equals("NoFall")) {
                if (client.player.fallDistance > 2.5f) {
                    // Paket hatasını çözen kısım:
                    client.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true));
                }
            }
            
            if (name.equals("Spider")) {
                if (client.player.horizontalCollision) client.player.setVelocity(client.player.getVelocity().x, 0.2, client.player.getVelocity().z);
            }
        }
    }
}
