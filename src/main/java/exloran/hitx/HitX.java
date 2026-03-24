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
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.item.Items;
import net.minecraft.item.ItemStack;

public class HitX implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            
            // ================= 1. SANDIK (CHEST) EKRANI FONKSİYONLARI =================
            if (screen instanceof GenericContainerScreen container) {
                int xPos = (scaledWidth / 2) + 92; 
                int yPos = (scaledHeight / 2) - 80;
                int syncId = container.getScreenHandler().syncId;

                // --- HERŞEYİ AL ---
                addButton(screen, "Herşeyi Al", xPos, yPos, 85, 20, b -> {
                    int chestSlots = container.getScreenHandler().getInventory().size();
                    for (int i = 0; i < chestSlots; i++) {
                        client.interactionManager.clickSlot(syncId, i, 0, SlotActionType.QUICK_MOVE, client.player);
                    }
                });

                // --- HERŞEYİ KOY ---
                addButton(screen, "Herşeyi Koy", xPos, yPos + 24, 85, 20, b -> {
                    int chestSlots = container.getScreenHandler().getInventory().size();
                    for (int i = chestSlots; i < chestSlots + 36; i++) {
                        client.interactionManager.clickSlot(syncId, i, 0, SlotActionType.QUICK_MOVE, client.player);
                    }
                });

                // --- MERŞEYİ AT (DÜNYAYA FIRLAT) ---
                addButton(screen, "Herşeyi At", xPos, yPos + 48, 85, 20, b -> {
                    for (int i = 0; i < container.getScreenHandler().slots.size(); i++) {
                        client.interactionManager.clickSlot(syncId, i, 1, SlotActionType.THROW, client.player);
                    }
                });

                // --- ÇÖPLERİ AT (Belirli değersiz itemlar) ---
                addButton(screen, "Çöpleri At", xPos, yPos + 72, 85, 20, b -> {
                    for (int i = 0; i < container.getScreenHandler().slots.size(); i++) {
                        ItemStack stack = container.getScreenHandler().getSlot(i).getStack();
                        if (isTrash(stack)) {
                            client.interactionManager.clickSlot(syncId, i, 1, SlotActionType.THROW, client.player);
                        }
                    }
                });
            }

            // ================= 2. ENVANTER EKRANI FONKSİYONLARI (SİMETRİK) =================
            if (screen instanceof InventoryScreen inv) {
                int x = (scaledWidth - 176) / 2;
                int y = (scaledHeight - 166) / 2;
                int syncId = inv.getScreenHandler().syncId;

                // SOL ÜST: Otomatik Zırh Giy
                addButton(screen, "🛡", x - 25, y, 20, 20, b -> {
                    for (int i = 9; i < 45; i++) {
                        ItemStack stack = inv.getScreenHandler().getSlot(i).getStack();
                        if (isArmor(stack)) client.interactionManager.clickSlot(syncId, i, 0, SlotActionType.QUICK_MOVE, client.player);
                    }
                });

                // SAĞ ÜST: Envanter Düzenle (Basit)
                addButton(screen, "⚙", x + 181, y, 20, 20, b -> {
                    client.player.sendMessage(Text.literal("§eSıralama Modu Aktif!"), true);
                });

                // SOL ALT: HERŞEYİ AT (İşaretlediğin yer - Dünyaya fırlatır)
                addButton(screen, "🗑", x - 25, y + 145, 20, 20, b -> {
                    for (int i = 9; i < 45; i++) {
                        client.interactionManager.clickSlot(syncId, i, 1, SlotActionType.THROW, client.player);
                    }
                });

                // SAĞ ALT: Hotbar Temizle
                addButton(screen, "H", x + 181, y + 145, 20, 20, b -> {
                    for (int i = 36; i < 45; i++) {
                        client.interactionManager.clickSlot(syncId, i, 1, SlotActionType.THROW, client.player);
                    }
                });
            }
        });
    }

    // Yardımcı Buton Ekleme Metodu
    private void addButton(Screen screen, String text, int x, int y, int w, int h, ButtonWidget.PressAction action) {
        Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal(text), action).dimensions(x, y, w, h).build());
    }

    // Çöp Kontrolü
    private boolean isTrash(ItemStack stack) {
        return stack.isOf(Items.ROTTEN_FLESH) || stack.isOf(Items.POISONOUS_POTATO) || stack.isOf(Items.DIRT) || stack.isOf(Items.COBBLESTONE);
    }

    // Zırh Kontrolü
    private boolean isArmor(ItemStack stack) {
        String name = stack.getItem().toString();
        return name.contains("helmet") || name.contains("chestplate") || name.contains("leggings") || name.contains("boots");
    }

    // Modül ve GUI sınıfları yapısı bozulmadan aşağıda kalabilir...
    public static class Module {
        String name; boolean enabled;
        public Module(String name, boolean enabled) { this.name = name; this.enabled = enabled; }
    }
}
