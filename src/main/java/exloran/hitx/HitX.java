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
        guiKey = new KeyBinding("HitX Menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "HitX");
        kbAuraKey = new KeyBinding("KB Aura", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "HitX");

        modules.add(new Module("FastPlace", true));
        modules.add(new Module("KBAura", false));
        modules.add(new Module("AutoTrade", false));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // KRITIK: Sunucuya giris yapilmadiysa hiçbir işlem yapma (Çökmeyi önler)
            if (client.player == null || client.world == null || client.getNetworkHandler() == null) return;

            if (guiKey.wasPressed()) client.setScreen(new ClickGUI());
            
            if (kbAuraKey.wasPressed()) {
                for(Module m : modules) if(m.name.equals("KBAura")) {
                    m.enabled = !m.enabled;
                    client.player.sendMessage(Text.of("§bHitX > §fKB Aura: " + (m.enabled ? "§aON" : "§cOFF")), true);
                }
            }
            
            for (Module m : modules) if (m.enabled) m.onUpdate(client);
        });

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.currentScreen instanceof ClickGUI) return;
            int y = 10;
            for (Module m : modules) {
                if (m.enabled) {
                    drawContext.drawText(client.textRenderer, "§b" + m.name, client.getWindow().getScaledWidth() - client.textRenderer.getWidth(m.name) - 5, y, -1, true);
                    y += 10;
                }
            }
        });
    }

    public static class ClickGUI extends Screen {
        public ClickGUI() { super(Text.of("HitX")); }
        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(10, 10, 110, 30, 0x80000000);
            context.drawText(this.textRenderer, "ENV BOSALT", 25, 17, -1, true);
            int y = 50;
            for (Module m : modules) {
                context.fill(10, y, 110, y + 15, m.enabled ? 0x8000FF00 : 0x80FF0000);
                context.drawText(this.textRenderer, m.name, 15, y + 4, -1, true);
                y += 20;
            }
        }
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return false;

            if (mouseX >= 10 && mouseX <= 110 && mouseY >= 10 && mouseY <= 30) {
                for (int i = 0; i < 45; i++) client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, i, 1, SlotActionType.THROW, client.player);
            }
            int y = 50;
            for (Module m : modules) {
                if (mouseX >= 10 && mouseX <= 110 && mouseY >= y && mouseY <= y + 15) {
                    m.enabled = !m.enabled;
                    return true;
                }
                y += 20;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
    }

    public static class Module {
        public String name; public boolean enabled;
        public Module(String n, boolean e) { name = n; enabled = e; }

        public void onUpdate(MinecraftClient client) {
            // EN ÖNEMLİ KISIM: Sunucu verileri gelmeden büyü sorgulama
            try {
                if (name.equals("FastPlace")) {
                    ((MinecraftClientAccessor)client).setItemUseCooldown(0);
                }
                
                if (name.equals("KBAura")) {
                    ItemStack stack = client.player.getMainHandStack();
                    if (stack.isEmpty()) return;

                    // Büyü listesini güvenli al
                    var reg = client.world.getRegistryManager().getOrEmpty(RegistryKeys.ENCHANTMENT);
                    if (reg.isEmpty()) return;

                    var kb = reg.get().getEntry(Enchantments.KNOCKBACK).orElse(null);
                    if (kb != null && EnchantmentHelper.getLevel(kb, stack) > 0) {
                        for (Entity e : client.world.getEntities()) {
                            if (e instanceof LivingEntity && e != client.player && client.player.distanceTo(e) < 4.5) {
                                if (client.player.getAttackCooldownProgress(0) >= 0.9) {
                                    client.interactionManager.attackEntity(client.player, e);
                                    client.player.swingHand(Hand.MAIN_HAND);
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
    }
}
