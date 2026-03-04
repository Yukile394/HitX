package com.exloran.hitx;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class HitX implements ClientModInitializer {

    // 🚩 Hile Modülleri Listesi
    private static final List<Module> modules = new ArrayList<>();
    private static boolean guiOpen = false;
    private static KeyBinding guiKey;

    @Override
    public void onInitializeClient() {
        // ✨ Modülleri Kaydet (Buraya 50+ özellik eklenebilir)
        registerModules();

        // 🎹 Sağ Shift veya Sağ Click için tuş ataması
        guiKey = new KeyBinding("HitX GUI", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "HitX");

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (guiKey.wasPressed()) {
                guiOpen = !guiOpen;
                client.player.sendMessage(Text.of("§d[HitX] §fMenü: " + (guiOpen ? "§aAçık" : "§cKapalı")), true);
            }
            
            // Aktif olan hilelerin her tick çalışması
            for (Module m : modules) {
                if (m.isEnabled()) m.onUpdate(client);
            }
        });

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> renderVisuals(drawContext));
    }

    private void registerModules() {
        // ⚔️ SALDIRI (Combat)
        modules.add(new Module("Killaura", "Çevredeki herkese otomatik vurur.", true));
        modules.add(new Module("TriggerBot", "Baktığın hedefe otomatik vurur.", false));
        modules.add(new Module("AutoCrit", "Her vuruşta kritik hasar verir.", false));
        modules.add(new Module("Reach", "Daha uzaktan vurmanı sağlar.", false));

        // 🕊️ HAREKET (Movement)
        modules.add(new Module("ElytraFly", "Elytra ile sonsuz uçuş sağlar.", false));
        modules.add(new Module("Speed", "Normalden hızlı koşmanı sağlar.", false));
        modules.add(new Module("Spider", "Duvarlara tırmanmanı sağlar.", false));
        modules.add(new Module("Jesus", "Su üzerinde yürümeni sağlar.", false));
        modules.add(new Module("AutoWalk", "Otomatik ileri yürür.", false));

        // 👁️ GÖRSEL (Visual)
        modules.add(new Module("ESP", "Oyuncuları duvar arkasından gösterir.", true));
        modules.add(new Module("FullBright", "Karanlığı tamamen aydınlatır.", false));
        modules.add(new Module("Tracers", "Oyunculara doğru çizgiler çeker.", false));
        modules.add(new Module("X-Ray", "Madenleri duvar arkasından gösterir.", false));

        // 🛠️ DİĞER (Misc)
        modules.add(new Module("AutoEat", "Acıkınca otomatik yemek yer.", false));
        modules.add(new Module("NoFall", "Düşme hasarını engeller.", false));
        modules.add(new Module("ChestStealer", "Sandıkları anında boşaltır.", false));
        // ... (Burada liste 50'ye kadar uzatılabilir)
    }

    private void renderVisuals(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        // 🔥 Sağ Üst Liste (Aktif Hileler)
        int yOffset = 10;
        for (Module m : modules) {
            if (m.isEnabled()) {
                float hue = (System.currentTimeMillis() % 2000L) / 2000f;
                int color = java.awt.Color.HSBtoRGB(hue, 1f, 1f);
                context.drawText(client.textRenderer, "» " + m.getName(), 10, yOffset, color, true);
                yOffset += 10;
            }
        }

        // 🎨 Menü Arayüzü (Basit ClickGUI Taslağı)
        if (guiOpen) {
            renderGUI(context, client);
        }
    }

    private void renderGUI(DrawContext context, MinecraftClient client) {
        int x = 50, y = 50;
        context.fill(45, 45, 180, 250, 0xAA000000); // Arka plan
        context.drawText(client.textRenderer, "§d§lHitX - DOOMSDAY HCK", 55, 55, 0xFFFFFF, false);
        
        int modY = 70;
        for (Module m : modules) {
            String status = m.isEnabled() ? "§a[ON]" : "§c[OFF]";
            context.drawText(client.textRenderer, m.getName() + " " + status, 60, modY, 0xFFFFFF, false);
            modY += 12;
        }
    }

    // 📦 Modül Sınıfı
    private static class Module {
        private String name, description;
        private boolean enabled;

        public Module(String name, String desc, boolean enabled) {
            this.name = name;
            this.description = desc;
            this.enabled = enabled;
        }

        public String getName() { return name; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public void onUpdate(MinecraftClient client) {
            // Hilenin asıl mantığı buraya gelir
            if (name.equals("FullBright")) client.options.getGamma().setValue(100.0);
            if (name.equals("AutoWalk")) client.options.forwardKey.setPressed(true);
        }
    }
}
