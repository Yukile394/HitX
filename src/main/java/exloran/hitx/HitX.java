package com.exloran.hitx;

import com.exloran.hitx.mixin.MinecraftClientAccessor;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class HitX implements ClientModInitializer {

    public static final List<Module> modules = new ArrayList<>();
    private static KeyBinding guiKey;
    private static KeyBinding kbAuraKey;

    @Override
    public void onInitializeClient() {
        registerModules();
        guiKey = new KeyBinding("HitX Menü", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "HitX");
        kbAuraKey = new KeyBinding("Oto Savurma Aç/Kapat", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "HitX");

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            
            if (guiKey.wasPressed()) client.setScreen(new ClickGUI());
            
            if (kbAuraKey.wasPressed()) {
                Module kbModule = getModule("KBAura");
                if (kbModule != null) {
                    kbModule.toggle();
                    client.player.sendMessage(Text.of("§b[HitX] §fSavurma Modu: " + (kbModule.isEnabled() ? "§aAÇIK" : "§cKAPALI")), true);
                }
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
            renderButton(context, 10, 10, 100, 20, "§cENV Boşalt", mouseX, mouseY);
            renderButton(context, this.width - 110, 10, 100, 20, "§aTrade Kabul", mouseX, mouseY);

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
            if (mouseX >= 10 && mouseX <= 110 && mouseY >= 10 && mouseY <= 30) {
                dropEverything();
                return true;
            }
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
            if (client.player == null) return;
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
            if (client.player == null || client.world == null) return;

            // 1. FastPlace (Hızlı Blok Koyma)
            if (name.equals("FastPlace")) {
                ((MinecraftClientAccessor) client).setItemUseCooldown(0);
            }

            // 2. KBAura (Savurma Otosu - Yeni sürüme uygun)
            if (name.equals("KBAura")) {
                ItemStack stack = client.player.getMainHandStack();
                
                var registryManager = client.world.getRegistryManager();
                var enchantmentRegistry = registryManager.get(RegistryKeys.ENCHANTMENT);
                var knockbackEntry = enchantmentRegistry.getEntry(Enchantments.KNOCKBACK).orElse(null);

                if (knockbackEntry != null && EnchantmentHelper.getLevel(knockbackEntry, stack) > 0) {
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

            // 3. AutoTrade
            if (name.equals("AutoTrade")) {
                // client.player.networkHandler.sendChatCommand("trade accept");
            }
        }
    }
}
