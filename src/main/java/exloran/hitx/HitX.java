package com.exloran.hitx;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
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

import org.lwjgl.glfw.GLFW;

import java.util.List;

public class HitX implements ClientModInitializer {

    // ── Mod durumları ──
    private boolean hudEnabled     = true;
    private boolean nameTagEnabled = true;

    // ── Target HUD ──
    private PlayerEntity cachedTarget = null;
    private float        hudAlpha     = 0.0f;

    // ── /ah sell dupe ──
    // dupeState:
    //   0 = bekliyor
    //   1 = /ah sell <fiyat> gönderildi, onay bekleniyor
    //   2 = satıldı, /ah cancel ile geri alınıyor (dupe döngüsü)
    private boolean dupeActive    = false;
    private int     dupeTimer     = 0;
    private int     dupeStep      = 0;
    private String  dupePrice     = "1000";
    private int     dupeLoopCount = 0;

    // ── Sabitler ──
    private static final double SHOW_RANGE  = 6.5;  // Reach + biraz fazla
    private static final double DOT_THRESH  = 0.97;
    private static final float  FADE_SPEED  = 0.12f;
    private static final int    KEY_TOGGLE  = GLFW.GLFW_KEY_R;
    private static final int    KEY_NAMETAG = GLFW.GLFW_KEY_N;

    // Tuş debounce
    private boolean rWasDown = false;
    private boolean nWasDown = false;

    // Fiyat input için
    private String pendingPrice = null;

    @Override
    public void onInitializeClient() {

        // ================= 1. GUI BUTONLARI =================
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {

            // ── SANDIK ──
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

            // ── ENVANTER ──
            if (screen instanceof InventoryScreen inv) {
                int x      = (scaledWidth - 176) / 2;
                int y      = (scaledHeight - 166) / 2;
                int syncId = inv.getScreenHandler().syncId;

                // Normal butonlar
                addButton(screen, "🛡", x - 25,  y,       20, 20, b -> {
                    for (int i = 9; i < 45; i++) {
                        ItemStack stack = inv.getScreenHandler().getSlot(i).getStack();
                        if (isArmor(stack))
                            client.interactionManager.clickSlot(syncId, i, 0, SlotActionType.QUICK_MOVE, client.player);
                    }
                });
                addButton(screen, "🗑", x - 25,  y + 145, 20, 20, b -> {
                    for (int i = 9; i < 45; i++)
                        client.interactionManager.clickSlot(syncId, i, 1, SlotActionType.THROW, client.player);
                });

                // ── AH SELL DUPE BÖLÜMÜ ──
                // Sağ tarafa yerleştir
                int ahX = x + 181;
                int ahY = y;

                // Fiyat yazısı etiketi
                // "AH SELL DUPE" başlık butonu (tıklanamaz, sadece görsel)
                addButton(screen, "§6AH SELL", ahX, ahY, 60, 10, b -> {});

                // Fiyat: - + butonları ve değer göstergesi
                addButton(screen, "-", ahX, ahY + 12, 18, 14, b -> {
                    int p = safeParseInt(dupePrice, 1000);
                    dupePrice = String.valueOf(Math.max(1, p - 100));
                });
                addButton(screen, "+", ahX + 42, ahY + 12, 18, 14, b -> {
                    int p = safeParseInt(dupePrice, 1000);
                    dupePrice = String.valueOf(p + 100);
                });

                // DUPE BAŞLAT butonu
                addButton(screen, dupeActive ? "§cDUR" : "§aDUPE", ahX, ahY + 28, 60, 14, b -> {
                    if (!dupeActive) {
                        dupeActive    = true;
                        dupeStep      = 0;
                        dupeTimer     = 0;
                        dupeLoopCount = 0;
                        client.player.sendMessage(
                            Text.literal("§aDupe başlatıldı! Fiyat: §e" + dupePrice), true);
                        client.setScreen(null); // Envanteri kapat, dupe başlasın
                    } else {
                        dupeActive = false;
                        dupeStep   = 0;
                        dupeTimer  = 0;
                        client.player.sendMessage(Text.literal("§cDupe durduruldu!"), true);
                    }
                });

                // x1 / x5 / x10 satış adet seçimi (loop sayısı)
                addButton(screen, "x1",  ahX,      ahY + 44, 18, 12, b -> dupeLoopCount = 1);
                addButton(screen, "x5",  ahX + 20, ahY + 44, 18, 12, b -> dupeLoopCount = 5);
                addButton(screen, "x10", ahX + 40, ahY + 44, 20, 12, b -> dupeLoopCount = 10);
            }
        });

