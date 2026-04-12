package exloran.hitx;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

public class HitX implements ClientModInitializer {

    // ── Modül Durumları ──────────────────────────────────────
    public static boolean auraActive      = false;
    public static boolean criticalsActive = false;
    public static boolean hitBoxActive    = false;
    public static boolean triggerActive   = false;
    public static boolean elytraTarget    = true;

    // ── Ayarlar ──────────────────────────────────────────────
    public static float auraRange  = 3.8f;
    public static float hitboxSize = 0.4f;
    public static int   critMode   = 0;   // 0 = packet, 1 = jump

    // ── İç Değişkenler ───────────────────────────────────────
    private boolean      menuKeyLast = false;
    private LivingEntity auraTarget  = null;

    // ─────────────────────────────────────────────────────────
    @Override
    public void onInitializeClient() {

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;

            // M tuşu → Menü
            boolean mKey = GLFW.glfwGetKey(
                    client.getWindow().getHandle(), GLFW.GLFW_KEY_M)
                    == GLFW.GLFW_PRESS;
            if (mKey && !menuKeyLast) {
                client.setScreen(new ModernGui());
            }
            menuKeyLast = mKey;

            // ── KILLAURA ─────────────────────────────────────
            if (auraActive) {
                double range = (client.player.isFallFlying() && elytraTarget)
                        ? 6.0 : auraRange;

                LivingEntity best    = null;
                double       bestDist = Double.MAX_VALUE;

                for (Entity e : client.world.getEntities()) {
                    if (!(e instanceof LivingEntity le)) continue;
                    if (le == client.player)              continue;
                    if (!le.isAlive())                    continue;
                    double d = client.player.distanceTo(le);
                    if (d <= range && d < bestDist) {
                        bestDist = d;
                        best     = le;
                    }
                }
                auraTarget = best;

                if (best != null) {
                    // Hedefe smooth yaw/pitch
                    double dx = best.getX() - client.player.getX();
                    double dz = best.getZ() - client.player.getZ();
                    double dy = (best.getY() + best.getHeight() * 0.5)
                              - (client.player.getY() + client.player.getEyeHeight(
                                    client.player.getPose()));

                    float targetYaw   = (float) Math.toDegrees(Math.atan2(-dx, dz));
                    float targetPitch = (float) Math.toDegrees(
                            -Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));

                    client.player.setYaw(lerpAngle(
                            client.player.getYaw(), targetYaw, 0.4f));
                    client.player.setPitch(MathHelper.clamp(
                            lerpAngle(client.player.getPitch(), targetPitch, 0.4f),
                            -90f, 90f));

                    // Saldırı
                    if (client.player.getAttackCooldownProgress(0.5f) >= 1.0f) {
                        if (criticalsActive && client.player.isOnGround()) {
                            sendCritPackets(client);
                        }
                        client.interactionManager.attackEntity(client.player, best);
                        client.player.swingHand(Hand.MAIN_HAND);
                    }
                }
            } else {
                auraTarget = null;
            }

