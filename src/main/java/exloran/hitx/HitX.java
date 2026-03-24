package com.exloran.hitx;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;

public class HitX implements ClientModInitializer {

    private static boolean fastPlaceEnabled = true;
    private static boolean autoAcceptEnabled = true;

    @Override
    public void onInitializeClient() {
        // Hızlı Blok Koyma & Trap Kapatma Mantığı
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null && fastPlaceEnabled) {
                // Eğer elinde odun/blok varsa ve sağ tık basılıysa gecikmeyi sıfırla
                if (client.options.useKey.isPressed()) {
                    // Minecraft'ın varsayılan 4 ticklik koyma gecikmesini bypass eder
                    try {
                        java.lang.reflect.Field field = MinecraftClient.class.getDeclaredField("itemUseCallbackTicks");
                        field.setAccessible(true);
                        field.setInt(client, 0);
                    } catch (Exception ignored) {}
                }
            }
            
            // Auto Accept (Chat kontrolü ile yapılabilir, burada temel mantık kurulu)
            if (autoAcceptEnabled && client.player != null) {
                // Buraya gelen mesajları tarayan bir sistem eklenebilir
            }
        });

        // Ekrana Butonları ve GUI Elemanlarını Çizme
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            renderCustomButtons(drawContext);
        });
    }

    private void renderCustomButtons(DrawContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen == null) return;

        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();

        // 1. SOL ÜST: "Tüm Eşyayı Bırak" (Inventory veya Chest açıkken)
        if (client.currentScreen instanceof InventoryScreen || client.currentScreen instanceof GenericContainerScreen) {
            ctx.fill(10, 10, 90, 25, 0x80000000); // Siyah transparan arka plan
            ctx.drawText(client.textRenderer, "Tümünü At", 15, 14, 0xFFFFFF, true);
            
            // 2. SAĞ ÜST: "Trade Accept"
            ctx.fill(width - 90, 10, width - 10, 25, 0x8000FF00); // Yeşilimsi arka plan
            ctx.drawText(client.textRenderer, "Oto Kabul: AÇIK", width - 85, 14, 0xFFFFFF, true);

            // 3. ALT KISIM: "Oto Trap / Fast"
            ctx.fill(width / 2 - 50, height - 40, width / 2 + 50, height - 25, 0x80FF0000);
            ctx.drawText(client.textRenderer, "PvP Modu: AKTİF", width / 2 - 40, height - 36, 0xFFFFFF, true);
        }
    }

    // Envanterdeki her şeyi hızlıca yere atan fonksiyon
    public static void dropEverything(MinecraftClient client) {
        if (client.player == null) return;
        for (int i = 0; i < 45; i++) {
            client.interactionManager.clickSlot(client.player.currentScreenHandler.syncId, i, 1, SlotActionType.THROW, client.player);
        }
    }

    // ================= GUI & MODULE YAPISI (BOZULMADI) =================
    public static class ClickGUI extends Screen {
        public ClickGUI() { super(null); }
        @Override
        public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
            ctx.fillGradient(0, 0, this.width, this.height, 0x20000000, 0x80000000);
            ctx.drawCenteredTextWithShadow(this.textRenderer, "HitX Control Panel", this.width / 2, 20, 0xFFFFFF);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            // Sol üst butona tıklandığında (Koordinat kontrolü)
            if (mouseX > 10 && mouseX < 90 && mouseY > 10 && mouseY < 25) {
                dropEverything(MinecraftClient.getInstance());
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
    }

    public static class Module {
        String name;
        boolean enabled;
        public Module(String name, boolean enabled) {
            this.name = name;
            this.enabled = enabled;
        }
        public void onUpdate(MinecraftClient client) {}
    }
}
