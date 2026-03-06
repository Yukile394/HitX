package com.exloran.hitx;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class HitX implements ClientModInitializer {

    public static final List<Module> modules = new ArrayList<>();
    private static KeyBinding guiKey;
    private static boolean sorted = false;

    @Override
    public void onInitializeClient() {
        registerModules();
        guiKey = new KeyBinding("HitX GUI", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "HitX");

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            if (guiKey.wasPressed()) client.setScreen(new ClickGUI());
            
            for (Module m : modules) {
                if (m.isEnabled()) m.onUpdate(client);
            }
        });

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> renderArrayList(drawContext));
    }

    private void registerModules() {
        // --- AFK & MISC ---
        modules.add(new Module("AFKFarmer", "AFK Süresini Simüle Eder", false));
        modules.add(new Module("AutoWalk", "Otomatik yürür", false));
        
        // --- Diğer Modüller (Combat, Movement vb.) ---
        modules.add(new Module("Killaura", "Otomatik vurur", false));
        modules.add(new Module("Flight", "Uçuş modu", false));
        modules.add(new Module("FullBright", "Gece görüşü", true));
    }

    private void renderArrayList(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.currentScreen instanceof ClickGUI) return;

        int y = 5;
        for (Module m : modules) {
            if (m.isEnabled()) {
                int textWidth = client.textRenderer.getWidth(m.getName());
                int x = client.getWindow().getScaledWidth() - textWidth - 10;
                context.fill(x - 4, y - 2, client.getWindow().getScaledWidth(), y + 10, 0x90000000);
                context.drawText(client.textRenderer, m.getName(), x, y, 0x00FFFF, true);
                y += 11;
            }
        }
    }

    public static class ClickGUI extends Screen {
        protected ClickGUI() { super(Text.of("HitX Menu")); }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(0, 0, this.width, this.height, 0x55000000);
            
            // Sol Üst Ödül Butonu
            int btnX = 10, btnY = 10;
            boolean hovered = mouseX >= btnX && mouseX <= btnX + 80 && mouseY >= btnY && mouseY <= btnY + 15;
            context.fill(btnX, btnY, btnX + 80, btnY + 15, hovered ? 0xFF00FFFF : 0xEE111111);
            context.drawText(this.textRenderer, "ÖDÜLÜ AL", btnX + 15, btnY + 4, 0xFFFFFF, true);

            // Ana Menü
            int x = 50, y = 50;
            context.fill(x - 5, y - 10, x + 160, y + 200, 0xEE111111);
            context.drawText(this.textRenderer, "§bHitX §f| §7AFK Modu", x + 5, y, 0xFFFFFF, true);

            for (int i = 0; i < modules.size(); i++) {
                Module m = modules.get(i);
                int modY = y + 25 + (i * 14);
                context.drawText(this.textRenderer, (m.isEnabled() ? "§a✔ " : "§c✖ ") + m.getName(), x + 10, modY, 0xBBBBBB, false);
            }
            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            // Ödül butonu tıklama kontrolü
            if (mouseX >= 10 && mouseX <= 90 && mouseY >= 10 && mouseY <= 25) {
                sendInstantPackets();
                return true;
            }

            // Modül tıklama kontrolü
            int x = 50, y = 75;
            for (int i = 0; i < modules.size(); i++) {
                if (mouseX >= x && mouseX <= x + 150 && mouseY >= y + (i * 14) && mouseY <= y + (i * 14) + 12) {
                    modules.get(i).toggle();
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        private void sendInstantPackets() {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                client.player.sendMessage(Text.of("§b[HitX] §fÖdül paketleri gönderiliyor..."), true);
                for (int i = 0; i < 50; i++) {
                    client.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true));
                }
            }
        }
    }

    public static class Module {
        private final String name, desc;
        private boolean enabled;

        public Module(String name, String desc, boolean enabled) {
            this.name = name; this.desc = desc; this.enabled = enabled;
        }

        public String getName() { return name; }
        public boolean isEnabled() { return enabled; }
        public void toggle() { this.enabled = !this.enabled; }

        public void onUpdate(MinecraftClient client) {
            if (client.player == null) return;

            // AFK Farmer Mantığı
            if (name.equals("AFKFarmer")) {
                if (client.player.age % 40 == 0) { // Her 2 saniyede bir ufak hareket
                    client.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.Full(
                        client.player.getX(), client.player.getY() + 0.01, client.player.getZ(),
                        client.player.getYaw(), client.player.getPitch(), true
                    ));
                }
            }
            
            // Diğer modül mantıkları (Killaura vb. buraya eklenebilir)
        }
    }
                      }