        // ─── Envanter ekranında fiyatı çiz ───
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof InventoryScreen) {
                ScreenEvents.afterRender(screen).register((scr, ctx, mx, my, delta) -> {
                    int x = (scaledWidth - 176) / 2;
                    int y = (scaledHeight - 166) / 2;
                    int ahX = x + 181;
                    int ahY = y;
                    // Fiyat göster
                    ctx.drawText(MinecraftClient.getInstance().textRenderer,
                        "§f" + dupePrice, ahX + 20, ahY + 15, 0xFFFFFF, true);
                    // Loop sayısı
                    ctx.drawText(MinecraftClient.getInstance().textRenderer,
                        "§7Loop: §e" + (dupeLoopCount == 0 ? "∞" : dupeLoopCount),
                        ahX, ahY + 58, 0xFFFFFF, false);
                    // Durum
                    ctx.drawText(MinecraftClient.getInstance().textRenderer,
                        dupeActive ? "§a● ÇALIŞIYOR" : "§c● DURDU",
                        ahX, ahY + 68, 0xFFFFFF, false);
                });
            }
        });

        // ================= 2. TICK =================
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;

            // ── Tuş kontrolleri ──
            boolean rDown = GLFW.glfwGetKey(client.getWindow().getHandle(), KEY_TOGGLE) == GLFW.GLFW_PRESS;
            if (rDown && !rWasDown) {
                hudEnabled = !hudEnabled;
                client.player.sendMessage(
                    Text.literal(hudEnabled ? "§a[HitX] HUD Açık" : "§c[HitX] HUD Kapalı"), true);
            }
            rWasDown = rDown;

            boolean nDown = GLFW.glfwGetKey(client.getWindow().getHandle(), KEY_NAMETAG) == GLFW.GLFW_PRESS;
            if (nDown && !nWasDown) {
                nameTagEnabled = !nameTagEnabled;
                client.player.sendMessage(
                    Text.literal(nameTagEnabled ? "§a[HitX] Can Barı Açık" : "§c[HitX] Can Barı Kapalı"), true);
            }
            nWasDown = nDown;

            // ── Auto-Sprint ──
            if (client.options.forwardKey.isPressed()
                    && !client.player.horizontalCollision
                    && !client.player.isSneaking()
                    && client.player.getHungerManager().getFoodLevel() > 6)
                client.player.setSprinting(true);

            // ── Fullbright ──
            if (!client.player.hasStatusEffect(StatusEffects.NIGHT_VISION))
                client.player.addStatusEffect(
                    new StatusEffectInstance(StatusEffects.NIGHT_VISION, 400, 0, false, false, false));

            // ── DUPE Döngüsü ──
            // Adımlar:
            //   Step 0: /ah sell <fiyat> gönder → 20 tick bekle
            //   Step 1: satış onaylandı, /ah cancel ile geri al → 20 tick bekle
            //   Step 2: döngüyü tekrarla (veya bitir)
            if (dupeActive && client.currentScreen == null) {
                dupeTimer--;
                if (dupeTimer <= 0) {
                    switch (dupeStep) {
                        case 0 -> {
                            // Elde bir item var mı kontrol et
                            ItemStack held = client.player.getMainHandStack();
                            if (!held.isEmpty()) {
                                client.player.networkHandler.sendChatCommand("ah sell " + dupePrice);
                                client.player.sendMessage(
                                    Text.literal("§e/ah sell " + dupePrice + " §7gönderildi"), true);
                            } else {
                                client.player.sendMessage(
                                    Text.literal("§c[HitX] Elde item yok! Dupe durdu."), true);
                                dupeActive = false;
                                break;
                            }
                            dupeTimer = 25; // ~1.25 saniye bekle (sunucu gecikmesi için)
                            dupeStep  = 1;
                        }
                        case 1 -> {
                            // /ah cancel ile geri al (dupe efekti — bazı sunucularda item geri gelir)
                            client.player.networkHandler.sendChatCommand("ah cancel");
                            client.player.sendMessage(
                                Text.literal("§e/ah cancel §7gönderildi"), true);
                            dupeTimer = 20;
                            dupeStep  = 2;
                        }
                        case 2 -> {
                            // Loop kontrolü
                            if (dupeLoopCount > 0) {
                                dupeLoopCount--;
                                if (dupeLoopCount == 0) {
                                    dupeActive = false;
                                    client.player.sendMessage(
                                        Text.literal("§a[HitX] Dupe tamamlandı!"), true);
                                    break;
                                }
                            }
                            // Devam et
                            dupeStep  = 0;
                            dupeTimer = 10;
                        }
                    }
                }
            }

            // ── Hedef tespiti ──
            boolean shouldShow = false;

            if (client.crosshairTarget instanceof EntityHitResult ehr
                    && ehr.getEntity() instanceof PlayerEntity p && p.isAlive()) {
                cachedTarget = p;
                shouldShow   = true;
            }

            if (!shouldShow) {
                Vec3d eye  = client.player.getCameraPosVec(1.0f);
                Vec3d look = client.player.getRotationVec(1.0f).normalize();
                Box box = client.player.getBoundingBox().expand(SHOW_RANGE);
                List<PlayerEntity> nearby = client.world.getEntitiesByClass(
                    PlayerEntity.class, box, e -> e != client.player && e.isAlive());

                PlayerEntity best    = null;
                double       bestDot = DOT_THRESH;
                for (PlayerEntity c : nearby) {
                    Vec3d dir = c.getCameraPosVec(1.0f).subtract(eye).normalize();
                    double dot = look.dotProduct(dir);
                    if (dot > bestDot) { bestDot = dot; best = c; }
                }
                if (best != null) { cachedTarget = best; shouldShow = true; }
            }

            if (!shouldShow) cachedTarget = null;

            hudAlpha = (shouldShow && hudEnabled)
                ? Math.min(1.0f, hudAlpha + FADE_SPEED)
                : Math.max(0.0f, hudAlpha - FADE_SPEED);
        });

        // ================= 3. HUD =================
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.options.hudHidden) return;

            int sw = client.getWindow().getScaledWidth();
            int sh = client.getWindow().getScaledHeight();

            // ── Sol üst: FPS + mod durumları ──
            drawContext.drawText(client.textRenderer,
                "§aFPS §f" + client.getCurrentFps(), 5, 5, 0xFFFFFF, true);
            drawContext.drawText(client.textRenderer,
                "§7HUD " + (hudEnabled ? "§a✔" : "§c✘") +
                " §7[§fR§7]", 5, 14, 0xFFFFFF, false);
            drawContext.drawText(client.textRenderer,
                "§7Bar " + (nameTagEnabled ? "§a✔" : "§c✘") +
                " §7[§fN§7]", 5, 23, 0xFFFFFF, false);
            if (dupeActive) {
                drawContext.drawText(client.textRenderer,
                    "§eDUPE §f" + dupePrice + " §7x" + (dupeLoopCount == 0 ? "∞" : dupeLoopCount),
                    5, 32, 0xFFFFFF, true);
            }

            // ── Düşük Can Uyarısı ──
            if (client.player.getHealth() <= 6.0f) {
                String warn = "⚠ DÜŞÜK CAN ⚠";
                int tw = client.textRenderer.getWidth(warn);
                drawContext.drawText(client.textRenderer, warn,
                    (sw / 2) - (tw / 2), (sh / 2) - 30, 0xFF0000, true);
            }

            // ── Oyuncu üstü can barları (2D projeksiyon) ──
            if (nameTagEnabled && client.world != null) {
                for (PlayerEntity player : client.world.getPlayers()) {
                    if (player == client.player || !player.isAlive()) continue;
                    double dist = client.player.distanceTo(player);
                    if (dist > SHOW_RANGE + 1.0) continue; // Reach mesafesi kadar

                    float health    = player.getHealth();
                    float maxHealth = player.getMaxHealth();
                    float hpRatio   = Math.max(0f, health / maxHealth);

                    // 3D → 2D projeksiyon
                    // Oyuncunun baş üstü pozisyonunu ekran koordinatına çevir
                    Vec3d worldPos = player.getPos().add(0,
                        player.getHeight() + 0.3, 0);

                    // Kamera ve projeksiyon hesabı
                    net.minecraft.client.render.Camera cam = client.gameRenderer.getCamera();
                    Vec3d camPos = cam.getPos();
                    Vec3d rel    = worldPos.subtract(camPos);

                    // Eğer kameranın arkasındaysa çizme
                    Vec3d look = client.player.getRotationVec(1.0f);
                    if (look.dotProduct(rel.normalize()) < 0) continue;

                    // Projeksiyon (basit manuel hesap)
                    double[] screen2d = worldToScreen(client, worldPos, sw, sh, tickDelta);
                    if (screen2d == null) continue;

                    int sx = (int) screen2d[0];
                    int sy = (int) screen2d[1];

                    // Uzaklığa göre boyut küçülsün
                    float sizeScale = (float) Math.max(0.3, 1.0 - dist / (SHOW_RANGE + 1.0) * 0.5);
                    int barW  = (int)(50 * sizeScale);
                    int barH  = (int)(4  * sizeScale);
                    int barX  = sx - barW / 2;
                    int barY  = sy - 2;
                    int fill  = Math.max(1, (int)(hpRatio * barW));

                    // Can rengi
                    int hpColor = healthColor(hpRatio);

                    // Arka plan
                    drawContext.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, 0xAA000000);
                    // Bar
                    drawContext.fill(barX, barY, barX + fill, barY + barH, hpColor);
                    // Parlaklık
                    drawContext.fill(barX, barY, barX + fill, barY + 1, 0x44FFFFFF);

                    // İsim (küçük)
                    if (dist < SHOW_RANGE) {
                        String pName = player.getName().getString();
                        int pw = client.textRenderer.getWidth(pName);
                        drawContext.drawText(client.textRenderer, pName,
                            sx - pw / 2, barY - 10, 0xFFFFFF, true);
                    }
                }
            }

            // ── Target HUD (sol alt) ──
            if (hudAlpha <= 0.01f || !hudEnabled) return;

            PlayerEntity target = cachedTarget;
            float health    = (target != null) ? target.getHealth()    : 0f;
            float maxHealth = (target != null) ? target.getMaxHealth() : 20f;
            int   hpInt     = (int) Math.ceil(health);
            float hpRatio   = (maxHealth > 0) ? Math.max(0f, health / maxHealth) : 0f;

            int   alpha     = (int)(hudAlpha * 255);
            int   hpColor   = healthColor(hpRatio);
            int   hpA       = (alpha << 24) | (hpColor & 0xFFFFFF);

            int boxW = 155;
            int boxH = 46;
            int rad  = 5;
            int boxX = (sw / 2) - 91 - boxW - 4;
            int boxY = sh - boxH - 24;

            drawContext.getMatrices().push();
            drawContext.getMatrices().translate(boxX, boxY, 200);

            int bg = (Math.min(alpha, 230) << 24) | 0x0A0A0A;
            drawContext.fill(rad, 0,   boxW - rad, boxH,       bg);
            drawContext.fill(0,   rad, boxW,       boxH - rad, bg);
            drawContext.fill(rad, 0, boxW - rad, 2, hpA); // Aksent çizgisi

            // Kafa
            if (target != null) {
                try {
                    Identifier skin = client.getSkinProvider()
                        .getSkinTextures(target.getGam
