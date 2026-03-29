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

import java.util.List;

public class HitX implements ClientModInitializer {

    private PlayerEntity cachedTarget = null;

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

        // ================= 2. PVP + HEDEF CACHE =================
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

            // Hedef güncelle — önce crosshair, yoksa 16 blok içi en yakın
            if (client.targetedEntity instanceof PlayerEntity p) {
                cachedTarget = p;
            } else {
                Box box = client.player.getBoundingBox().expand(16.0);
                List<PlayerEntity> nearby = client.world.getEntitiesByClass(
                    PlayerEntity.class, box,
                    e -> e != client.player && e.isAlive()
                );
                cachedTarget = nearby.isEmpty() ? null : nearby.stream()
                    .min((a, b) -> Double.compare(
                        a.squaredDistanceTo(client.player),
                        b.squaredDistanceTo(client.player)))
                    .orElse(null);
            }
        });

        // ================= 3. HUD =================
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.options.hudHidden) return;

            int sw = client.getWindow().getScaledWidth();
            int sh = client.getWindow().getScaledHeight();

            // ── FPS (Sol Üst) ──
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
            // TARGET HUD
            // Konum: SOL ALT — hotbar'ın hemen üstü, scoreboard ile çakışmaz
            // ─────────────────────────────────────────────────────────────
            PlayerEntity target = cachedTarget;
            if (target != null && target.isAlive()) {

                float health    = target.getHealth();
                float maxHealth = target.getMaxHealth();
                int   hpInt     = (int) Math.ceil(health);

                // Animasyonlu renk: Sarı → Pembe → Beyaz
                float cycle  = (System.currentTimeMillis() % 2000) / 2000f;
                int barColor = animatedBarColor(cycle);

                // ── Boyutlar ──
                int boxW = 140;
                int boxH = 42;
                int rad  = 5;

                // ── Konum: Sol alt, hotbar'ın üstünde ──
                // Hotbar ~22px yüksekte + 2px boşluk
                int boxX = 8;
                int boxY = sh - boxH - 24;

                drawContext.getMatrices().push();
                drawContext.getMatrices().translate(boxX, boxY, 200); // z=200 → her şeyin üstünde

                // ── Arka plan: Yuvarlak köşeli koyu kutu ──
                drawContext.fill(rad, 0,    boxW - rad, boxH,       0xEE0D0D0D);
                drawContext.fill(0,   rad,  boxW,       boxH - rad, 0xEE0D0D0D);

                // ── Renkli üst çizgi (animasyonlu) ──
                drawContext.fill(rad, 0, boxW - rad, 2, 0xFF000000 | barColor);

                // ── Oyuncu kafası (16x16) ──
                try {
                    Identifier skin = client.getSkinProvider()
                        .getSkinTextures(target.getGameProfile()).texture();
                    // Kafa arka plan (koyu kare)
                    drawContext.fill(5, 6, 23, 24, 0x55000000);
                    // Kafa layer 1
                    drawContext.drawTexture(skin, 5, 6, 18, 18, 8,  8, 8, 8, 64, 64);
                    // Kafa overlay
                    drawContext.drawTexture(skin, 5, 6, 18, 18, 40, 8, 8, 8, 64, 64);
                } catch (Exception ignored) {}

                // ── Oyuncu adı (beyaz, bold-shadow) ──
                String name = target.getName().getString();
                // İsmin üstünde küçük "TARGET" etiketi
                drawContext.drawText(client.textRenderer,
                    "§7TARGET", 27, 4, 0xAAAAAA, false);
                drawContext.drawText(client.textRenderer,
                    "§f" + name, 27, 13, 0xFFFFFF, true);

                // ── Sağ tarafta can sayısı (büyük, animasyonlu renkle) ──
                String hpStr = hpInt + " §7❤";
                int hpW = client.textRenderer.getWidth(hpStr);
                drawContext.drawText(client.textRenderer,
                    hpStr, boxW - hpW - 6, 13, 0xFF000000 | barColor, true);

                // ── Can barı (altta, animasyonlu) ──
                int barX   = 27;
                int barY   = 28;
                int barW   = boxW - barX - 6;
                int barH   = 6;
                int filled = Math.max(1, (int) ((health / maxHealth) * barW));

                // Arka plan
                drawContext.fill(barX, barY, barX + barW, barY + barH, 0xFF1E1E1E);
                // Dolu kısım
                drawContext.fill(barX, barY, barX + filled, barY + barH, 0xFF000000 | barColor);
                // Parlaklık şeridi
                drawContext.fill(barX, barY, barX + filled, barY + 2, 0x44FFFFFF);
                // Yüzde etiketi (bar içinde sağda)
                String pct = (int)((health / maxHealth) * 100) + "%";
                int pctW = client.textRenderer.getWidth(pct);
                if (filled > pctW + 4) {
                    drawContext.drawText(client.textRenderer, pct,
                        barX + filled - pctW - 2, barY - 1, 0xDDFFFFFF, false);
                }

                drawContext.getMatrices().pop();
            }
        });
    }

    // ── Sarı → Pembe → Beyaz → Sarı ──
    private int animatedBarColor(float t) {
        float r, g, b;
        if (t < 0.33f) {
            float p = t / 0.33f;
            r = 1.0f; g = lerp(1.0f, 0.50f, p); b = lerp(0.0f, 0.75f, p);
        } else if (t < 0.66f) {
            float p = (t - 0.33f) / 0.33f;
            r = 1.0f; g = lerp(0.50f, 1.0f, p); b = lerp(0.75f, 1.0f, p);
        } else {
            float p = (t - 0.66f) / 0.34f;
            r = 1.0f; g = 1.0f; b = lerp(1.0f, 0.0f, p);
        }
        return ((int)(r * 255) << 16) | ((int)(g * 255) << 8) | (int)(b * 255);
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