            // ── TRIGGERBOT ───────────────────────────────────
            if (triggerActive) {
                HitResult hr = client.crosshairTarget;
                if (hr instanceof EntityHitResult ehr) {
                    Entity t = ehr.getEntity();
                    if (t instanceof LivingEntity le && le.isAlive()) {
                        if (client.player.getAttackCooldownProgress(0.5f) >= 1.0f) {
                            if (criticalsActive && client.player.isOnGround()) {
                                sendCritPackets(client);
                            }
                            client.interactionManager.attackEntity(client.player, le);
                            client.player.swingHand(Hand.MAIN_HAND);
                        }
                    }
                }
            }
        });
    }

    // ── Kritik paket hilesi (NCP bypass) ─────────────────────
    private void sendCritPackets(MinecraftClient client) {
        if (client.getNetworkHandler() == null) return;
        double x = client.player.getX();
        double y = client.player.getY();
        double z = client.player.getZ();
        client.getNetworkHandler().sendPacket(
                new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.0625, z, false));
        client.getNetworkHandler().sendPacket(
                new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.0, z, false));
        client.getNetworkHandler().sendPacket(
                new PlayerMoveC2SPacket.PositionAndOnGround(x, y - 0.0625, z, false));
        client.getNetworkHandler().sendPacket(
                new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, true));
    }

    // ── Açı lerp (sarma destekli) ────────────────────────────
    private float lerpAngle(float from, float to, float t) {
        float delta = ((to - from) % 360 + 540) % 360 - 180;
        return from + delta * t;
    }

    // =========================================================
    //  MODERN GUI — Tamamen saf çizim, PNG YOK
    // =========================================================
    public class ModernGui extends Screen {

        private static final int W = 260;
        private static final int H = 300;

        private static final String[] MODULE_NAMES = {
            "KillAura", "TriggerBot", "Criticals", "HitBoxes"
        };

        private int selected = -1;
        private int hoverRow = -1;

        protected ModernGui() {
            super(Text.literal("HitX"));
        }

        // ── Durum getter/setter ───────────────────────────────
        private boolean getState(int i) {
            return switch (i) {
                case 0 -> auraActive;
                case 1 -> triggerActive;
                case 2 -> criticalsActive;
                case 3 -> hitBoxActive;
                default -> false;
            };
        }

        private void toggleModule(int i) {
            switch (i) {
                case 0 -> auraActive      = !auraActive;
                case 1 -> triggerActive   = !triggerActive;
                case 2 -> criticalsActive = !criticalsActive;
                case 3 -> hitBoxActive    = !hitBoxActive;
            }
        }

        // ── Render ───────────────────────────────────────────
        @Override
        public void render(DrawContext ctx, int mx, int my, float delta) {
            int cx = (width  - W) / 2;
            int cy = (height - H) / 2;

            // Hover hesapla
            hoverRow = -1;
            for (int i = 0; i < MODULE_NAMES.length; i++) {
                int ry = cy + 50 + i * 50;
                if (mx >= cx + 10 && mx <= cx + W - 10
                 && my >= ry      && my <= ry + 40) {
                    hoverRow = i;
                }
            }

            // Kart arka planı
            drawRoundRect(ctx, cx, cy, W, H, 12, 0xFF0D0018);

            // Mor üst şerit
            ctx.fill(cx + 12, cy,     cx + W - 12, cy + 2,  0xFFCC44FF);
            ctx.fill(cx,      cy + 2, cx + W,      cy + 44, 0xFF160025);
            ctx.fill(cx,      cy + 44, cx + W,     cy + 45, 0xFF330055);

            // Başlık
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "\u2694 HITX PREMIUM \u2694",
                    cx + W / 2, cy + 14, 0xFFEE88FF);
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "M = open/close",
                    cx + W / 2, cy + 30, 0xFF555566);

            // Modül satırları
            for (int i = 0; i < MODULE_NAMES.length; i++) {
                drawRow(ctx, cx, cy, i);
            }

            // Ayar paneli
            if (selected >= 0) {
                drawSettings(ctx, cx, cy);
            }

            // Alt bilgi
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "Left: toggle  |  Right: settings",
                    cx + W / 2, cy + H - 12, 0xFF333344);

            super.render(ctx, mx, my, delta);
        }

        // ── Modül Satırı ─────────────────────────────────────
        private void drawRow(DrawContext ctx, int cx, int cy, int idx) {
            boolean active = getState(idx);
            boolean hover  = (hoverRow == idx);
            boolean sel    = (selected == idx);

            int rx = cx + 10;
            int ry = cy + 50 + idx * 50;
            int rw = W - 20;
            int rh = 40;

            int bg = sel   ? 0xCC1A0035
                   : hover ? 0xAA130028
                           : 0x99090015;

            drawRoundRect(ctx, rx, ry, rw, rh, 7, bg);

            // Sol aktif çizgisi
            if (active) {
                ctx.fill(rx, ry + 5, rx + 3, ry + rh - 5, 0xFFCC44FF);
            }

            // İsim
            int nameColor = active ? 0xFFFFFFFF : 0xFF888899;
            ctx.drawTextWithShadow(textRenderer,
                    MODULE_NAMES[idx],
                    rx + 14, ry + rh / 2 - 4, nameColor);

            // ON etiketi
            if (active) {
                int lx = rx + rw - 76;
                int ly = ry + rh / 2 - 6;
                ctx.fill(lx, ly, lx + 28, ly + 12, 0xAA220044);
                ctx.drawCenteredTextWithShadow(textRenderer,
                        "ON", lx + 14, ly + 2, 0xFFCC44FF);
            }

            // Toggle switch
            drawSwitch(ctx, rx + rw - 38, ry + rh / 2 - 7, active);
        }

        // ── Toggle Switch ────────────────────────────────────
        private void drawSwitch(DrawContext ctx, int x, int y, boolean on) {
            int track = on ? 0xAA660099 : 0xAA222233;
            ctx.fill(x, y + 3, x + 28, y + 11, track);
            int kx    = on ? x + 16 : x + 2;
            int knob  = on ? 0xFFDD66FF : 0xFF555566;
            ctx.fill(kx, y, kx + 10, y + 14, knob);
            ctx.fill(kx + 1, y + 1, kx + 9, y + 13, on ? 0xFFFFAAFF : 0xFF777788);
        }

        // ── Ayar Paneli ──────────────────────────────────────
        private void drawSettings(DrawContext ctx, int cx, int cy) {
            int px = cx + 10;
            int py = cy + 50 + MODULE_NAMES.length * 50 + 4;
            int pw = W - 20;
            int ph = 70;

            drawRoundRect(ctx, px, py, pw, ph, 7, 0xCC0A0020);
            ctx.fill(px, py, px + pw, py + 2, 0xFFCC44FF);

            ctx.drawTextWithShadow(textRenderer,
                    "\u26a9 " + MODULE_NAMES[selected] + " Settings",
                    px + 10, py + 8, 0xFFDD88FF);

            switch (selected) {
                case 0 -> {
                    ctx.drawText(textRenderer,
                            "Range: " + String.format("%.1f", auraRange) + " blocks",
                            px + 10, py + 24, 0xFFCCCCCC, false);
                    ctx.drawText(textRenderer,
                            "[-]  [+]    ElytraTarget: " + (elytraTarget ? "ON" : "OFF"),
                            px + 10, py + 38, 0xFF888899, false);
                }
                case 2 -> {
                    ctx.drawText(textRenderer,
                            "Mode: " + (critMode == 0 ? "Packet" : "Jump"),
                            px + 10, py + 24, 0xFFCCCCCC, false);
                    ctx.drawText(textRenderer,
                            "[Click] to switch mode",
                            px + 10, py + 38, 0xFF888899, false);
                }
                case 3 -> {
                    ctx.drawText(textRenderer,
                            "Extra size: +" + String.format("%.2f", hitboxSize),
                            px + 10, py + 24, 0xFFCCCCCC, false);
                    ctx.drawText(textRenderer,
                            "[-]  [+]  to adjust",
                            px + 10, py + 38, 0xFF888899, false);
                }
                default -> ctx.drawText(textRenderer,
                        "No settings.",
                        px + 10, py + 30, 0xFF444455, false);
            }
        }

        // ── Mouse Click ──────────────────────────────────────
        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            int cx = (width  - W) / 2;
            int cy = (height - H) / 2;

            // Satır tıklamaları
            for (int i = 0; i < MODULE_NAMES.length; i++) {
                int rx = cx + 10;
                int ry = cy + 50 + i * 50;
                int rw = W - 20;
                int rh = 40;
                if (mx >= rx && mx <= rx + rw && my >= ry && my <= ry + rh) {
                    if (button == 0) {
                        toggleModule(i);
                    } else if (button == 1) {
                        selected = (selected == i) ? -1 : i;
                    }
                    return true;
                }
            }

            // Ayar paneli tıklamaları
            if (selected >= 0) {
                int px = cx + 10;
                int py = cy + 50 + MODULE_NAMES.length * 50 + 4;

                if (selected == 0 && my >= py + 34 && my <= py + 50) {
                    if (mx >= px + 10 && mx <= px + 22) {
                        auraRange = Math.max(1.5f, auraRange - 0.2f);
                    } else if (mx >= px + 30 && mx <= px + 42) {
                        auraRange = Math.min(6.0f, auraRange + 0.2f);
                    } else if (mx >= px + 60) {
                        elytraTarget = !elytraTarget;
                    }
                    return true;
                }

                if (selected == 2 && my >= py + 34 && my <= py + 50) {
                    critMode = 1 - critMode;
                    return true;
                }

                if (selected == 3 && my >= py + 34 && my <= py + 50) {
                    if (mx >= px + 10 && mx <= px + 22) {
                        hitboxSize = Math.max(0.05f, hitboxSize - 0.05f);
                    } else if (mx >= px + 30 && mx <= px + 42) {
                        hitboxSize = Math.min(1.5f, hitboxSize + 0.05f);
                    }
                    return true;
                }
            }

            return super.mouseClicked(mx, my, button);
        }

        // ── Yuvarlak Dikdörtgen ──────────────────────────────
        private void drawRoundRect(DrawContext ctx,
                                   int x, int y, int w, int h,
                                   int r, int color) {
            ctx.fill(x + r, y,     x + w - r, y + h,     color);
            ctx.fill(x,     y + r, x + r,     y + h - r, color);
            ctx.fill(x + w - r, y + r, x + w, y + h - r, color);

            for (int dx = 0; dx < r; dx++) {
                for (int dy = 0; dy < r; dy++) {
                    double dist = Math.sqrt(
                            (double)(r - dx) * (r - dx)
                          + (double)(r - dy) * (r - dy));
                    if (dist <= r) {
                        ctx.fill(x + dx,             y + dy,
                                 x + dx + 1,         y + dy + 1,         color);
                        ctx.fill(x + w - r + dx,     y + dy,
                                 x + w - r + dx + 1, y + dy + 1,         color);
                        ctx.fill(x + dx,             y + h - r + dy,
                                 x + dx + 1,         y + h - r + dy + 1, color);
                        ctx.fill(x + w - r + dx,     y + h - r + dy,
                                 x + w - r + dx + 1, y + h - r + dy + 1, color);
                    }
                }
            }
        }

        @Override public boolean shouldPause()      { return false; }
        @Override public boolean shouldCloseOnEsc() { return true;  }

    } // end ModernGui

} // end HitX
