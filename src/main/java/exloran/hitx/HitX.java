package com.exloran.hitx;

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
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class HitX implements ClientModInitializer {
    public static final List<Module> modules = new ArrayList<>();
    private static KeyBinding guiKey;
    private static Field cooldownField;

    @Override
    public void onInitializeClient() {
        guiKey = new KeyBinding("HitX Menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "HitX");
        
        modules.add(new Module("FastPlace", true));
        modules.add(new Module("KBAura", false));

        // Reflection hazırlığı: MinecraftClient içindeki cooldown alanını buluyoruz
        try {
            // field_3761 = itemUseCooldown (Minecraft 1.21 Intermediary adı)
            cooldownField = MinecraftClient.class.getDeclaredField("field_3761");
            cooldownField.setAccessible(true);
        } catch (Exception e) {
            System.out.println("HitX: Cooldown alani bulunamadi!");
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            if (guiKey.wasPressed()) client.setScreen(new ClickGUI());
            for (Module m : modules) if (m.enabled) m.onUpdate(client);
        });

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.currentScreen instanceof ClickGUI) return;
            int y = 10;
            for (Module m : modules) {
                if (m.enabled) {
                    drawContext.drawText(client.textRenderer, "§b" + m.name, 10, y, -1, true);
                    y += 10;
                }
            }
        });
    }

    public static class ClickGUI extends Screen {
        public ClickGUI() { super(Text.of("HitX")); }
        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            this.renderBackground(context, mouseX, mouseY, delta);
            int y = 50;
            for (Module m : modules) {
                context.fill(10, y, 110, y + 15, m.enabled ? 0x8000FF00 : 0x80FF0000);
                context.drawText(this.textRenderer, m.name, 15, y + 4, -1, true);
                y += 20;
            }
        }
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
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
            try {
                if (name.equals("FastPlace") && cooldownField != null) {
                    cooldownField.setInt(client, 0); // Mixin yerine direkt Reflection ile 0 yapıyoruz
                }
                
                if (name.equals("KBAura")) {
                    ItemStack stack = client.player.getMainHandStack();
                    var reg = client.world.getRegistryManager().getOptional(RegistryKeys.ENCHANTMENT);
                    if (reg.isPresent()) {
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
                }
            } catch (Exception ignored) {}
        }
    }
}
