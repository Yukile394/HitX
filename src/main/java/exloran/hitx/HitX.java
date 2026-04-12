package exloran.hitx;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class HitX implements ClientModInitializer {

    // ── Modül Durumları ──────────────────────────────────────────────────────
    public static boolean auraActive      = false;
    public static boolean criticalsActive = false;
    public static boolean hitBoxActive    = false;
    public static boolean antiKBActive    = false;
    public static boolean speedActive     = false;
    public static boolean elytraTarget    = true;

    // ── Ayarlar ──────────────────────────────────────────────────────────────
    public static float auraRange    = 3.8f;
    public static float hitboxSize   = 0.6f;
    public static float attackDelay  = 0.5f;

    // ── İç Değişkenler ───────────────────────────────────────────────────────
    private boolean      menuKeyLast  = false;
    private LivingEntity auraTarget   = null;
    private int          criticalTick = 0;
    private int          antiKBTick   = 0;

    // ── Texture (sadece GUI_BG) ───────────────────────────────────────────────
    private static final Identifier GUI_BG =
            Identifier.of("hitx", "textures/gui/gui_bg.png");

    // ─────────────────────────────────────────────────────────────────────────
    @Override
    public void onInitializeClient() {

        // ── WORLD RENDER ────────────────────────────────────────────────────
        WorldRenderEvents.LAST.register(context -> {
            // Gelecekte render efektleri buraya eklenebilir
        });

        // ── CLIENT TICK ─────────────────────────────────────────────────────
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;

            // Menü: M tuşu
            boolean menuKey = GLFW.glfwGetKey(
                    client.getWindow().getHandle(), GLFW.GLFW_KEY_M)
                    == GLFW.GLFW_PRESS;
            if (menuKey && !menuKeyLast) {
                client.setScreen(new ModernGui());
            }
            menuKeyLast = menuKey;

            // ── CRITICALS ────────────────────────────────────────────────────
            // Her vuruşta yerden küçük paket zıplaması → garanti kritik
            if (criticalsActive && client.player.isOnGround()) {
                criticalTick++;
                if (criticalTick % 2 == 0) {
                    client.player.jump(); // micro-jump
                }
            }

            // ── ANTI-KNOCKBACK ───────────────────────────────────────────────
            // Gelen itmeyi velocity sıfırlayarak baskıla
            if (antiKBActive) {
                antiKBTick++;
                if (antiKBTick % 4 == 0) {
                    client.player.setVelocity(
                            client.player.getVelocity().multiply(0.0, 1.0, 0.0));
                }
            }

            // ── GELIŞMIŞ KILLAURA + ELYTRA TARGET ───────────────────────────
            if (auraActive) {
                double range = (client.player.isFallFlying() && elytraTarget)
                        ? 6.0 : auraRange;

                auraTarget = null;
                LivingEntity bestTarget = null;
                double closestDist = Double.MAX_VALUE;

                // En yakın geçerli hedefi seç
                for (Entity e : client.world.getEntities()) {
                    if (!(e instanceof LivingEntity le)) continue;
                    if (le == client.player)              continue;
                    if (!le.isAlive())                    continue;

                    double dist = client.player.distanceTo(le);
                    if (dist <= range && client.player.canSee(le)) {
                        if (dist < closestDist) {
                            closestDist = dist;
                            bestTarget  = le;
                        }
                    }
                }

                if (bestTarget != null) {
                    auraTarget = bestTarget;

                    // Anti-cheat dostu saldırı hızı kontrolü
                    float cooldown = client.player.getAttackCooldownProgress(attackDelay);
                    if (cooldown >= 1.0f) {
                        client.interactionManager.attackEntity(client.player, bestTarget);
                        client.player.swingHand(Hand.MAIN_HAND);
                    }
                }
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  MODERN GUI
    // ─────────────────────────────────────────────────────────────────────────
    public class ModernGui extends Screen {

        // Kart boyutları (görsele birebir uygun)
        private static final int CARD_W = 300;
        private static final int CARD_H = 380;

        // Modül satır ayarları
        private static final int ROW_H    = 42;
        private static final int ROW_PADX = 18;
        private static final int ROW_PADY = 56; // ilk satır Y offseti

        // Seçili modül (ayar paneli için)
        private int selectedModule = -1;

        // Modül isimleri ve durumları (dizi → kolay genişleme)
        private final String[] moduleNames = {
                "KillAura", "Criticals", "HitBoxes", "Anti-KB", "Speed"
        };

        protected ModernGui() {
            super(Text.literal("HitX"));
        }

        // ── Yardımcı: modül aktif mi? ────────────────────────────────────────
        private boolean isActive(int id) {
            return switch (id) {
                case 0 -> auraActive;
                case 1 -> criticalsActive;
                case 2 -> hitBoxActive;
                case 3 -> antiKBActive;
                case 4 -> speedActive;
                default -> false;
            };
        }

        private void toggle(int id) {
            switch (id) {
                case 0 -> auraActive      = !auraActive;
                case 1 -> criticalsActive = !criticalsActive;
                case 2 -> hitBoxActive    = !hitBoxActive;
                case 3 -> antiKBActive    = !antiKBActive;
                case 4 -> speedActive     = !speedActive;
            }
        }

        // ── RENDER ───────────────────────────────────────────────────────────
        @Override
        public void render(DrawContext ctx, int mx, int my, float delta) {
            int cx = (width  - CARD_W) / 2;
            int cy = (height - CARD_H) / 2;

            // 1. Arka plan dokusu (tam kart boyutu)
            ctx.drawTexture(GUI_BG, cx, cy, 0, 0, CARD_W, CARD_H, CARD_W, CARD_H);

            // 2. Başlık — görseldeki "⚔ Combat" yazısına uygun
            int titleColor = 0xFFD580FF; // lila/pembe
            ctx.drawCenteredTextWithShadow(
                    textRenderer,
                    "⚔  Combat",
                    cx + CARD_W / 2,
                    cy + 16,
                    titleColor
            );

            // 3. Her modül satırını çiz
            for (int i = 0; i < moduleNames.length; i++) {
                drawModuleRow(ctx, cx, cy, i, mx, my);
            }

            // 4. Seçili modülün sağ/alt ayar paneli
            if (selectedModule >= 0) {
                drawSettingsPanel(ctx, cx, cy);
            }

            super.render(ctx, mx, my, delta);
        }

        // ── Modül Satırı ─────────────────────────────────────────────────────
        private void drawModuleRow(DrawContext ctx, int cx, int cy,
                                   int idx, int mx, int my) {
            int rx = cx + ROW_PADX;
            int ry = cy + ROW_PADY + idx * ROW_H;
            int rw = CARD_W - ROW_PADX * 2;
            int rh = ROW_H - 6;

            boolean active  = isActive(idx);
            boolean hovered = mx >= rx && mx <= rx + rw
                           && my >= ry && my <= ry + rh;
            boolean selected = selectedModule == idx;

            // Satır dolgu rengi
            int bgColor;
            if (selected)     bgColor = 0xCC1A0030;   // seçili: koyu mor
            else if (hovered) bgColor = 0xAA1C1C2E;   // hover: biraz açık
            else              bgColor = 0x99101020;    // normal: koyu

            // Yuvarlak köşe efekti (3 farklılı iç içe fill)
            ctx.fill(rx + 2, ry,     rx + rw - 2, ry + rh,     bgColor);
            ctx.fill(rx,     ry + 2, rx + rw,     ry + rh - 2, bgColor);

            // Aktifken sol kenar çizgisi (yeşil)
            if (active) {
                ctx.fill(rx, ry + 2, rx + 3, ry + rh - 2, 0xFF44FF88);
            }

            // Modül adı
            int textColor = active ? 0xFFEEEEEE : 0xFF888899;
            ctx.drawTextWithShadow(
                    textRenderer,
                    moduleNames[idx],
                    rx + 14,
                    ry + rh / 2 - 4,
                    textColor
            );

            // Toggle butonu (sağ taraf)
            drawToggle(ctx, rx + rw - 36, ry + rh / 2 - 6, active);

            // Aktif ise küçük "ON" rozeti
            if (active) {
                int bx = rx + rw - 76;
                int by = ry + rh / 2 - 5;
                ctx.fill(bx, by, bx + 24, by + 11, 0xAA003322);
                ctx.drawText(textRenderer, "ON", bx + 4, by + 2, 0xFF44FF88, false);
            }
        }

        // ── Toggle Switch ────────────────────────────────────────────────────
        private void drawToggle(DrawContext ctx, int x, int y, boolean on) {
            int trackColor = on ? 0xAA6600CC : 0xAA333344;
            int knobColor  = on ? 0xFFCC66FF : 0xFF666677;

            // İz (track)
            ctx.fill(x,      y + 2, x + 28, y + 10, trackColor);
            ctx.fill(x + 1,  y + 1, x + 27, y + 11, 0x00000000); // şeffaf kenar

            // Top (knob)
            int kx = on ? x + 16 : x + 2;
            ctx.fill(kx,     y,     kx + 10, y + 12, knobColor);
            ctx.fill(kx + 1, y + 1, kx + 9,  y + 11, on ? 0xFFDD88FF : 0xFF888899);
        }

        // ── Ayar Paneli ──────────────────────────────────────────────────────
        private void drawSettingsPanel(DrawContext ctx, int cx, int cy) {
            if (selectedModule < 0) return;

            int px = cx + ROW_PADX;
            int py = cy + ROW_PADY + moduleNames.length * ROW_H + 6;
            int pw = CARD_W - ROW_PADX * 2;
            int ph = 70;

            // Panel arka planı
            ctx.fill(px, py, px + pw, py + ph, 0xCC0D0020);
            ctx.fill(px, py, px + pw, py + 1,  0x88AA44FF); // üst çizgi

            ctx.drawTextWithShadow(
                    textRenderer,
                    "§d⚙  " + moduleNames[selectedModule] + " §7Settings",
                    px + 8, py + 8, 0xFFDDDDDD
            );

            // KillAura'ya özel ayar: Range
            if (selectedModule == 0) {
                ctx.drawText(textRenderer,
                        "Range: §a" + String.format("%.1f", auraRange) + " §7blocks",
                        px + 8, py + 26, 0xFFBBBBBB, false);
                ctx.drawText(textRenderer,
                        "ElytraTarget: " + (elytraTarget ? "§aON" : "§cOFF"),
                        px + 8, py + 42, 0xFFBBBBBB, false);
                ctx.drawText(textRenderer,
                        "[-]  [+]   Click to toggle Elytra",
                        px + 8, py + 54, 0xFF666688, false);
            }
            // Hitbox ayarı
            else if (selectedModule == 2) {
                ctx.drawText(textRenderer,
                        "HitboxSize: §a" + String.format("%.2f", hitboxSize),
                        px + 8, py + 26, 0xFFBBBBBB, false);
                ctx.drawText(textRenderer,
                        "[-]  [+]   to adjust size",
                        px + 8, py + 42, 0xFF666688, false);
            }
            else {
                ctx.drawText(textRenderer,
                        "§7No configurable options.",
                        px + 8, py + 30, 0xFF555566, false);
            }
        }

        // ── MOUSE CLICK ──────────────────────────────────────────────────────
        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            int cx = (width  - CARD_W) / 2;
            int cy = (height - CARD_H) / 2;

            for (int i = 0; i < moduleNames.length; i++) {
                int rx = cx + ROW_PADX;
                int ry = cy + ROW_PADY + i * ROW_H;
                int rw = CARD_W - ROW_PADX * 2;
                int rh = ROW_H - 6;

                if (mx >= rx && mx <= rx + rw && my >= ry && my <= ry + rh) {
                    if (button == 0) {          // Sol tık → toggle
                        toggle(i);
                    } else if (button == 1) {   // Sağ tık → ayar paneli
                        selectedModule = (selectedModule == i) ? -1 : i;
                    }
                    return true;
                }
            }

            // Ayar panelindeki [-] [+] butonları (KillAura range)
            if (selectedModule == 0) {
                int px = cx + ROW_PADX + 8;
                int py = cy + ROW_PADY + moduleNames.length * ROW_H + 6 + 54;
                if (my >= py && my <= py + 12) {
                    if (mx >= px && mx <= px + 14) {
                        auraRange = Math.max(1.0f, auraRange - 0.1f);
                        return true;
                    }
                    if (mx >= px + 19 && mx <= px + 33) {
                        auraRange = Math.min(6.0f, auraRange + 0.1f);
                        return true;
                    }
                    // ElytraTarget toggle
                    if (mx >= px + 38 && mx <= px + 180) {
                        elytraTarget = !elytraTarget;
                        return true;
                    }
                }
            }

            // Hitbox ayarları
            if (selectedModule == 2) {
                int px = cx + ROW_PADX + 8;
                int py = cy + ROW_PADY + moduleNames.length * ROW_H + 6 + 42;
                if (my >= py && my <= py + 12) {
                    if (mx >= px && mx <= px + 14) {
                        hitboxSize = Math.max(0.1f, hitboxSize - 0.05f);
                        return true;
                    }
                    if (mx >= px + 19 && mx <= px + 33) {
                        hitboxSize = Math.min(2.0f, hitboxSize + 0.05f);
                        return true;
                    }
                }
            }

            return super.mouseClicked(mx, my, button);
        }

        // ESC ile kapat
        @Override
        public boolean shouldPause() { return false; }
    }
}
