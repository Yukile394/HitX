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
import net.minecraft.entity.player.PlayerEntity;
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

            if (guiKey.wasPressed()) {
                client.setScreen(new ClickGUI());
            }
            
            for (Module m : modules) {
                if (m.isEnabled()) m.onUpdate(client);
            }
        });

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> renderArrayList(drawContext));
    }

    private void registerModules() {
        modules.add(new Module("Killaura", "Vuruş hilesi", false));
        modules.add(new Module("Speed", "Hızlı koşu", false));
        modules.add(new Module("Flight", "Uçma", false));
        modules.add(new Module("FullBright", "Parlaklık", true));
        modules.add(new Module("NoFall", "Hasar engelleme", false));
        modules.add(new Module("Spider", "Tırmanma", false));
    }

    private void renderArrayList(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.currentScreen instanceof ClickGUI) return;

        if (!sorted && client.textRenderer != null) {
            modules.sort((m1, m2) -> client.textRenderer.getWidth(m2.getName()) - client.textRenderer.getWidth(m1.getName()));
            sorted = true;
        }

        int y = 5;
        for (Module m : modules) {
            if (m.isEnabled()) {
                int textWidth = client.textRenderer.getWidth(m.getName());
                int x = client.getWindow().getScaledWidth() - textWidth - 10;
                float hue = (System.currentTimeMillis() % 4000L) / 4000f;
                int color = Color.HSBtoRGB(hue, 0.7f, 1f);
                context.fill(x - 4, y - 2, client.getWindow().getScaledWidth(), y + 10, 0x90000000);
                context.drawText(client.textRenderer, m.getName(), x, y, color, true);
                y += 11;
            }
        }
    }

    public static class ClickGUI extends Screen {
        protected ClickGUI() { super(Text.of("HitX Menu")); }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            // Arka planı kendimiz çiziyoruz (Bulanıklık yok!)
            context.fill(0, 0, this.width, this.height, 0x44000000);
            
            int x = 70, y = 70;
            context.fill(x - 5, y - 10, x + 150, y + 150, 0xEE050505);
            context.drawText(this.textRenderer, "§bHitX §f- 1.21", x + 5, y, 0xFFFFFF, true);
            context.fill(x, y + 12, x + 140, y + 13, 0xFF00AAAA);

            int modY = y + 25;
            for (Module m : modules) {
                boolean hovered = mouseX >= x && mouseX <= x + 140 && mouseY >= modY && mouseY <= modY + 11;
                int color = m.isEnabled() ? 0xFF00FFCC : 0xFFFFFFFF;
                
                if (hovered) context.fill(x, modY - 1, x + 140, modY + 10, 0x22FFFFFF);
                context.drawText(this.textRenderer, (m.isEnabled() ? "§a[+] " : "§c[-] ") + m.getName(), x + 10, modY, color, false);
                modY += 13;
            }
            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int x = 70, modY = 95;
            for (Module m : modules) {
                if (mouseX >= x && mouseX <= x + 140 && mouseY >= modY && mouseY <= modY + 12) {
                    m.toggle();
                    return true;
                }
                modY += 13;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        // --- ÖNEMLİ: Hata veren kısmı düzelttim ---
        @Override
        public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
            // Burayı boş bırakıyoruz ki Minecraft'ın varsayılan bulanık ekranı gelmesin.
        }

        @Override public boolean shouldPause() { return false; }
    }

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

            // ⚔️ KILLAURA FIX
            if (name.equals("Killaura")) {
                for (Entity e : client.world.getEntities()) {
                    if (e instanceof PlayerEntity && e != client.player && client.player.distanceTo(e) < 4.0) {
                        if (client.player.getAttackCooldownProgress(0.5f) >= 1.0f) {
                            client.interactionManager.attackEntity(client.player, e);
                            client.player.networkHandler.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
                        }
                    }
                }
            }

            // 🏃 SPEED FIX (Yavaşlatmadan ivme verir)
            if (name.equals("Speed") && client.player.isOnGround() && client.player.input.movementForward > 0) {
                client.player.jump();
                Vec3d v = client.player.getVelocity();
                client.player.setVelocity(v.x * 1.3, v.y, v.z * 1.3);
            }

            if (name.equals("Flight")) {
                client.player.getAbilities().flying = true;
                client.player.getAbilities().setFlySpeed(0.1f);
            } else if (!name.equals("Flight") && !client.player.isCreative()) {
                client.player.getAbilities().flying = false;
            }

            if (name.equals("FullBright")) client.options.getGamma().setValue(100.0);
            
            if (name.equals("NoFall") && client.player.fallDistance > 2.5f) {
                client.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true));
                client.player.fallDistance = 0;
            }

            if (name.equals("Spider") && client.player.horizontalCollision) {
                client.player.setVelocity(client.player.getVelocity().x, 0.3, client.player.getVelocity().z);
            }
        }
    }
}
