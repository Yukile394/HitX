package exloran.hitx;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;

public class HitX implements ClientModInitializer {

    // ═══════════════════════════════════════════
    //  MODÜL DURUMLARI
    // ═══════════════════════════════════════════
    public static boolean auraActive      = false;
    public static boolean criticalsActive = false;
    public static boolean hitBoxActive    = false;
    public static boolean triggerActive   = false;
    public static boolean elytraTarget    = true;

    // ═══════════════════════════════════════════
    //  AYARLAR
    // ═══════════════════════════════════════════
    public static float auraRange   = 3.8f;   // KillAura menzil
    public static float hitboxSize  = 0.4f;   // HitBox genişletme
    public static int   critMode    = 0;       // 0 = packet, 1 = jump

    // ═══════════════════════════════════════════
    //  İÇ DEĞİŞKENLER
    // ═══════════════════════════════════════════
    private boolean      menuKeyLast  = false;
    private LivingEntity auraTarget   = null;
    private double       rotationYaw  = 0;     // hedef etrafında dönme açısı
    private int          critTick     = 0;
    private int          auraTick     = 0;

    // ═══════════════════════════════════════════
    //  INIT
    // ═══════════════════════════════════════════
    @Override
    public void onInitializeClient() {

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;

            // ── M tuşu → Menü ──────────────────────────────────────
            boolean mKey = GLFW.glfwGetKey(
                    client.getWindow().getHandle(), GLFW.GLFW_KEY_M)
                    == GLFW.GLFW_PRESS;
            if (mKey && !menuKeyLast) {
                client.setScreen(new ModernGui());
            }
            menuKeyLast = mKey;

            auraTick++;

            // ══════════════════════════════════════════════
            //  KILLAURA — En yakın hedefi seç, etrafında
            //  dönerek (yaw rotasyon) vur, elytra desteği
            // ══════════════════════════════════════════════
            if (auraActive) {
                double range = (client.player.isFallFlying() && elytraTarget) ? 6.0 : auraRange;

                // Hedef seç: en yakın living entity
                LivingEntity best = null;
                double bestDist = Double.MAX_VALUE;
                for (Entity e : client.world.getEntities()) {
                    if (!(e instanceof LivingEntity le)) continue;
                    if (le == client.player)              continue;
                    if (!le.isAlive())                    continue;
                    double d = client.player.distanceTo(le);
                    if (d <= range && d < bestDist) {
                        bestDist = d;
                        best = le;
                    }
                }
                auraTarget = best;

                if (best != null) {
                    // ── Hedefe doğru smooth yaw rotasyonu ──
                    double dx = best.getX() - client.player.getX();
                    double dz = best.getZ() - client.player.getZ();
                    double dy = (best.getY() + best.getHeight() * 0.5)
                              - (client.player.getY() + client.player.getEyeHeight(client.player.getPose()));

                    double targetYaw   = Math.toDegrees(Math.atan2(-dx, dz));
                    double targetPitch = Math.toDegrees(-Math.atan2(dy,
                                          Math.sqrt(dx * dx + dz * dz)));

                    // Smooth — her tick biraz yaklaş (anti-cheat dostu)
                    float currentYaw   = client.player.getYaw();
                    float currentPitch = client.player.getPitch();

                    float newYaw   = lerpAngle(currentYaw,   (float) targetYaw,   0.4f);
                    float newPitch = lerpAngle(currentPitch, (float) targetPitch, 0.4f);

                    client.player.setYaw(newYaw);
                    client.player.setPitch(MathHelper.clamp(newPitch, -90f, 90f));

                    // Dönen yaw referansı (görsel için)
                    rotationYaw += 8.0;

                    // ── Saldırı (cooldown dolunca) ──
                    float cd = client.player.getAttackCooldownProgress(0.5f);
                    if (cd >= 1.0f) {
                        // Criticals varsa önce packet jump
                        if (criticalsActive && client.player.isOnGround()) {
                            sendCriticalPackets(client);
                        }
                        client.interactionManager.attackEntity(client.player, best);
                        client.player.swingHand(Hand.MAIN_HAND);
                    }
                }
            } else {
                auraTarget = null;
            }

            // ══════════════════════════════════════════════
            //  TRIGGERBOT — Nişan hedefindeyse otomatik vur
            // ══════════════════════════════════════════════
            if (triggerActive) {
                HitResult hr = client.crosshairTarget;
                if (hr instanceof EntityHitResult ehr) {
                    Entity target = ehr.getEntity();
                    if (target instanceof LivingEntity le && le.isAlive()) {
                        float cd = client.player.getAttackCooldownProgress(0.5f);
                        if (cd >= 1.0f) {
                            if (criticalsActive && client.player.isOnGround()) {
                                sendCriticalPackets(client);
                            }
                            client.interactionManager.attackEntity(client.player, le);
                            client.player.swingHand(Hand.MAIN_HAND);
                        }
                    }
                }
            }

            // ══════════════════════════════════════════════
            //  HITBOXES — Tüm entity'lerin box'ını genişlet
            //  (EntityDimensions mixin gerekir, burada flag)
            // ══════════════════════════════════════════════
            // hitBoxActive flag'i → HitboxMixin tarafından kullanılır
            // (aşağıda mixin sınıfı örneği var)
        });
    }

    // ═══════════════════════════════════════════
    //  YARDIMCI: Kritik paket hilesi
    //  Yerden küçük yükseklik paketi gönder →
    //  sunucu "havada" zannetsin → kritik kayıt
    // ═══════════════════════════════════════════
    private void sendCriticalPackets(MinecraftClient client) {
        if (client.getNetworkHandler() == null) return;
        double x = client.player.getX();
        double y = client.player.getY();
        double z = client.player.getZ();

        // 3 aşamalı micro-hop (NCP bypass)
        client.getNetworkHandler().sendPacket(
                new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.0625, z, false));
        client.getNetworkHandler().sendPacket(
                new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.0, z, false));
        client.getNetworkHandler().sendPacket(
                new PlayerMoveC2SPacket.PositionAndOnGround(x, y - 0.0625, z, false));
        client.getNetworkHandler().sendPacket(
                new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, true));
    }

    // ═══════════════════════════════════════════
    //  YARDIMCI: Açı lerp (sarma destekli)
    // ═══════════════════════════════════════════
    private float lerpAngle(float from, float to, float t) {
        float delta = ((to - from) % 360 + 540) % 360 - 180;
        return from + delta * t;
    }

    // ═══════════════════════════════════════════
    //  MODERN GUI — Saf drawContext, PNG YOK
    // ═══════════════════════════════════════════
    public class ModernGui extends Screen {

        // Kart boyutu
        private static final int W = 260;
        private static final int H = 310;

        // Modüller: isim, aktif getter/setter referansı yoktur (switch kullanır)
        private static final String[] NAMES = {
            "KillAura", "TriggerBot", "Criticals", "HitBoxes"
        };

        // Seçili satır (ayar açma)
        private int selected = -1;

        // Fare yeri (hover efekti için)
        private int hoverRow = -1;

        protected ModernGui() {
            super(Text.literal("HitX"));
        }

        // ── Aktif mi? ───────────────────────────────
        private boolean getState(int i) {
            return switch (i) {
                case 0 -> auraActive;
                case 1 -> triggerActive;
                case 2 -> criticalsActive;
                case 3 -> hitBoxActive;
                default -> false;
            };
        }

        private void toggle(int i) {
            switch (i) {
                case 0 -> auraActive      = !auraActive;
                case 1 -> triggerActive   = !triggerActive;
                case 2 -> criticalsActive = !criticalsActive;
                case 3 -> hitBoxActive    = !hitBoxActive;
            }
        }

        // ── Ana Render ──────────────────────────────
        @Override
        public void render(DrawContext ctx, int mx, int my, float delta) {
            int cx = (width  - W) / 2;
            int cy = (height - H) / 2;

            // Hover satırını hesapla
            hoverRow = -1;
            for (int i = 0; i < NAMES.length; i++) {
                int ry = cy + 52 + i * 54;
                if (mx >= cx + 12 && mx <= cx + W - 12
                 && my >= ry      && my <= ry + 44) {
                    hoverRow = i;
                }
            }

            // ── 1. Kart arka planı ─────────────────
            // Dış gölge (birkaç katman koyu fill)
            for (int s = 6; s > 0; s--) {
                int alpha = 0x18 * (7 - s);
                ctx.fill(cx - s, cy - s, cx + W + s, cy + H + s,
                         (alpha << 24));
            }
            // Ana kart
            drawRoundRect(ctx, cx, cy, W, H, 14, 0xFF0D0018);

            // Mor kenar çizgisi
            drawRoundRectBorder(ctx, cx, cy, W, H, 14, 0xFFCC44FF, 2);

            // ── 2. Başlık alanı ────────────────────
            // Başlık gradient şeridi
            ctx.fill(cx + 14, cy, cx + W - 14, cy + 1, 0xFFCC44FF);
            drawRoundRect(ctx, cx, cy, W, 44, 14, 0xFF160025);
            ctx.fill(cx, cy + 30, cx + W, cy + 44, 0xFF0D0018); // düz köşe alt

            // ⚔ simge (kılıç karakteri) + başlık
            ctx.drawCenteredTextWithShadow(
                    textRenderer,
                    "§d✦ §fHITX §7PREMIUM §d✦",
                    cx + W / 2,
                    cy + 15,
                    0xFFFFFFFF
            );

            // Versiyon
            ctx.drawCenteredTextWithShadow(
                    textRenderer, "§8v2.0  |  M to toggle",
                    cx + W / 2, cy + 30, 0xFF444455);

            // ── 3. Modül Satırları ─────────────────
            for (int i = 0; i < NAMES.length; i++) {
                drawRow(ctx, cx, cy, i);
            }

            // ── 4. Seçili modül ayar paneli ────────
            if (selected >= 0) {
                drawSettings(ctx, cx, cy);
            }

            // ── 5. Alt bilgi ───────────────────────
            ctx.drawCenteredTextWithShadow(
                    textRenderer,
                    "§8Left: toggle  |  Right: settings",
                    cx + W / 2, cy + H - 14, 0xFF333344
            );

            super.render(ctx, mx, my, delta);
        }

        // ── Modül Satırı ────────────────────────────
        private void drawRow(DrawContext ctx, int cx, int cy, int idx) {
            boolean active  = getState(idx);
            boolean hover   = (hoverRow == idx);
            boolean sel     = (selected == idx);

            int rx = cx + 12;
            int ry = cy + 52 + idx * 54;
            int rw = W - 24;
            int rh = 44;

            // Arka plan
            int bg = sel   ? 0xCC1A0035 :
                     hover ? 0xAA130028 :
                             0x99090015;
            drawRoundRect(ctx, rx, ry, rw, rh, 8, bg);

            // Sol aktif çizgisi
            if (active) {
                ctx.fill(rx, ry + 6, rx + 3, ry + rh - 6, 0xFFCC44FF);
                // Parlama efekti
                ctx.fill(rx, ry + 6, rx + 1, ry + rh - 6, 0x88FF88FF);
            }

            // Modül adı
            int nameColor = active ? 0xFFFFFFFF : 0xFF888899;
            ctx.drawTextWithShadow(
                    textRenderer, NAMES[idx],
                    rx + 16, ry + rh / 2 - 4, nameColor
            );

            // Durum etiketi
            if (active) {
                int lx = rx + rw - 80;
                int ly = ry + rh / 2 - 6;
                ctx.fill(lx, ly, lx + 30, ly + 13, 0xAA220044);
                ctx.fill(lx, ly, lx + 30, ly + 1,  0xFFCC44FF);
                ctx.drawCenteredTextWithShadow(
                        textRenderer, "§dON", lx + 15, ly + 3, 0xFFCC44FF);
            }

            // Toggle switch (sağ taraf)
            drawSwitch(ctx, rx + rw - 42, ry + rh / 2 - 7, active);
        }

        // ── Toggle Switch ────────────────────────────
        private void drawSwitch(DrawContext ctx, int x, int y, boolean on) {
            // Track
            int trackBg = on ? 0xAA660099 : 0xAA222233;
            ctx.fill(x,      y + 3,  x + 30, y + 11, trackBg);
            ctx.fill(x + 1,  y + 2,  x + 29, y + 12, trackBg);

            // Knob
            int kx = on ? x + 17 : x + 2;
            int knobColor = on ? 0xFFDD66FF : 0xFF555566;
            ctx.fill(kx,     y,     kx + 11, y + 14, knobColor);
            ctx.fill(kx + 1, y + 1, kx + 10, y + 13,
                     on ? 0xFFFFAAFF : 0xFF777788);
        }

        // ── Ayar Paneli ─────────────────────────────
        private void drawSettings(DrawContext ctx, int cx, int cy) {
            int px = cx + 12;
            int py = cy + 52 + NAMES.length * 54 + 4;
            int pw = W - 24;
            int ph = 68;

            drawRoundRect(ctx, px, py, pw, ph, 8, 0xCC0A0020);
            ctx.fill(px, py, px + pw, py + 2, 0xFFCC44FF);

            ctx.drawTextWithShadow(
                    textRenderer,
                    "§d⚙ §f" + NAMES[selected] + " §7Settings",
                    px + 10, py + 8, 0xFFDDDDFF
            );

            switch (selected) {
                case 0 -> { // KillAura
                    ctx.drawText(textRenderer,
                            "§7Range: §d" + String.format("%.1f", auraRange) + " §8blocks",
                            px + 10, py + 24, 0xFFCCCCCC, false);
                    ctx.drawText(textRenderer,
                            "§8[§f-§8] §7decrease   §8[§f+§8] §7increase",
                            px + 10, py + 38, 0xFF888899, false);
                    ctx.drawText(textRenderer,
                            "§7ElytraTarget: " + (elytraTarget ? "§dON" : "§8OFF"),
                            px + 10, py + 52, 0xFFAAAAAA, false);
                }
                case 2 -> { // Criticals
                    ctx.drawText(textRenderer,
                            "§7Mode: §d" + (critMode == 0 ? "Packet (NCP)" : "Jump"),
                            px + 10, py + 24, 0xFFCCCCCC, false);
                    ctx.drawText(textRenderer,
                            "§8[§fClick§8] §7to switch mode",
                            px + 10, py + 38, 0xFF888899, false);
                    ctx.drawText(textRenderer,
                            "§8Packet mode = more compatible",
                            px + 10, py + 52, 0xFF555566, false);
                }
                case 3 -> { // HitBoxes
                    ctx.drawText(textRenderer,
                            "§7Size: §d+" + String.format("%.2f", hitboxSize),
                            px + 10, py + 24, 0xFFCCCCCC, false);
                    ctx.drawText(textRenderer,
                            "§8[§f-§8] §7smaller   §8[§f+§8] §7larger",
                            px + 10, py + 38, 0xFF888899, false);
                    ctx.drawText(textRenderer,
                            "§8Requires HitboxMixin enabled",
                            px + 10, py + 52, 0xFF555566, false);
                }
                default -> ctx.drawText(textRenderer,
                        "§8No settings available.",
                        px + 10, py + 30, 0xFF444455, false);
            }
        }

        // ── Mouse Click ─────────────────────────────
        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            int cx = (width  - W) / 2;
            int cy = (height - H) / 2;

            for (int i = 0; i < NAMES.length; i++) {
                int rx = cx + 12;
                int ry = cy + 52 + i * 54;
                int rw = W - 24;
                int rh = 44;

                if (mx >= rx && mx <= rx + rw && my >= ry && my <= ry + rh) {
                    if (button == 0) toggle(i);              // Sol tık: toggle
                    else if (button == 1)                    // Sağ tık: ayarlar
                        selected = (selected == i) ? -1 : i;
                    return true;
                }
            }

            // Ayar paneli etkileşimleri
            if (selected >= 0) {
                int px = cx + 12;
                int py = cy + 52 + NAMES.length * 54 + 4;
                int pw = W - 24;

                // KillAura range [-] [+]
                if (selected == 0 && my >= py + 34 && my <= py + 48) {
                    if (mx >= px + 10 && mx <= px + 22)
                        auraRange = Math.max(1.5f, auraRange - 0.2f);
                    else if (mx >= px + 110 && mx <= px + 122)
                        auraRange = Math.min(6.0f, auraRange + 0.2f);
                    else if (my >= py + 48 && my <= py + 60)
                        elytraTarget = !elytraTarget;
                    return true;
                }
                // Criticals mode toggle
                if (selected == 2 && my >= py + 34 && my <= py + 48) {
                    critMode = 1 - critMode;
                    return true;
                }
                // HitBoxes size [-] [+]
                if (selected == 3 && my >= py + 34 && my <= py + 48) {
                    if (mx >= px + 10 && mx <= px + 22)
                        hitboxSize = Math.max(0.05f, hitboxSize - 0.05f);
                    else if (mx >= px + 110 && mx <= px + 122)
                        hitboxSize = Math.min(1.5f, hitboxSize + 0.05f);
                    return true;
                }
            }

            return super.mouseClicked(mx, my, button);
        }

        // ── Yuvarlak Dikdörtgen (drawContext ile) ───
        private void drawRoundRect(DrawContext ctx,
                                   int x, int y, int w, int h,
                                   int r, int color) {
            // Merkez
            ctx.fill(x + r, y,     x + w - r, y + h,     color);
            ctx.fill(x,     y + r, x + r,     y + h - r, color);
            ctx.fill(x + w - r, y + r, x + w, y + h - r, color);
            // 4 köşe yayı (4x4 px blok yaklaşımı)
            for (int dx = 0; dx < r; dx++) {
                for (int dy = 0; dy < r; dy++) {
                    double dist = Math.sqrt((r - dx - 0.5) * (r - dx - 0.5)
             
