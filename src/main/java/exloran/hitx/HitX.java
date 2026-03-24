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
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class HitX implements ClientModInitializer {

    public static final List<Module> modules = new ArrayList<>();
    private static KeyBinding guiKey;
    private static KeyBinding kbAuraKey; // Savurma Otosu için tuş

    @Override
    public void onInitializeClient() {
        registerModules();
        guiKey = new KeyBinding("HitX Menü", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "HitX");
        kbAuraKey = new KeyBinding("Oto Savurma Aç/Kapat", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "HitX");

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            
            if (guiKey.wasPressed()) client.setScreen(new ClickGUI());
            
            // Tuş ile modül kontrolü
            if (kbAuraKey.wasPressed()) {
                getModule("KBAura").toggle();
                client.player.sendMessage(Text.of("§b[HitX] §fSavurma Modu: " + (getModule("KBAura").isEnabled() ? "§aAÇIK" : "§cKAPALI")), true);
            }

            for (Module m : modules) {
                if (m.isEnabled()) m.onUpdate(client);
            }
        });

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> renderArrayList(drawContext));
    }

    private void registerModules() {
        modules.add(new Module("FastPlace", "Odunları hızlı koyar, lag yapmaz", true));
        modules.add(new Module("KBAura", "Savurma kılıcıyla oto vurur", false));
        modules.add(new Module("AutoTrade", "Gelen takasları otomatik kabul eder", false));
    }

    public static Module getModule(String name) {
        return modules.stream().filter(m -> m.getName().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    private void renderArrayList(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.currentScreen instanceof ClickGUI) return;
        int y = 5;
        for (Module m : modules) {
            if (m.isEnabled()) {
                int x = client.getWindow().getScaledWidth() - client.textRenderer.getWidth(m.getName()) - 10;
                context.drawText(client.textRenderer, "§b" + m.getName(), x, y, 0xFFFFFF, true);
                y += 11;
            }
        }
    }

    public static class ClickGUI extends Screen {
        protected ClickGUI() { super(Text.of("HitX")); }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            // Fotoğraftaki gibi üst köşelere butonlar
            renderButton(context, 10, 10, 100, 20, "§cENV Boşalt", mouseX, mouseY); // Sol Üst
            renderButton(context, this.width - 110, 10, 100, 20, "§aTrade Kabul", mouseX, mouseY); // Sağ Üst

            // Orta Menü
            int x = this.width / 2 - 75;
            int y = this.height / 2 - 50;
            context.fill(x, y, x + 150, y + 100, 0xAA000000);
            context.drawCenteredTextWithShadow(this.textRenderer, "--- HitX Mod Menü ---", this.width / 2, y + 10, 0x00FFFF);

            for (int i = 0; i < modules.size(); i++) {
                Module m = modules.get(i);
                context.drawText(this.textRenderer, (m.isEnabled() ? "§a[ON] " : "§c[OFF] ") + m.getName(), x + 10, y + 35 + (i * 15), 0xFFFFFF, false);
            }
            super.render(context, mouseX, mouseY, delta);
        }

        private void renderButton(DrawContext context, int x, int y, int w, int h, String text, int mx, int my) {
            boolean hover = mx >= x && mx <= x + w && my >= y && my <= y + h;
            context.fill(x, y, x + w, y + h, hover ? 0xEE444444 : 0xEE222222);
            context.drawCenteredTextWithShadow(this.textRenderer, text, x + w / 2, y + h / 4, 0xFFFFFF);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            // Env Boşalt (Sol Üst)
            if (mouseX >= 10 && mouseX <= 110 && mouseY >= 10 && mouseY <= 30) {
                dropEverything();
                return true;
            }
            // Modül Tıklama
            int x = this.width / 2 - 75;
            int y = this.height / 2 - 15;
            for (int i = 0; i < modules.size(); i++) {
                if (mouseY >= y + (i * 15) && mouseY <= y + (i * 15) + 12) {
                    modules.get(i).toggle();
                    return true;
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        private void dropEverything() {
            MinecraftClient client = MinecraftClient.getInstance();
            for (int i = 0; i < 45; i++) {
                client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, i, 1, SlotActionType.THROW, client.player);
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
            // 1. FastPlace (Hızlı Blok Koyma & Trap Kapatma)
            if (name.equals("FastPlace")) {
                client.itemUseCooldown = 0; 
            }

            // 2. KBAura (Savurma Otosu)
            if (name.equals("KBAura")) {
                ItemStack stack = client.player.getMainHandStack();
                // Elindeki eşyada Savurma (Knockback) varsa çalışır
                if (EnchantmentHelper.getLevel(Enchantments.KNOCKBACK, stack) > 0) {
                    for (Entity target : client.world.getEntities()) {
                        if (target instanceof LivingEntity && target != client.player && client.player.distanceTo(target) < 4.5) {
                            if (client.player.getAttackCooldownProgress(0.5f) >= 1) {
                                client.interactionManager.attackEntity(client.player, target);
                                client.player.swingHand(Hand.MAIN_HAND);
                            }
                        }
                    }
                }
            }

            // 3. AutoTrade (Gelen istekleri oto kabul)
            if (name.equals("AutoTrade")) {
                // Bu kısım sunucu komutuna göre değişir, genelde /trade accept yazdırılır
                // client.player.networkHandler.sendChatCommand("trade accept");
            }
        }
    }
}
