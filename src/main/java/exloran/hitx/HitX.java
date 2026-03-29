package com.exloran.hitx;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.awt.Color;

public class HitX implements ClientModInitializer {

    @Override
    public void onInitializeClient() {

        // ================= 1. GUI / EKRAN BUTONLARI (ESKİ YAPI KORUNDU) =================
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            
            // --- SANDIK (CHEST) EKRANI ---
            if (screen instanceof GenericContainerScreen container) {
                int xPos = (scaledWidth / 2) + 92; 
                int yPos = (scaledHeight / 2) - 80;
                int syncId = container.getScreenHandler().syncId;

                addButton(screen, "Herşeyi Al", xPos, yPos, 85, 20, b -> {
                    int chestSlots = container.getScreenHandler().getInventory().size();
                    for (int i = 0; i < chestSlots; i++) {
                        client.interactionManager.clickSlot(syncId, i, 0, SlotActionType.QUICK_MOVE, client.player);
                    }
                });

                addButton(screen, "Herşeyi Koy", xPos, yPos + 24, 85, 20, b -> {
                    int chestSlots = container.getScreenHandler().getInventory().size();
                    for (int i = chestSlots; i < chestSlots + 36; i++) {
                        client.interactionManager.clickSlot(syncId, i, 0, SlotActionType.QUICK_MOVE, client.player);
                    }
                });

                addButton(screen, "Herşeyi At", xPos, yPos + 48, 85, 20, b -> {
                    for (int i = 0; i < container.getScreenHandler().slots.size(); i++) {
                        client.interactionManager.clickSlot(syncId, i, 1, SlotActionType.THROW, client.player);
                    }
                });

                addButton(screen, "Çöpleri At", xPos, yPos + 72, 85, 20, b -> {
                    for (int i = 0; i < container.getScreenHandler().slots.size(); i++) {
                        ItemStack stack = container.getScreenHandler().getSlot(i).getStack();
                        if (isTrash(stack)) {
                            client.interactionManager.clickSlot(syncId, i, 1, SlotActionType.THROW, client.player);
                        }
                    }
                });
            }

            // --- ENVANTER EKRANI ---
            if (screen instanceof InventoryScreen inv) {
                int x = (scaledWidth - 176) / 2;
                int y = (scaledHeight - 166) / 2;
                int syncId = inv.getScreenHandler().syncId;

                addButton(screen, "🛡", x - 25, y, 20, 20, b -> {
                    for (int i = 9; i < 45; i++) {
                        ItemStack stack = inv.getScreenHandler().getSlot(i).getStack();
                        if (isArmor(stack)) client.interactionManager.clickSlot(syncId, i, 0, SlotActionType.QUICK_MOVE, client.player);
                    }
                });

                addButton(screen, "⚙", x + 181, y, 20, 20, b -> {
                    if (client.player != null) {
                        client.player.sendMessage(Text.literal("§eSıralama Modu Aktif!"), true);
                    }
                });

                addButton(screen, "🗑", x - 25, y + 145, 20, 20, b -> {
                    for (int i = 9; i < 45; i++) {
                        client.interactionManager.clickSlot(syncId, i, 1, SlotActionType.THROW, client.player);
                    }
                });

                addButton(screen, "H", x + 181, y + 145, 20, 20, b -> {
                    for (int i = 36; i < 45; i++) {
                        client.interactionManager.clickSlot(syncId, i, 1, SlotActionType.THROW, client.player);
                    }
                });
            }
        });

        // ================= 2. PVP ÖZELLİKLERİ (TICK EVENTS) =================
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null) {
                // Auto-Sprint (Otomatik Koşma)
                if (client.options.forwardKey.isPressed() && !client.player.horizontalCollision && !client.player.isSneaking() && client.player.getHungerManager().getFoodLevel() > 6) {
                    client.player.setSprinting(true);
                }
                
                // Fullbright (Sınırsız Gece Görüşü - PvP için)
                if (!client.player.hasStatusEffect(StatusEffects.NIGHT_VISION)) {
                    // Parçacık (particle) efekti olmadan, ekranı temiz bir şekilde aydınlatır
                    client.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 400, 0, false, false, false));
                }
            }
        });

        // ================= 3. HUD (EKRAN ÇİZİM) ÖZELLİKLERİ =================
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;

            int screenWidth = client.getWindow().getScaledWidth();
            int screenHeight = client.getWindow().getScaledHeight();

            // --- FPS GÖSTERGESİ (Sol Üst) ---
            String fpsText = "FPS: " + client.getCurrentFps();
            drawContext.drawText(client.textRenderer, fpsText, 5, 5, 0x00FF00, true);

            // --- DÜŞÜK CAN UYARISI ---
            if (client.player.getHealth() <= 6.0f) { 
                String warningText = "⚠ DÜŞÜK CAN ⚠";
                int textWidth = client.textRenderer.getWidth(warningText);
                drawContext.drawText(client.textRenderer, warningText, (screenWidth / 2) - (textWidth / 2), (screenHeight / 2) - 30, 0xFF0000, true);
            }

            // --- ZIRH DURUMU (ARMOR HUD - Sağ Alt) ---
            int armorX = screenWidth - 25;
            int armorY = screenHeight - 60;
            Iterable<ItemStack> armorItems = client.player.getArmorItems();
            int yOffset = 0;
            
            for (ItemStack armor : armorItems) {
                if (!armor.isEmpty()) {
                    // Zırh ikonunu çiz
                    drawContext.drawItem(armor, armorX, armorY - yOffset);
                    drawContext.drawItemInSlot(client.textRenderer, armor, armorX, armorY - yOffset);
                    
                    // Zırh Dayanıklılığı (Durability)
                    if (armor.isDamageable()) {
                        int maxDamage = armor.getMaxDamage();
                        int damage = armor.getDamage();
                        int remaining = maxDamage - damage;
                        String durText = String.valueOf(remaining);
                        int textWidth = client.textRenderer.getWidth(durText);
                        
                        // Kırılmaya yaklaştıkça renk değiştir (Yeşil -> Sarı -> Kırmızı)
                        int color = 0x00FF00;
                        if (remaining <= maxDamage * 0.5f) color = 0xFFFF00;
                        if (remaining <= maxDamage * 0.2f) color = 0xFF0000;
                        
                        drawContext.drawText(client.textRenderer, durText, armorX - textWidth - 5, armorY - yOffset + 4, color, true);
                    }
                }
                yOffset += 18; // Bir üstteki zırha geç
            }

            // --- TARGET HUD (HEDEF EKRANI - Sol ve Biraz Aşağı) ---
            if (client.targetedEntity instanceof PlayerEntity target) {
                
                // Matris ayarları: Büyütme (Scale) ve Pozisyonlandırma (Translate)
                drawContext.getMatrices().push();
                
                // Konum: Ekranın solunda, ortadan biraz aşağıda
                int targetX = (screenWidth / 2) - 150; 
                int targetY = (screenHeight / 2) + 20;
                
                drawContext.getMatrices().translate(targetX, targetY, 0);
                
                // Boyut: Orta Boyut (1.2x)
                drawContext.getMatrices().scale(1.2f, 1.2f, 1.0f);

                int width = 110;
                int height = 40;

                // Animasyonlu RGB Renk Hesaplama
                float hue = (System.currentTimeMillis() % 3000) / 3000f;
                int rgbColor = Color.HSBtoRGB(hue, 0.8f, 1.0f);
                int borderColor = rgbColor | 0xFF000000; 

                // Arka plan (Yarı saydam siyah)
                drawContext.fill(0, 0, width, height, 0x90000000);

                // İnce Kare Çerçeve (Animasyonlu)
                drawContext.fill(0, 0, width, 1, borderColor); // Üst
                drawContext.fill(0, height - 1, width, height, borderColor); // Alt
                drawContext.fill(0, 0, 1, height, borderColor); // Sol
                drawContext.fill(width - 1, 0, width, height, borderColor); // Sağ

                // Oyuncu İsmi
                String targetName = target.getName().getString();
                drawContext.drawText(client.textRenderer, targetName, 5, 5, 0xFFFFFF, true);

                // Arapça 2 Harfli Can Yazısı (دم -> Kan/Can demek)
                float health = target.getHealth();
                float maxHealth = target.getMaxHealth();
                String healthText = String.format("%.1f / %.1f دم", health, maxHealth);
                
                int hpColor = 0x00FF00;
                if (health <= maxHealth * 0.5f) hpColor = 0xFFFF00;
                if (health <= maxHealth * 0.25f) hpColor = 0xFF0000;
                
                drawContext.drawText(client.textRenderer, healthText, 5, 16, hpColor, true);

                // Can Barı (Altta)
                int barWidth = 100;
                int currentBarWidth = (int) ((health / maxHealth) * barWidth);
                
                drawContext.fill(5, 28, 5 + barWidth, 33, 0xFF444444);
                drawContext.fill(5, 28, 5 + currentBarWidth, 33, borderColor);

                // Matrisi eski haline getir (diğer çizimleri bozmamak için)
                drawContext.getMatrices().pop();
            }
        });
    }

    // ================= YARDIMCI METOTLAR =================

    private void addButton(Screen screen, String text, int x, int y, int w, int h, ButtonWidget.PressAction action) {
        Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal(text), action).dimensions(x, y, w, h).build());
    }

    private boolean isTrash(ItemStack stack) {
        return stack.isOf(Items.ROTTEN_FLESH) || stack.isOf(Items.POISONOUS_POTATO) || stack.isOf(Items.DIRT) || stack.isOf(Items.COBBLESTONE);
    }

    private boolean isArmor(ItemStack stack) {
        String name = stack.getItem().toString();
        return name.contains("helmet") || name.contains("chestplate") || name.contains("leggings") || name.contains("boots");
    }

    public static class Module {
        String name; 
        boolean enabled;
        public Module(String name, boolean enabled) { 
            this.name = name; 
            this.enabled = enabled; 
        }
    }
}
