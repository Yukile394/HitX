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
            if (guiKey.wasPressed()) client.setScreen(new ClickGUI());
            
            for (Module m : modules) {
                if (m.isEnabled()) m.onUpdate(client);
            }
        });

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> renderArrayList(drawContext));
    }

    private void registerModules() {
        // --- COMBAT ---
        modules.add(new Module("Killaura", "Otomatik vurur", false));
        modules.add(new Module("Hitboxes", "Geniş vuruş alanı", false));
        modules.add(new Module("AutoCrit", "Hep kritik vurur", false));
        modules.add(new Module("TriggerBot", "Bakınca vurur", false));
        modules.add(new Module("Reach", "Uzaktan vurma", false));

        // --- MOVEMENT ---
        modules.add(new Module("Speed", "Bhop hızı", false));
        modules.add(new Module("Flight", "Sorunsuz uçuş", false));
        modules.add(new Module("Spider", "Duvar tırmanma", false));
        modules.add(new Module("NoFall", "Hasar almaz", false));
        modules.add(new Module("AirJump", "Havada zıpla", false));
        modules.add(new Module("Jesus", "Suda yürü", false));
        modules.add(new Module("Step", "Blokları direk çıkar", false));

        // --- VISUAL ---
        modules.add(new Module("FullBright", "Gece görüşü", true));
        modules.add(new Module("ESP", "Wallhack", false));
        modules.add(new Module("Tracers", "Çizgiler", false));
        modules.add(new Module("NoVisuals", "Efektleri siler", false));

        // --- MISC ---
        modules.add(new Module("AutoWalk", "Otomatik yürür", false));
        modules.add(new Module("AutoEat", "Otomatik yemek", false));
        modules.add(new Module("FastPlace", "Hızlı blok koy", false));
        modules.add(new Module("ChestStealer", "Sandık boşalt", false));
        
        // Buraya 50'ye kadar benzer şekilde ekleme yapabilirsin...
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
        private int scroll = 0;
        protected ClickGUI() { super(Text.of("HitX Menu")); }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(0, 0, this.width, this.height, 0x55000000); // Bulanıklık yerine şeffaf siyah
            
            int x = 50, y = 50;
            context.fill(x - 5, y - 10, x + 160, y + 250, 0xEE111111);
            context.drawText(this.textRenderer, "§b§lHitX §f| §7V5.0", x + 5, y, 0xFFFFFF, true);
            context.fill(x, y + 12, x + 150, y + 13, 0xFF00FFFF);

            int modY = y + 25;
            for (int i = 0; i < modules.size(); i++) {
                Module m = modules.get(i);
                int currentY = modY + (i * 14) - scroll;
                
                // Menü sınırları içinde kalması için kontrol
                if (currentY > y + 15 && currentY < y + 240) {
                    boolean hovered = mouseX >= x && mouseX <= x + 150 && mouseY >= currentY && mouseY <= currentY + 12;
                    int color = m.isEnabled() ? 0xFF00FFCC : 0xFFBBBBBB;
                    
                    if (hovered) context.fill(x, currentY - 1, x + 150, currentY + 11, 0x22FFFFFF);
                    context.drawText(this.textRenderer, (m.isEnabled() ? "§a✔ " : "§c✖ ") + m.getName(), x + 10, currentY, color, false);
                }
            }
            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            int x = 50, modY = 75;
            for (int i = 0; i < modules.size(); i++) {
                int currentY = modY + (i * 14) - scroll;
                if (mouseX >= x && mouseX <= x + 150 && mouseY >= currentY && mouseY <= currentY + 12) {
                    modules.get(i).toggle();
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            scroll -= (int) (verticalAmount * 10);
            if (scroll < 0) scroll = 0;
            return true;
        }

        @Override
        public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) { }
        @Override public boolean shouldPause() { return false; }
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
            if (client.player == null || client.world == null) return;

            // --- ⚔️ COMBAT LOGIC ---
            if (name.equals("Killaura") || name.equals("Hitboxes")) {
                double range = name.equals("Hitboxes") ? 6.0 : 4.2;
                for (Entity e : client.world.getEntities()) {
                    if (e instanceof LivingEntity && e != client.player && client.player.distanceTo(e) < range) {
                        if (client.player.getAttackCooldownProgress(0.5f) >= 1.0f) {
                            client.interactionManager.attackEntity(client.player, e);
                            client.player.networkHandler.sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
                        }
                    }
                }
            }

            // --- 🏃 MOVEMENT LOGIC ---
            if (name.equals("Speed") && client.player.isOnGround() && client.player.input.movementForward > 0) {
                client.player.jump();
                client.player.updateVelocity(0.2f, new Vec3d(0, 0, 1));
            }

            if (name.equals("Flight")) {
                client.player.getAbilities().flying = true;
            } else if (!name.equals("Flight") && !client.player.isCreative()) {
                client.player.getAbilities().flying = false;
            }

            if (name.equals("NoFall") && client.player.fallDistance > 2.0f) {
                client.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true));
                client.player.fallDistance = 0;
            }

            // --- 👁️ VISUAL LOGIC ---
            if (name.equals("FullBright")) {
                client.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1000, 1, false, false));
            }

            if (name.equals("Spider") && client.player.horizontalCollision) {
                client.player.setVelocity(client.player.getVelocity().x, 0.25, client.player.getVelocity().z);
            }
            
            if (name.equals("AutoWalk")) client.options.forwardKey.setPressed(true);
        }
    }
}
