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
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public class HitX implements ClientModInitializer {

    private PlayerEntity cachedTarget  = null;

    // Fade animasyonu için alpha (0.0 = tamamen gizli, 1.0 = tam görünür)
    private float hudAlpha = 0.0f;

    // HUD'un ne zaman gösterileceği:
    // Oyuncunun hitbox'ına girince (mesafe < HIT_RANGE) VE bakıyorken
    private static final double HIT_RANGE   = 4.5;  // Blok — vanilla PvP menzili
    private static final float  FADE_SPEED  = 0.08f; // Fade hızı (0.0→1.0 adım)

    @Override
    public void onInitializeClient() {

        // ================= 1. GUI BUTONLARI =================
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {

            if (screen instanceof GenericContainerScreen container) {
                int xPos   = (scaledWidth / 2) + 92;
                int yPos   = (scaledHeight / 2) - 80;
                int syncId = container.getScreenHandler().syncId;

                addButton(screen, "Herşeyi Al",  xPos, yPos,      85, 20, b -> {
                    int cs = container.getScreenHandler().getInventory().size();
                    for (int i = 0; i < cs; i++)
                        client.interactionManager.clickSlot(syncId, i, 0, SlotActionType.QUICK_MOVE, client.player);
                });
                addButton(screen, "Herşeyi Koy", xPos, yPos + 24, 85, 20, b -> {
                    int cs = container.getScreenHandler().getInventory().size();
                    for (int i = cs; i < cs + 36; i++)
                        client.interactionManager.clickSlot(syncId, i, 0, SlotActionType.QUICK_MOVE, client.player);
                });
                addButton(screen, "Herşeyi At",  xPos, yPos + 48, 85, 20, b -> {
                    for (int i = 0; i < container.getScreenHandler().slots.size(); i++)
                        client.interactionManager.clickSlot(syncId, i, 1, SlotActionType.THROW, client.player);
                });
                addButton(screen, "Çöpleri At",  xPos, yPos + 72, 85, 20, b -> {
                    for (int i = 0; i < container.getScreenHandler().slots.size(); i++) {
                        ItemStack stack = container.getScreenHandler().getSlot(i).getStack();
                        if (isTrash(stack))
                            client.interactionManager.clickSlot(syncId, i, 1, SlotActionType.THROW, client.player);
                    }
                });
            }

            if (screen instanceof InventoryScreen inv) {
                int x      = (scaledWidth - 176) / 2;
                int y      = (scaledHeight - 166) / 2;
                int syncId = inv.getScreenHandler().syncId;

                addButton(screen, "🛡", x - 25,  y,       20, 20, b -> {
                    for (int i = 9; i < 45; i++) {
                        ItemStack stack = inv.getScreenHandler().getSlot(i).getStack();
                        if (isArmor(stack))
                            client.interactionManager.clickSlot(syncId, i, 0, SlotActionType.QUICK_MOVE, client.player);
                    }
                });
                addButton(screen, "⚙", x + 181, y,       20, 20, b -> {
                    if (client.player != null)
                        client.player.sendMessage(Text.literal("§eSıralama Modu Aktif!"), true);
                });
                addButton(screen, "🗑", x - 25,  y + 145, 20, 20, b -> {
                    for (int i = 9; i < 45; i++)
                        client.interactionManager.clickSlot(syncId, i, 1, SlotActionType.THROW, client.player);
                });
                addButton(screen, "H",  x + 181, y + 145, 20, 20, b -> {
                    for (int i = 36; i < 45; i++)
                        client.interactionManager.clickSlot(syncId, i, 1, SlotActionType.THROW, client.player);
                });
            }
        });

        // ================= 2. PVP + HEDEF + FADE =================
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;

            // Auto-Sprint
            if (client.options.forwardKey.isPressed()
                    && !client.player.horizontalCollision
                    && !client.player.isSneaking()
                    && client.player.getHungerManager().getFoodLevel() > 6) {
                client.player.setSprinting(true);
            }

            // Fullbright
            if (!client.player.hasStatusEffect(StatusEffects.NIGHT_VISION)) {
                client.player.addStatusEffect(
                    new StatusEffectInstance(StatusEffects.NIGHT_VISION, 400, 0, false, false, false)
                );
            }

            // ── Hedef & Koşul Kontrolü ──
            boolean shouldShow = false;

            // 1) Crosshair'de bir oyuncu var mı?
            if (client.targetedEntity instanceof PlayerEntity p && p.isAlive()) {
                double dist = client.player.distanceTo(p);
                if (dist <= HIT_RANGE) {
                    cachedTarget = p;
                    shouldShow   = true;
                }
            }

            // 2) Crosshair'de yoksa — hitbox menzilinde (HIT_RANGE) biri var mı?
            if (!shouldShow) {
                Box box = client.player.getBoundingBox().expand(HIT_RANGE);
                List<PlayerEntity> nearby = client.world.getEntitiesByClass(
                    PlayerEntity.class, box,
                    e -> e != client.player && e.isAlive()
                );

                // En yakını al ve bakış açısını kontrol et
                PlayerEntity closest = nearby.isEmpty() ? null : nearby.stream()
                    .min((a, b) -> Double.compare(
                        a.squaredDistanceTo(client.player),
                        b.squaredDistanceTo(client.player)))
                    .orElse(null);

                if (closest != null) {
                    // Bakış yönü kontrolü: oyuncunun bakış vektörü ile hedef yönü arasındaki açı < 90°
                    Vec3d look  = client.player.getRotationVec(1.0f).normalize();
                    Vec3d toTgt = closest.getPos().subtract(client.player.getPos()).normalize();
                    double dot  = look.dotProduct(toTgt); // 1.0 = tam karşı, 0.0 = 90°

                    if (dot > 0.0) { // Hedefe doğru bakıyorsa (herhangi bir açıda önde)
                        cachedTarget = closest;
                        shouldShow   = true;
                    }
                }
            }

            // Hedef yoksa veya koşul sağlanmıyorsa cache'i temizle
            if (!shouldShow) cachedTarget = null;

            // ── Smooth Fade ──
            if (shouldShow) {
                hudAlpha = Math.min(1.0f, hudAlpha + FADE_SPEED);
            } else {
                hudAlpha = Math.max(0.0f, hudAlpha - FADE_SPEED);
            }
        });

        // ================= 3. HUD =================
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.options.hudHidden) return;
            if (hudAlpha <= 0.01f) return; // Tamamen gizliyse hiç çizme

            int sw = client.getWindow().getScaledWidth();
            int sh = client.getWindow().getScaledHeight();

            // ── FPS ──
            drawContext.drawText(client.textRenderer,
                "FPS: " + client.getCurrentFps(), 5, 5, 0x00FF00, true);

            // ── Düşük Can Uyarısı ──
            if (client.player.getHealth() <= 6.0f) {
                String warn = "⚠ DÜŞÜK CAN ⚠";
                int tw = client.textRenderer.getWidth(warn);
                drawContext.drawText(client.textRenderer, warn,
                    (sw / 2) - (tw / 2), (sh / 2) - 30, 0xFF0000, true);
            }

            // ─────────────────────────────────────────────────────────────
            // TARGET HUD — Sol Alt, Sade Tasarım, Fade Animasyonlu
            // ─────────────────────────────────────────────────────────────
            PlayerEntity target = cachedTarget;
            if (target == null && hudAlpha <= 0.01f) return;

            // Alpha değerini 0-255 arasına çevir
            int a = (int)(hudAlpha * 255);

            // ── Sabit, göz yormayan renkler ──
            // Arka plan: koyu siyah (alpha ile)
            int bgColor   = (Math.min(a, 238) << 24) | 0x0D0D0D;
            // Üst çizgi: sakin mavi-beyaz
            int accentColor = (a << 24) | 0x88BBFF;
            // Can barı: sağlık durumuna göre (yeşil → sarı → kırmızı), sade
            // İsim: beyaz
            int nameColor = (a << 24) | 0xFFFFFF;
            // Alt yazı rengi
            int subColor  = (a << 24) | 0xAAAAAA;

            float health    = (target != null) ? target.getHealth()    : 0f;
            float maxHealth = (target != null) ? target.getMaxHealth() : 20f;
            int   hpInt     = (int) Math.ceil(health);
            float hpRatio   = (maxHealth > 0) ? (health / maxHealth) : 0f;

            // Can rengi — sade, göz yormaz
            int hpR, hpG;
            if (hpRatio > 0.5f) {
                // Yeşil → Sarı
                float t = (hpRatio - 0.5f) / 0.5f;
                hpR = (int)(lerp(255f, 80f,  t));
                hpG = (int)(lerp(220f, 210f, t));
            } else {
                // Sarı → Kırmızı
                float t = hpRatio / 0.5f;
                hpR = 220;
                hpG = (int)(lerp(0f, 220f, t));
            }
            int hpBarColor = (a << 24) | (hpR << 16) | (hpG << 8) | 0x44;

            // Kutu
            int boxW = 140;
            int boxH = 42;
            int rad  = 5;

            // Sol alt — hotbar üstü
            int boxX = 8;
            int boxY = sh - boxH - 24;

            drawContext.getMatrices().push();
            drawContext.getMatrices().translate(boxX, boxY, 200);

            // Arka plan
            drawContext.fill(rad, 0,   boxW - rad, boxH,       bgColor);
            drawContext.fill(0,   rad, boxW,       boxH - rad, bgColor);

            // Üst aksent çizgisi (sakin mavi-beyaz)
            drawContext.fill(rad, 0, boxW - rad, 2, accentColor);

            // Oyuncu kafası
            if (target != null) {
                try {
                    Identifier skin = client.getSkinProvider()
                        .getSkinTextures(target.getGameProfile()).texture();
                    drawContext.fill(5, 6, 23, 24, (Math.min(a, 80) << 24) | 0x000000);
                    drawContext.drawTexture(skin, 5, 6, 18, 18, 8,  8, 8, 8, 64, 64);
                    drawContext.drawTexture(skin, 5, 6, 18, 18, 40, 8, 8, 8, 64, 64);
                } catch (Exception ignored) {}
            }

            // "TARGET" etiketi + isim
            drawContext.drawText(client.textRenderer, "TARGET", 27, 4,  subColor,  false);
            String name = (target != null) ? target.getName().getString() : "...";
            drawContext.drawText(client.textRenderer, name,     27, 13, nameColor, true);

            // Can sayısı (sağda)
            String hpStr = hpInt + " ❤";
            int hpW = client.textRenderer.getWidth(hpStr);
            drawContext.drawText(client.textRenderer, hpStr,
                boxW - hpW - 6, 13, hpBarColor | 0xFF000000 & ((a << 24) | 0xFFFFFF), true);
            // Daha güvenli can rengi yazısı
            int hpTextColor = (a << 24) | (hpR << 16) | (hpG << 8) | 0x44;
            drawContext.drawText(client.textRenderer, hpStr, boxW - hpW - 6, 13, hpTextColor, true);

            // Can barı
            int barX   = 27;
            int barY   = 28;
            int barW   = boxW - barX - 6;
            int barH   = 6;
            int filled = Math.max(1, (int)(hpRatio * barW));

            int barBg  = (Math.min(a, 200) << 24) | 0x1E1E1E;
            drawContext.fill(barX, barY, barX + barW, barY + barH, barBg);
            drawContext.fill(barX, barY, barX + filled, barY + barH, hpBarColor);
            // İnce parlaklık
            int shine = (Math.min(a / 3, 60) << 24) | 0xFFFFFF;
            drawContext.fill(barX, barY, barX + filled, barY + 2, shine);

            // Yüzde
            String pct = (int)(hpRatio * 100) + "%";
            int pctW = client.textRenderer.getWidth(pct);
            if (filled > pctW + 4) {
                drawContext.drawText(client.textRenderer, pct,
                    barX + filled - pctW - 2, barY - 1, subColor, false);
            }

            drawContext.getMatrices().pop();
        });
    }

    private float lerp(float a, float b, float t) { return a + (b - a) * t; }

    private void addButton(Screen screen, String text, int x, int y, int w, int h,
                           ButtonWidget.PressAction action) {
        Screens.getButtons(screen).add(
            ButtonWidget.builder(Text.literal(text), action).dimensions(x, y, w, h).build()
        );
    }

    private boolean isTrash(ItemStack stack) {
        return stack.isOf(Items.ROTTEN_FLESH)  || stack.isOf(Items.POISONOUS_POTATO)
            || stack.isOf(Items.DIRT)          || stack.isOf(Items.COBBLESTONE)
            || stack.isOf(Items.GRAVEL)        || stack.isOf(Items.SAND);
    }

    private boolean isArmor(ItemStack stack) {
        String n = stack.getItem().toString().toLowerCase();
        return n.contains("helmet") || n.contains("chestplate")
            || n.contains("leggings") || n.contains("boots");
    }

    public static class Module {
        String name; boolean enabled;
        public Module(String name, boolean enabled) { this.name = name; this.enabled = enabled; }
    }
}
