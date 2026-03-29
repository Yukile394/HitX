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
import net.minecraft.entity.EquipmentSlot;
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

        // ================= 1. GUI / EKRAN BUTONLARI =================
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
                        if (isArmor(stack)) {
                            client.interactionManager.clickSlot(syncId, i, 0, SlotActionType.QUICK_MOVE, client.player);
                        }
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

                // Auto-Sprint
                if (client.options.forwardKey.isPressed()
                        && !client.player.horizontalCollision
                        && !client.player.isSneaking()
                        && client.player.getHungerManager().getFoodLevel() > 6) {
                    client.player.setSprinting(true);
                }

                // Fullbright (Gece Görüşü — Parçacıksız, Gösterimsiz)
                if (!client.player.hasStatusEffect(StatusEffects.NIGHT_VISION)) {
                    client.player.addStatusEffect(
                        new StatusEffectInstance(StatusEffects.NIGHT_VISION, 400, 0, false, false, false)
                    );
                }
            }
        });

        // ================= 3. HUD ÖZELLİKLERİ =================
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.options.hudHidden) return;

            int screenWidth  = client.getWindow().getScaledWidth();
            int screenHeight = client.getWindow().getScaledHeight();

            // ── Animasyonlu RGB Renk (tüm HUD'lar paylaşır) ──
            float hue = (System.currentTimeMillis() % 3000) / 3000f;
            int rgbColor  = Color.HSBtoRGB(hue, 0.8f, 1.0f) | 0xFF000000;

            // ─────────────────────────────────────────────────
            // A) FPS GÖSTERGESİ — Sol Üst
            // ─────────────────────────────────────────────────
            String fpsText = "FPS: " + client.getCurrentFps();
            drawContext.drawText(client.textRenderer, fpsText, 5, 5, 0x00FF00, true);

            // ─────────────────────────────────────────────────
            // B) DÜŞÜK CAN UYARISI — Ortada
            // ─────────────────────────────────────────────────
            if (client.player.getHealth() <= 6.0f) {
                String warn = "⚠ DÜŞÜK CAN ⚠";
                int tw = client.textRenderer.getWidth(warn);
                drawContext.drawText(client.textRenderer, warn,
                    (screenWidth / 2) - (tw / 2),
                    (screenHeight / 2) - 30,
                    0xFF0000, true);
            }

            // ─────────────────────────────────────────────────
            // C) ZIRH HUD — Envanterin sağ yanında, sıralı
            //    Sıra: Kask (HEAD) → Göğüslük (CHEST) → Pantolon (LEGS) → Bot (FEET)
            //    Envanter slot'ları: 5=HEAD, 6=CHEST, 7=LEGS, 8=FEET (vanilla)
            //    Ama player.getEquippedStack ile direkt çekiyoruz.
            // ─────────────────────────────────────────────────

            // Envanterin sağ kenarını tahmin et: ekran ortası + 88 piksel (vanilla inv genişliği 176/2)
            int invRightEdge = (screenWidth / 2) + 88 + 8; // 8px boşluk
            int armorStartY  = (screenHeight / 2) - 36;    // Envanterin üstü civarı

            // Sıralı zırh slotları
            EquipmentSlot[] slots = {
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
            };

            for (int i = 0; i < slots.length; i++) {
                ItemStack armor = client.player.getEquippedStack(slots[i]);
                int itemX = invRightEdge;
                int itemY = armorStartY + i * 20; // 20px aralık → düzgün sıra

                if (!armor.isEmpty()) {
                    // İkon
                    drawContext.drawItem(armor, itemX, itemY);
                    drawContext.drawItemInSlot(client.textRenderer, armor, itemX, itemY);

                    // Dayanıklılık rengi + metni
                    if (armor.isDamageable()) {
                        int max       = armor.getMaxDamage();
                        int remaining = max - armor.getDamage();
                        float ratio   = (float) remaining / max;

                        int durColor = 0x00FF00;
                        if (ratio <= 0.5f) durColor = 0xFFFF00;
                        if (ratio <= 0.2f) durColor = 0xFF0000;

                        String durStr = remaining + "/" + max;
                        drawContext.drawText(client.textRenderer, durStr,
                            itemX + 18, itemY + 4, durColor, true);
                    }
                } else {
                    // Boş slot göstergesi (gri slot adı)
                    String emptyLabel = switch (slots[i]) {
                        case HEAD  -> "Kask";
                        case CHEST -> "Göğüs";
                        case LEGS  -> "Pantolon";
                        case FEET  -> "Bot";
                        default    -> "";
                    };
                    drawContext.drawText(client.textRenderer, "§7[" + emptyLabel + "]",
                        itemX, itemY + 4, 0x888888, false);
                }
            }

            // ─────────────────────────────────────────────────
            // D) TARGET HUD — Sol Üst, Orta Boyut (1.0x — sade, net)
            // ─────────────────────────────────────────────────
            if (client.targetedEntity instanceof PlayerEntity target) {

                float health    = target.getHealth();
                float maxHealth = target.getMaxHealth();

                // Konum: sol üstte, FPS yazısının altında, biraz sağda
                int targetX = 5;
                int targetY = 18; // FPS'nin hemen altı

                int boxW = 120;
                int boxH = 44;

                drawContext.getMatrices().push();
                drawContext.getMatrices().translate(targetX, targetY, 0);
                // Orta boyut → 1.0f (scale yok, net piksel)

                // Arka plan
                drawContext.fill(0, 0, boxW, boxH, 0xA0000000);

                // Animasyonlu çerçeve
                drawContext.fill(0, 0, boxW, 1,      rgbColor); // üst
                drawContext.fill(0, boxH - 1, boxW, boxH, rgbColor); // alt
                drawContext.fill(0, 0, 1, boxH,      rgbColor); // sol
                drawContext.fill(boxW - 1, 0, boxW, boxH, rgbColor); // sağ

                // Oyuncu adı (beyaz, bold görünüm için shadow=true)
                drawContext.drawText(client.textRenderer,
                    target.getName().getString(),
                    5, 5, 0xFFFFFF, true);

                // Can yazısı
                int hpColor = 0x00FF00;
                if (health <= maxHealth * 0.5f) hpColor = 0xFFFF00;
                if (health <= maxHealth * 0.25f) hpColor = 0xFF3333;

                String hpText = String.format("%.1f / %.1f ❤", health, maxHealth);
                drawContext.drawText(client.textRenderer, hpText, 5, 16, hpColor, true);

                // Can barı
                int barW       = boxW - 10;
                int filledW    = (int) ((health / maxHealth) * barW);

                drawContext.fill(5, 30, 5 + barW, 38, 0xFF333333);          // arka plan
                drawContext.fill(5, 30, 5 + filledW, 38, hpColor | 0xFF000000); // dolu kısım

                // Can barı yüzde etiketi (sağda küçük)
                String pctText = (int)((health / maxHealth) * 100) + "%";
                int pctW = client.textRenderer.getWidth(pctText);
                drawContext.drawText(client.textRenderer, pctText,
                    5 + barW - pctW, 30, 0xCCCCCC, false);

                drawContext.getMatrices().pop();
            }
        });
    }

    // ================= YARDIMCI METOTLAR =================

    private void addButton(Screen screen, String text, int x, int y, int w, int h,
                           ButtonWidget.PressAction action) {
        Screens.getButtons(screen).add(
            ButtonWidget.builder(Text.literal(text), action)
                .dimensions(x, y, w, h)
                .build()
        );
    }

    private boolean isTrash(ItemStack stack) {
        return stack.isOf(Items.ROTTEN_FLESH)
            || stack.isOf(Items.POISONOUS_POTATO)
            || stack.isOf(Items.DIRT)
            || stack.isOf(Items.COBBLESTONE)
            || stack.isOf(Items.GRAVEL)
            || stack.isOf(Items.SAND);
    }

    private boolean isArmor(ItemStack stack) {
        String name = stack.getItem().toString().toLowerCase();
        return name.contains("helmet")
            || name.contains("chestplate")
            || name.contains("leggings")
            || name.contains("boots");
    }

    public static class Module {
        String name;
        boolean enabled;

        public Module(String name, boolean enabled) {
            this.name    = name;
            this.enabled = enabled;
        }
    }
}
