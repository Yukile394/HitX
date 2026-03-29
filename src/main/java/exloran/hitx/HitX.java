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

import org.lwjgl.glfw.GLFW;

import java.util.List;

public class HitX implements ClientModInitializer {

    private boolean hudEnabled     = true;
    private boolean nameTagEnabled = true;
    private PlayerEntity cachedTarget = null;
    private float hudAlpha = 0.0f;

    private boolean dupeActive    = false;
    private int     dupeTimer     = 0;
    private int     dupeStep      = 0;
    private String  dupePrice     = "1000";
    private int     dupeLoopCount = 0;
    private int     dupeLoopMax   = 0;

    private static final double SHOW_RANGE = 6.5;
    private static final double DOT_THRESH = 0.97;
    private static final float  FADE_SPEED = 0.12f;
    private static final int    KEY_TOGGLE  = GLFW.GLFW_KEY_R;
    private static final int    KEY_NAMETAG = GLFW.GLFW_KEY_N;

    private boolean rWasDown = false;
    private boolean nWasDown = false;

    @Override
    public void onInitializeClient() {

        // ===== 1. GUI BUTONLARI =====
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {

            if (screen instanceof GenericContainerScreen container) {
                int xPos   = (scaledWidth / 2) + 92;
                int yPos   = (scaledHeight / 2) - 80;
                int syncId = container.getScreenHandler().syncId;
                addButton(screen, "Herseyi Al",  xPos, yPos,      85, 20, b -> {
                    int cs = container.getScreenHandler().getInventory().size();
                    for (int i = 0; i < cs; i++)
                        client.interactionManager.clickSlot(syncId, i, 0, SlotActionType.QUICK_MOVE, client.player);
                });
                addButton(screen, "Herseyi Koy", xPos, yPos + 24, 85, 20, b -> {
                    int cs = container.getScreenHandler().getInventory().size();
                    for (int i = cs; i < cs + 36; i++)
                        client.interactionManager.clickSlot(syncId, i, 0, SlotActionType.QUICK_MOVE, client.player);
                });
                addButton(screen, "Herseyi At",  xPos, yPos + 48, 85, 20, b -> {
                    for (int i = 0; i < container.getScreenHandler().slots.size(); i++)
                        client.interactionManager.clickSlot(syncId, i, 1, SlotActionType.THROW, client.player);
                });
                addButton(screen, "Copleri At",  xPos, yPos + 72, 85, 20, b -> {
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

                addButton(screen, "Zirhi Giy", x - 50, y,       46, 20, b -> {
                    for (int i = 9; i < 45; i++) {
                        ItemStack stack = inv.getScreenHandler().getSlot(i).getStack();
                        if (isArmor(stack))
                            client.interactionManager.clickSlot(syncId, i, 0, SlotActionType.QUICK_MOVE, client.player);
                    }
                });
                addButton(screen, "Temizle", x - 50, y + 22, 46, 20, b -> {
                    for (int i = 9; i < 45; i++)
                        client.interactionManager.clickSlot(syncId, i, 1, SlotActionType.THROW, client.player);
                });

                // AH SELL DUPE paneli — sag alt
                int ahX = x + 182;
                int ahY = y + 60;

                addButton(screen, "-100",  ahX,      ahY,      38, 16, b -> {
                    int p = safeInt(dupePrice, 1000);
                    dupePrice = String.valueOf(Math.max(1, p - 100));
                });
                addButton(screen, "+100",  ahX + 40, ahY,      38, 16, b -> {
                    int p = safeInt(dupePrice, 1000);
                    dupePrice = String.valueOf(p + 100);
                });
                addButton(screen, "x1",   ahX,      ahY + 18, 25, 14, b -> dupeLoopMax = 1);
                addButton(screen, "x5",   ahX + 27, ahY + 18, 25, 14, b -> dupeLoopMax = 5);
                addButton(screen, "x10",  ahX + 54, ahY + 18, 25, 14, b -> dupeLoopMax = 10);
                addButton(screen, "sonsuz", ahX,    ahY + 34, 79, 14, b -> dupeLoopMax = 0);
                addButton(screen, dupeActive ? ">> DUR <<" : ">> DUPE <<", ahX, ahY + 50, 79, 18, b -> {
                    if (!dupeActive) {
                        dupeActive    = true;
                        dupeStep      = 0;
                        dupeTimer     = 5;
                        dupeLoopCount = 0;
                        client.setScreen(null);
                        client.player.sendMessage(
                            Text.literal("§a[HitX] Dupe basladi! Fiyat: §e" + dupePrice
                                + " §7Loop: §e" + (dupeLoopMax == 0 ? "sonsuz" : dupeLoopMax)), true);
                    } else {
                        dupeActive = false;
                        dupeStep   = 0;
                        dupeTimer  = 0;
                        client.player.sendMessage(Text.literal("§c[HitX] Dupe durduruldu."), true);
                    }
                });
            }
        });

        // Envanter render - fiyat ve durum yazisi
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof InventoryScreen)) return;
            ScreenEvents.afterRender(screen).register((scr, ctx, mx, my, delta) -> {
                int x   = (scaledWidth - 176) / 2;
                int y   = (scaledHeight - 166) / 2;
                int ahX = x + 182;
                int ahY = y + 60;
                ctx.drawText(client.textRenderer, "§6AH SELL DUPE", ahX, ahY - 10, 0xFFAA00, true);
                ctx.drawText(client.textRenderer, "§fFiyat: §e" + dupePrice, ahX, ahY - 1, 0xFFFFFF, false);
                ctx.drawText(client.textRenderer,
                    "§7Loop: §e" + (dupeLoopMax == 0 ? "sonsuz" : dupeLoopMax),
                    ahX + 40, ahY + 19, 0xFFFFFF, false);
                ctx.drawText(client.textRenderer,
                    dupeActive ? "§a● CALISIYOR" : "§c● DURDU",
                    ahX, ahY + 70, 0xFFFFFF, false);
            });
        });

        // ===== 2. TICK =====
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;

            // Tus kontrolleri
            boolean rDown = GLFW.glfwGetKey(client.getWindow().getHandle(), KEY_TOGGLE) == GLFW.GLFW_PRESS;
            if (rDown && !rWasDown) {
                hudEnabled = !hudEnabled;
                client.player.sendMessage(
                    Text.literal(hudEnabled ? "§a[HitX] Target HUD Acik" : "§c[HitX] Target HUD Kapali"), true);
            }
            rWasDown = rDown;

            boolean nDown = GLFW.glfwGetKey(client.getWindow().getHandle(), KEY_NAMETAG) == GLFW.GLFW_PRESS;
            if (nDown && !nWasDown) {
                nameTagEnabled = !nameTagEnabled;
                client.player.sendMessage(
                    Text.literal(nameTagEnabled ? "§a[HitX] Can Bari Acik" : "§c[HitX] Can Bari Kapali"), true);
            }
            nWasDown = nDown;

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

            // Dupe
            if (dupeActive && client.currentScreen == null) {
                dupeTimer--;
                if (dupeTimer <= 0) {
                    switch (dupeStep) {
                        case 0 -> {
                            ItemStack held = client.player.getMainHandStack();
                            if (held.isEmpty()) {
                                dupeActive = false;
                                client.player.sendMessage(
                                    Text.literal("§c[HitX] Elde item yok! Dupe durdu."), true);
                                break;
                            }
                            client.player.networkHandler.sendChatCommand("ah sell " + dupePrice);
                            client.player.sendMessage(
                                Text.literal("§7>> §e/ah sell " + dupePrice + " §7gonderildi"), true);
                            dupeTimer = 25;
                            dupeStep  = 1;
                        }
                        case 1 -> {
                            client.player.networkHandler.sendChatCommand("ah cancel");
                            client.player.sendMessage(
                                Text.literal("§7>> §e/ah cancel §7gonderildi"), true);
                            dupeTimer = 20;
                            dupeStep  = 2;
                        }
                        case 2 -> {
                            dupeLoopCount++;
                            if (dupeLoopMax > 0 && dupeLoopCount >= dupeLoopMax) {
                                dupeActive = false;
                                client.player.sendMessage(
                                    Text.literal("§a[HitX] Dupe tamamlandi! " + dupeLoopCount + " loop yapildi."), true);
                                break;
                            }
                            dupeStep  = 0;
                            dupeTimer = 10;
                        }
                    }
                }
            }

            // Hedef tespiti
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

        // ===== 3. HUD =====
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.options.hudHidden) return;

            int sw = client.getWindow().getScaledWidth();
            int sh = client.getWindow().getScaledHeight();

            // Sol ust bilgiler
            drawContext.drawText(client.textRenderer,
                "§aFPS §f" + client.getCurrentFps(), 5, 5, 0xFFFFFF, true);
            drawContext.drawText(client.textRenderer,
                "§7HUD " + (hudEnabled ? "§a[R]" : "§c[R]"), 5, 14, 0xFFFFFF, false);
            drawContext.drawText(client.textRenderer,
                "§7Bar " + (nameTagEnabled ? "§a[N]" : "§c[N]"), 5, 23, 0xFFFFFF, false);
            if (dupeActive) {
                drawContext.drawText(client.textRenderer,
                    "§6DUPE §f" + dupePrice + " §7(" + dupeLoopCount
                        + "/" + (dupeLoopMax == 0 ? "sonsuz" : dupeLoopMax) + ")",
                    5, 32, 0xFFFFFF, true);
            }

            // Dusuk can uyarisi
            if (client.player.getHealth() <= 6.0f) {
                String warn = "DUSUK CAN";
                int tw = client.textRenderer.getWidth(warn);
                drawContext.drawText(client.textRenderer, warn,
                    (sw / 2) - (tw / 2), (sh / 2) - 30, 0xFF2222, true);
            }

            // Oyuncu ustu can barlari (2D projeksiyon)
            if (nameTagEnabled && client.world != null) {
                for (PlayerEntity player : client.world.getPlayers()) {
                    if (player == client.player || !player.isAlive()) continue;
                    double dist = client.player.distanceTo(player);
                    if (dist > SHOW_RANGE + 0.5) continue;

                    float hp    = player.getHealth();
                    float maxHp = player.getMaxHealth();
                    float ratio = Math.max(0f, hp / maxHp);

                    Vec3d worldPos = player.getPos().add(0, player.getHeight() + 0.25, 0);
                    double[] sc = project(client, worldPos, sw, sh);
                    if (sc == null) continue;

                    int sx   = (int) sc[0];
                    int sy   = (int) sc[1];
                    float sz = (float) Math.max(0.4, 1.0 - dist / (SHOW_RANGE + 1.0) * 0.4);
                    int barW = (int)(48 * sz);
                    int barH = (int)(4  * sz);
                    int bx   = sx - barW / 2;
                    int by   = sy;
                    int fill = Math.max(1, (int)(ratio * barW));
                    int col  = hpColor(ratio);

                    drawContext.fill(bx - 1, by - 1, bx + barW + 1, by + barH + 1, 0xAA000000);
                    drawContext.fill(bx, by, bx + fill, by + barH, col);
                    drawContext.fill(bx, by, bx + fill, by + 1, 0x33FFFFFF);

                    if (dist < SHOW_RANGE) {
                        String pn = player.getName().getString();
                        int pw = client.textRenderer.getWidth(pn);
                        drawContext.drawText(client.textRenderer, pn,
                            sx - pw / 2, by - 10, 0xFFFFFF, true);
                    }
                }
            }

            // Target HUD
            if (hudAlpha <= 0.01f || !hudEnabled) return;
            PlayerEntity target = cachedTarget;
            float health    = target != null ? target.getHealth()    : 0f;
            float maxHealth = target != null ? target.getMaxHealth() : 20f;
            int   hpInt     = (int) Math.ceil(health);
            float hpRatio   = maxHealth > 0 ? Math.max(0f, health / maxHealth) : 0f;
            int   alpha     = (int)(hudAlpha * 255);
            int   col       = hpColor(hpRatio);
            int   hpA       = (alpha << 24) | (col & 0x00FFFFFF);

            int boxW = 155, boxH = 46, rad = 5;
            int boxX = (sw / 2) - 91 - boxW - 4;
            int boxY = sh - boxH - 24;

            drawContext.getMatrices().push();
            drawContext.getMatrices().translate(boxX, boxY, 200);

            int bg = (Math.min(alpha, 230) << 24) | 0x0A0A0A;
            drawContext.fill(rad, 0,   boxW - rad, boxH,       bg);
            drawContext.fill(0,   rad, boxW,       boxH - rad, bg);
            drawContext.fill(rad, 0, boxW - rad, 2, hpA);

            if (target != null) {
                try {
                    Identifier skin = client.getSkinProvider()
                        .getSkinTextures(target.getGameProfile()).texture();
                    int hx = 6, hy = (boxH - 20) / 2;
                    drawContext.fill(hx - 1, hy - 1, hx + 21, hy + 21,
                        (Math.min(alpha, 100) << 24) | 0x000000);
                    drawContext.drawTexture(skin, hx, hy, 20, 20, 8,  8, 8, 8, 64, 64);
                    drawContext.drawTexture(skin, hx, hy, 20, 20, 40, 8, 8, 8, 64, 64);
                } catch (Exception ignored) {}
            }

            int tx = 32;
            drawContext.drawText(client.textRenderer, "TARGET",
                tx, 4, (Math.min(alpha, 180) << 24) | 0x88BBFF, false);
            drawContext.drawText(client.textRenderer,
                target != null ? target.getName().getString() : "---",
                tx, 13, (alpha << 24) | 0xFFFFFF, true);

            String hpStr = hpInt + " H";
            int hpW = client.textRenderer.getWidth(hpStr);
            drawContext.drawText(client.textRenderer, hpStr,
                boxW - hpW - 6, 13, hpA, true);

            int barX = tx, barY = 29, barW = boxW - tx - 6, barH = 7;
            int fill  = Math.max(1, (int)(hpRatio * barW));
            drawContext.fill(barX, barY, barX + barW, barY + barH,
                (Math.min(alpha, 200) << 24) | 0x1A1A1A);
            drawContext.fill(barX, barY, barX + fill, barY + barH, hpA);
            drawContext.fill(barX, barY, barX + fill, barY + 2,
                (Math.min(alpha / 4, 50) << 24) | 0xFFFFFF);

            drawContext.getMatrices().pop();
        });
    }

    // 3D -> 2D projeksiyon
    private double[] project(MinecraftClient client, Vec3d world, int sw, int sh) {
        try {
            net.minecraft.client.render.Camera cam = client.gameRenderer.getCamera();
            Vec3d rel = world.subtract(cam.getPos());
            Vec3d look = client.player.getRotationVec(1.0f);
            if (look.dotProduct(rel.normalize()) < 0) return null;

            float yaw   = cam.getYaw();
            float pitch = cam.getPitch();
            double yr = Math.toRadians(yaw);
            double pr = Math.toRadians(pitch);

            double rx  =  rel.x * Math.cos(yr) - rel.z * Math.sin(yr);
            double ry  =  rel.y;
            double rz  =  rel.x * Math.sin(yr) + rel.z * Math.cos(yr);
            double ry2 =  ry * Math.cos(pr) - rz * Math.sin(pr);
            double rz2 =  ry * Math.sin(pr) + rz * Math.cos(pr);

            if (rz2 <= 0.05) return null;

            double fov  = Math.toRadians(client.options.getFov().getValue());
            double proj = sw / (2.0 * Math.tan(fov / 2.0));
            double sx   = (sw / 2.0) + (rx  / rz2) * proj;
            double sy   = (sh / 2.0) - (ry2 / rz2) * proj;

            if (sx < -100 || sx > sw + 100 || sy < -100 || sy
