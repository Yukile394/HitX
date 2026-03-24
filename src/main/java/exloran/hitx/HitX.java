package com.exloran.hitx;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class HitX implements ClientModInitializer {

    public static final List<Module> modules = new ArrayList<>();
    private static KeyBinding guiKey;

    @Override
    public void onInitializeClient() {
        // Ekranlar açıldığında butonları enjekte etmek için event kaydı
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            
            // --- 1. SANDIK (CHEST) EKRANI: SAĞ TARAFTAKİ LİSTE ---
            if (screen instanceof GenericContainerScreen) {
                // Sandık GUI'sinin orta noktasından sağa doğru hizalama
                int xPos = (scaledWidth / 2) + 92; 
                int yPos = (scaledHeight / 2) - 80;

                String[] buttonLabels = {
                    "Merşeyi At", "Oto Ekipman", "Herşeyi Koy", "Herşeyi Al", "Çöpleri At"
                };

                for (int i = 0; i < buttonLabels.length; i++) {
                    final String label = buttonLabels[i];
                    Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal(label), button -> {
                        // Buraya tıklama işlevi (logic) gelecek
                        if (client.player != null) {
                            client.player.sendMessage(Text.literal("§a" + label + " tıklandı!"), true);
                        }
                    }).dimensions(xPos, yPos + (i * 24), 85, 20).build());
                }
            }

            // --- 2. ENVANTER EKRANI: KÖŞELERDEKİ SİMETRİK BUTONLAR ---
            if (screen instanceof InventoryScreen) {
                int invWidth = 176;
                int invHeight = 166;
                int x = (scaledWidth - invWidth) / 2;
                int y = (scaledHeight - invHeight) / 2;

                // Sol Üst (İşaretli yer 1)
                Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("⚙"), b -> {})
                        .dimensions(x - 22, y + 5, 20, 20).build());

                // Sağ Üst (İşaretli yer 2)
                Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("X"), b -> {})
                        .dimensions(x + invWidth + 2, y + 5, 20, 20).build());

                // Sol Alt (İşaretli yer 3)
                Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("S"), b -> {})
                        .dimensions(x - 22, y + 141, 20, 20).build());

                // Sağ Alt (İşaretli yer 4)
                Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("C"), b -> {})
                        .dimensions(x + invWidth + 2, y + 141, 20, 20).build());
            }
        });
    }

    // ================= GUI =================
    public static class ClickGUI extends Screen {
        protected ClickGUI() {
            super(Text.literal("HitX GUI"));
        }

        @Override
        public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
            // Boş render yapısı korundu
            super.render(ctx, mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return super.mouseClicked(mouseX, mouseY, button);
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
            // Güncelleme mantığı buraya gelir
        }
    }
}
