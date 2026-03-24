package com.exloran.hitx;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.screen.slot.SlotActionType;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class HitX implements ClientModInitializer {

    public static final List<Module> modules = new ArrayList<>();
    private static KeyBinding guiKey;
    private static boolean fastPlaceEnabled = true;

    @Override
    public void onInitializeClient() {
        // GUI Açma Tuşu (G varsayılan)
        guiKey = new KeyBinding("key.hitx.gui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "category.hitx");

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (guiKey.wasPressed()) {
                client.setScreen(new ClickGUI());
            }

            // Hızlı Blok Koyma (FastPlace) Mantığı
            if (client.player != null && fastPlaceEnabled) {
                if (client.options.useKey.isPressed()) {
                    try {
                        // Minecraft'ın blok koyma gecikmesini bypass eder (Trap kapatma için)
                        java.lang.reflect.Field field = MinecraftClient.class.getDeclaredField("itemUseCallbackTicks");
                        field.setAccessible(true);
                        field.setInt(client, 0);
                    } catch (Exception ignored) {}
                }
            }
        });

        // Ekrana Bilgileri Yazdırma (PVP/Envanter Ayrımı)
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            renderModInfo(drawContext);
        });
    }

    private void renderModInfo(DrawContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen == null) return;

        // Sadece Envanter açıkken göster
        if (client.currentScreen instanceof InventoryScreen) {
            int x = 10; // Sol kenardan boşluk
            int y = 10; // Üst kenardan boşluk

            // Bilgi Kutusu Tasarımı (Fotoğraftaki gibi sol üst)
            ctx.fill(x, y, x + 100, y + 45, 0x90000000); // Yarı saydam arka plan
            ctx.drawText(client.textRenderer, "§bHitX§r | §aAKTİF§r", x + 5, y + 5, 0xFFFFFF, true);
            ctx.drawText(client.textRenderer, "--------------", x + 5, y + 15, 0x808080, true);
            
            // İstediğin PVP Modu Göstergesi
            ctx.drawText(client.textRenderer, "MOD: §ePvP 1§r", x + 5, y + 25, 0xFFFFFF, true);
            ctx.drawText(client.textRenderer, "[Envanter Gecerli]", x + 5, y + 35, 0xA0A0A0, true);
        }
    }

    // ================= GUI =================
    public static class ClickGUI extends Screen {
        protected ClickGUI() {
            super(null);
        }

        @Override
        public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
            ctx.fillGradient(0, 0, this.width, this.height, 0x20000000, 0x80000000);
            ctx.drawCenteredTextWithShadow(this.textRenderer, "HitX Mod Menüsü", this.width / 2, 20, 0xFFFFFF);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            // "Tümünü At" mantığı burada çalışır (Koordinat kontrolü eklenebilir)
            if (mouseX > 10 && mouseX < 110 && mouseY > 10 && mouseY < 55) {
                dropEverything(MinecraftClient.getInstance());
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
    }

    // Tüm envanteri yere atma fonksiyonu
    private static void dropEverything(MinecraftClient client) {
        if (client.player == null) return;
        for (int i = 0; i < 45; i++) {
            client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, i, 1, SlotActionType.THROW, client.player);
        }
    }

    // ================= MODULE =================
    public static class Module {
        String name;
        boolean enabled;

        public Module(String name, boolean enabled) {
            this.name = name;
            this.enabled = enabled;
        }

        public void onUpdate(MinecraftClient client) {
            // Mod güncelleme mantığı
        }
    }
}
