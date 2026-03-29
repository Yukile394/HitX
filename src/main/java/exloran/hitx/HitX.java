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

    // Hedef oyuncu — Tick'te güncellenir, HUD'da kullanılır
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

            // ── Hedef Güncelleme ──
            // Önce crosshair'e bak (vanilla targetedEntity)
            if (client.targetedEntity instanceof PlayerEntity p) {
                cachedTarget = p;
                return;
            }

            // Crosshair'de yoksa: 16 blok içindeki en yakın PlayerEntity'yi al
            Box searchBox = client.player.getBoundingBox().expand(16.0);
            List<PlayerEntity> nearby = client.world.getEntitiesByClass(
                PlayerEntity.class,
                searchBox,
                e -> e != client.player && e.isAlive()
            );

            if (!nearby.isEmpty()) {
                // En yakınını bul
                cachedTarget = nearby.stream()
                    .min((a, b) -> Double.compare(
                        a.squaredDistanceTo(client.player),
                        b.squaredDistanceTo(client.player)
                    ))
                    .orElse(null);
            } else {
                cachedTarget = null;
            }
        });

        // ================= 3. HUD =================
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.options.hudHidden) return;

            int sw = client.getWindow().getScaledWidth();
            int sh = client.getWindow().getScaledHeight();

            // ── FPS ──
            drawContext.drawText(client.textRenderer, "FPS: " + client.getCurrentFps(), 5, 5, 0x00FF00, true);

            // ── Düşük Can Uyarısı ──
            if (client.player.getHealth() <= 6.0f) {
                String warn = "⚠ DÜŞÜK CAN ⚠";
                int tw = client.textRenderer.getWidth(warn);
                drawContext.drawText(client.textRenderer, warn,
                    (sw / 2) - (tw / 2), (sh / 2) - 30, 0xFF0000, true);
            }

            // ─────────────────────────────────────────────────────────────
            // TARGET HUD — Pojav tarzı, sağ orta
            // ─────────────────────────────────────────────────────────────
            PlayerEntity target = cachedTarget;
            if (target != null && target.isAlive()) {

                float health    = target.getHealth();
                float maxHealth = target.getMaxHealth();
                int   hpInt     = (int) Math.ceil(health);

                int boxW = 130;
                int boxH = 36;
                int rad  = 5;

                // Sağ taraf, dikey ortanın biraz yukarısı
                int boxX = sw - boxW - 12;
                int boxY = (sh / 2) - boxH / 2 - 20;

                // Animasyonlu bar rengi: Sarı → Pembe → Beyaz
                float cycle  = (System.currentTimeMillis() % 2000) / 2000f;
                int barColor = animatedBarColor(cycle);

                drawContext.getMatrices().push();
                drawContext.getMatrices().translate(boxX, boxY, 0);

                // ── Yuvarlak köşeli arka plan ──
                drawContext.fill(rad, 0,    boxW - rad, boxH,      0xEE111111);
                drawContext.fill(0,   rad,  boxW,       boxH - rad, 0xEE111111);

                // ── Oyuncu kafası (skin, 14x14) ──
                try {
                    Identifier skin = client.getSkinProvider()
                        .getSkinTextures(target.getGameProfile()).texture();
                    int hx = 6, hy = (boxH - 14) / 2;
                    drawContext.drawTexture(skin, hx, hy, 14, 14, 8,  8, 8, 8, 64, 64);
                    drawContext.drawTexture(skin, hx, hy, 14, 14, 40, 8, 8, 8, 64, 64);
                } catch (Exception ignored) {
                    // Skin yüklenmediyse boş bırak
                }

                // ── İsim ──
                String name = target.getName().getString();
                drawContext.drawText(client.textRenderer, name, 25, 5, 0xFFFFFF, true);

                // ── Sağ üstte can sayısı ──
                String hpStr = String.valueOf(hpInt);
                int hpW = client.textRenderer.getWidth(hpStr);
                drawContext.drawText(client.textRenderer, hpStr,
                    boxW - hpW - 5, 5, 0xFF000000 | barColor, true);

                // ── Can barı ──
                int barX   = 25;
                int barY   = 25;
                int barW   = boxW - barX - 6;
                int barH   = 5;
                int filled = Math.max(1, (int) ((health / maxHealth) * barW));

                // Arka plan
                drawContext.fill(barX, barY, barX + barW, barY + barH, 0xFF2A2A2A);
                // Dolu (animasyonlu renk)
                drawContext.fill(barX, barY, barX + filled, barY + barH, 0xFF000000 | barColor);
                // Parlaklık şeridi (üst ince beyaz)
                drawContext.fill(barX, barY, barX + filled, barY + 1, 0x55FFFFFF);

                drawContext.getMatrices().pop();
            }
        });
    }

    // ─────────────────────────────────────────
    // Sarı → Pembe → Beyaz → Sarı animasyonu
    // ─────────────────────────────────────────
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
        String name = stack.getItem().toString().toLowerCase();
        return name.contains("helmet") || name.contains("chestplate")
            || name.contains("leggings") || name.contains("boots");
    }

    public static class Module {
        String name; boolean enabled;
        public Module(String name, boolean enabled) { this.name = name; this.enabled = enabled; }
    }
}
