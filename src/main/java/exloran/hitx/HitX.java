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
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public class HitX implements ClientModInitializer {

    private PlayerEntity cachedTarget = null;
    private float hudAlpha = 0.0f;

    // Sadece oyuncunun hitbox'ına bakarken göster
    // crosshair tam hitbox'a değiyorsa = targetedEntity PlayerEntity
    // yoksa 6 blok içinde ve crosshair ±15° içindeyse göster
    private static final double SHOW_RANGE  = 6.0;
    private static final double DOT_THRESH  = 0.97; // ~14° — çok dar, yere bakınca tetiklenmez
    private static final float  FADE_SPEED  = 0.10f;

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

        // ================= 2. PVP + HEDEF =================
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;

            // Auto-Sprint
            if (client.options.forwardKey.isPressed()
                    && !client.player.horizontalCollision
                    && !client.player.isSneaking()
                    && client.player.getHungerManager().getFoodLevel() > 6)
                client.player.setSprinting(true);

            // Fullbright
            if (!client.player.hasStatusEffect(StatusEffects.NIGHT_VISION))
                client.player.addStatusEffect(
                    new StatusEffectInstance(StatusEffects.NIGHT_VISION, 400, 0, false, false, false));

            // ── Hedef Tespiti ──
            boolean shouldShow = false;

            // Yöntem 1: Crosshair TAM olarak bir PlayerEntity'ye değiyor (en güvenilir)
            if (client.crosshairTarget instanceof EntityHitResult ehr
                    && ehr.getEntity() instanceof PlayerEntity p
                    && p.isAlive()) {
                cachedTarget = p;
                shouldShow   = true;
            }

            // Yöntem 2: Crosshair başka bir şeye değiyor ama yakında oyuncu var
            // Bu durumda çok dar açı (DOT_THRESH = 0.97 ≈ 14°) ve mesafe kontrolü yap
            if (!shouldShow) {
                Vec3d eyePos = client.player.getCameraPosVec(1.0f);
                Vec3d look   = client.player.getRotationVec(1.0f).normalize();

                Box searchBox = client.player.getBoundingBox().expand(SHOW_RANGE);
                List<PlayerEntity> nearby = client.world.getEntitiesByClass(
                    PlayerEntity.class, searchBox,
                    e -> e != client.player && e.isAlive()
                );

                PlayerEntity best     = null;
                double       bestDot  = DOT_THRESH; // Eşiğin altındakiler kabul edilmez

                for (PlayerEntity candidate : nearby) {
                    // Oyuncunun hitbox merkezine bak (göz seviyesi)
                    Vec3d targetCenter = candidate.getCameraPosVec(1.0f);
                    Vec3d dir = targetCenter.subtract(eyePos).normalize();
                    double dot = look.dotProduct(dir);

                    if (dot > bestDot) {
                        bestDot = dot;
                        best    = candidate;
                    }
                }

                if (best != null) {
                    cachedTarget = best;
                    shouldShow   = true;
                }
            }

            if (!shouldShow) cachedTarget = null;

            // Smooth fade
            hudAlpha = shouldShow
                ? Math.min(1.0f, hudAlpha + FADE_SPEED)
                : Math.max(0.0f, hudAlpha - FADE_SPEED);
        });

        // ================= 3. HUD =================
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.options.hudHidden) return;

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

            // Fade yoksa çizme
            if (hudAlpha <= 0.01f) return;

            PlayerEntity target = cachedTarget;
            float health    = (target != null) ? target.getHealth()    : 0f;
            float maxHealth = (target != null) ? target.getMaxHealth() : 20f;
            int   hpInt     = (int) Math.ceil(health);
            float hpRatio   = (maxHealth > 0) ? Math.max(0f, health / maxHealth) : 0f;

            int alpha = (int)(hudAlpha * 255);

            // ── Can rengine göre bar rengi (yeşil → sarı → kırmızı) ──
            int hpR, hpG;
            if (hpRatio > 0.5f) {
                float t = (hpRatio - 0.5f) / 0.5f;
                hpR = (int) lerp(255f, 80f,  t);
                hpG = (int) lerp(200f, 210f, t);
            } else {
                float t = hpRatio / 0.5f;
                hpR = 220;
                hpG = (int) lerp(30f, 200f, t);
            }
            int hpBarColor  = (alpha << 24) | (hpR << 16) | (hpG << 8) | 0x44;
            int hpTextColor = (alpha << 24) | (hpR << 16) | (hpG << 8) | 0x44;

            // ── Kutu boyutları — orta boyut ──
            int boxW = 155;
            int boxH = 46;
            int rad  = 5;

            // ── Konum: Kırmızı can göstergesinin (vanilla heart HUD) yanına ──
            // Vanilla can barı: ekranın ortasından solda, hotbar'ın hemen üstü
            // sh - 39 civarında başlar. Biz onun soluna koyuyoruz.
            int hotbarY  = sh - 22;          // hotbar'ın üst kenarı yaklaşık
            int boxX     = (sw / 2) - 91 - boxW - 4;  // can barının hemen solunda
            int boxY     = hotbarY - boxH - 2;

            drawContext.getMatrices().push();
            drawContext.getMatrices().translate(boxX, boxY, 200);

            // Arka plan (yuvarlak köşeli)
            int bgColor = (Math.min(alpha, 230) << 24) | 0x0A0A0A;
            drawContext.fill(rad, 0,   boxW - rad, boxH,       bgColor);
            drawContext.fill(0,   rad, boxW,       boxH - rad, bgColor);

            // Üst aksent çizgisi — can rengine göre dinamik
            drawContext.fill(rad, 0, boxW - rad, 2, hpBarColor);

            // ── Oyuncu kafası (20x20) ──
            if (target != null) {
                try {
                    Identifier skin = client.getSkinProvider()
                        .getSkinTextures(target.getGameProfile()).texture();
                    int hx = 6, hy = (boxH - 20) / 2;
                    // Kafa gölgesi
                    drawContext.fill(hx - 1, hy - 1, hx + 21, hy + 21,
                        (Math.min(alpha, 100) << 24) | 0x000000);
                    // Kafa layer 1
                    drawContext.drawTexture(skin, hx, hy, 20, 20, 8,  8, 8, 8, 64, 64);
                    // Kafa overlay (şapka)
                    drawContext.drawTexture(skin, hx, hy, 20, 20, 40, 8, 8, 8, 64, 64);
                } catch (Exception ignored) {}
            }

            int textX = 32;

            // ── "TARGET" etiketi ──
            int subAlpha = Math.min(alpha, 180);
            drawContext.drawText(client.textRenderer, "TARGET",
                textX, 4, (subAlpha << 24) | 0x88BBFF, false);

            // ── Oyuncu adı ──
            String name = (target != null) ? target.getName().getString() : "---";
            drawContext.drawText(client.textRenderer, name,
                textX, 13, (alpha << 24) | 0xFFFFFF, true);

            // ── Can sayısı (sağda, can rengiyle) ──
            String hpStr = hpInt + " ❤";
            int hpW = client.textRenderer.getWidth(hpStr);
            drawContext.drawText(client.textRenderer, hpStr,
                boxW - hpW - 6, 13, hpTextColor, true);

            // ── Can barı ──
            int barX   = textX;
            int barY   = 29;
            int barW   = boxW - barX - 6;
            int barH   = 7;
            int filled = Math.max(1, (int)(hpRatio * barW));

            // Arka plan
            drawContext.fill(barX, barY, barX + barW, barY + barH,
                (Math.min(alpha, 200) << 24) | 0x1A1A1A);
            // Dolu kısım
            drawContext.fill(barX, barY, barX + filled, barY + barH, hpBarColor);
            // İnce parlaklık
            drawContext.fill(barX, barY, barX + filled, barY + 2,
                (Math.min(alpha / 4, 50) << 24) | 0xFFFFFF);

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
