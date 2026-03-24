package com.exloran.hitx;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.block.BlockItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class HitX implements ClientModInitializer {
    public static final List<Module> modules = new ArrayList<>();
    private static KeyBinding guiKey;
    private static Field cooldownField;
    public static boolean autoAccept = false;

    @Override
    public void onInitializeClient() {
        guiKey = new KeyBinding("HitX Menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "HitX");
        
        // Modülleri ekle
        modules.add(new Module("FastPlace", true));
        modules.add(new Module("KBAura", false));
        modules.add(new Module("Sprint", true));
        modules.add(new Module("AutoClicker", false));

        // Reflection: Cooldown (Hızlı Koyma) için
        try {
            cooldownField = MinecraftClient.class.getDeclaredField("field_3761");
            cooldownField.setAccessible(true);
        } catch (Exception ignored) {}

        // Oto Trade/TPA Kabul Etme Mesaj Dinleyici
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (autoAccept && message.getString().toLowerCase().contains("istek")) {
                MinecraftClient.getInstance().player.networkHandler.sendChatCommand("tpaccept");
                MinecraftClient.getInstance().player.networkHandler.sendChatCommand("trade accept");
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            if (guiKey.wasPressed()) client.setScreen(new ClickGUI());
            for (Module m : modules) if (m.enabled) m.onUpdate(client);
        });

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.currentScreen instanceof ClickGUI) return;
            int y = 10;
            drawContext.drawText(client.textRenderer, "§bHitX §7v1.0", 10, 10, -1, true);
            for (Module m : modules) {
                if (m.enabled) {
                    drawContext.drawText(client.textRenderer, "§a" + m.name, 10, y + 12, -1, true);
                    y += 10;
                }
            }
        });
    }

    public static class ClickGUI extends Screen {
        public ClickGUI() { super(Text.of("HitX")); }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            int w = this.width;
            int h = this.height;

            // Arkaplan
            context.fill(0, 0, w, h, 0x90000000);

            // 1. SOL ÜST: ENV BOŞALT
            drawButton(context, 10, 10, 110, 40, "§cENV BOSALT", mouseX, mouseY);

            // 2. SAĞ ÜST: AUTO ACCEPT
            drawButton(context, w - 120, 10, w - 10, 40, autoAccept ? "§aAUTO ACCEPT: ON" : "§7AUTO ACCEPT: OFF", mouseX, mouseY);

            // 3. ORTA: MODÜLLER
            int y = 60;
            for (Module m : modules) {
                drawButton(context, w/2 - 50, y, w/2 + 50, y + 20, (m.enabled ? "§a" : "§c") + m.name, mouseX, mouseY);
                y += 25;
            }

            // 4. ALT KISIM: EKSTRA BİLGİ
            context.drawCenteredTextWithShadow(this.textRenderer, "§bHitX - Pojav Edition", w / 2, h - 20, -1);
        }

        private void drawButton(DrawContext context, int x1, int y1, int x2, int y2, String text, int mx, int my) {
            boolean hover = mx >= x1 && mx <= x2 && my >= y1 && my <= y2;
            context.fill(x1, y1, x2, y2, hover ? 0x90444444 : 0x90222222);
            context.drawBorder(x1, y1, x2 - x1, y2 - y1, -1);
            context.drawCenteredTextWithShadow(this.textRenderer, text, x1 + (x2 - x1) / 2, y1 + (y2 - y1) / 2 - 4, -1);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            MinecraftClient client = MinecraftClient.getInstance();
            int w = this.width;

            // Env Boşalt Tıkla
            if (mouseX >= 10 && mouseX <= 110 && mouseY >= 10 && mouseY <= 40) {
                for (int i = 9; i < 45; i++) client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, i, 1, SlotActionType.THROW, client.player);
                return true;
            }

            // Auto Accept Tıkla
            if (mouseX >= w - 120 && mouseX <= w - 10 && mouseY >= 10 && mouseY <= 40) {
                autoAccept = !autoAccept;
                return true;
            }

            // Modüller Tıkla
            int y = 60;
            for (Module m : modules) {
                if (mouseX >= w/2 - 50 && mouseX <= w/2 + 50 && mouseY >= y && mouseY <= y + 20) {
                    m.enabled = !m.enabled;
                    client.player.sendMessage(Text.of("§bHitX > §f" + m.name + ": " + (m.enabled ? "§aAÇIK" : "§cKAPALI")), true);
                    return true;
                }
                y += 25;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
    }

    public static class Module {
        public String name; public boolean enabled;
        public Module(String n, boolean e) { name = n; enabled = e; }

        public void onUpdate(MinecraftClient client) {
            try {
                // 1. FAST PLACE (Sadece Odun/Blok Tutarken)
                if (name.equals("FastPlace") && cooldownField != null) {
                    if (client.player.getMainHandStack().getItem() instanceof BlockItem) {
                        cooldownField.setInt(client, 0);
                    }
                }

                // 2. KBAURA (Savurma Silahını Bulur ve Vurur)
                if (name.equals("KBAura")) {
                    // Hotbar'da savurma olan silahı ara
                    for (int i = 0; i < 9; i++) {
                        ItemStack s = client.player.getInventory().getStack(i);
                        var reg = client.world.getRegistryManager().get(RegistryKeys.ENCHANTMENT);
                        var kbEntry = reg.getEntry(Enchantments.KNOCKBACK).orElse(null);
                        
                        if (kbEntry != null && EnchantmentHelper.getLevel(kbEntry, s) > 0) {
                            client.player.getInventory().selectedSlot = i; // Silahı ele al
                            break;
                        }
                    }

                    for (Entity e : client.world.getEntities()) {
                        if (e instanceof LivingEntity && e != client.player && client.player.distanceTo(e) < 4.0) {
                            if (client.player.getAttackCooldownProgress(0) >= 0.9) {
                                client.interactionManager.attackEntity(client.player, e);
                                client.player.swingHand(Hand.MAIN_HAND);
                            }
                        }
                    }
                }

                // 3. SPRINT (Her Zaman Koş)
                if (name.equals("Sprint")) client.player.setSprinting(true);

                // 4. AUTOCLICKER
                if (name.equals("AutoClicker") && client.options.attackKey.isPressed()) {
                    if (client.player.getAttackCooldownProgress(0) >= 0.9) {
                        client.interactionManager.attackEntity(client.player, client.targetedEntity);
                    }
                }

            } catch (Exception ignored) {}
        }
    }
}
