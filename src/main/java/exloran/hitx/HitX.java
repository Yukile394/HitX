package exloran.hitx;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
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
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class HitX implements ClientModInitializer {

    // ─── Toggle State ───────────────────────────────────────────────────────────
    private boolean hudOn  = true;
    private boolean tagOn  = true;
    private boolean espOn  = true;  // YENİ: ESP (oyuncu highlight) toggle
    private boolean rLast  = false;
    private boolean nLast  = false;
    private boolean mLast  = false; // YENİ: M tuşu ESP için

    // ─── Target Lock ────────────────────────────────────────────────────────────
    private PlayerEntity target = null;
    private float        alpha  = 0f;

    // ─── Sabitler ───────────────────────────────────────────────────────────────
    private static final double RANGE   = 7.0;   // Algılama menzili
    private static final double DOT     = 0.96;  // Crosshair dot-product eşiği
    private static final float  FADE    = 0.10f; // HUD fade hızı
    private static final int    BAR_H   = 5;     // Kafadaki bar yüksekliği (px)
    private static final int    MAX_BAR_W = 52;  // Kafadaki bar maks. genişliği

    // ─── Animasyon Zamanlayıcısı ─────────────────────────────────────────────
    private long startTime = System.currentTimeMillis();

    // ════════════════════════════════════════════════════════════════════════════
    @Override
    public void onInitializeClient() {
        AutoConfig.register(HitXConfig.class, GsonConfigSerializer::new);
        startTime = System.currentTimeMillis();

        // ── EKRAN BUTONLARI ───────────────────────────────────────────────────
        ScreenEvents.AFTER_INIT.register((client, screen, W, H) -> {

            // Sandık ekranı
            if (screen instanceof GenericContainerScreen chest) {
                int sx = W / 2 + 92, sy = H / 2 - 86;
                int id = chest.getScreenHandler().syncId;
                btn(screen, "▼ Herşeyi Al",  sx, sy,      87, 20,
                    b -> quickMoveAll(client, chest.getScreenHandler(), id, true));
                btn(screen, "▲ Herşeyi Koy", sx, sy + 22, 87, 20,
                    b -> quickMoveAll(client, chest.getScreenHandler(), id, false));
                btn(screen, "✕ Herşeyi At",  sx, sy + 44, 87, 20,
                    b -> throwAll(client, chest.getScreenHandler(), id, false));
                btn(screen, "🗑 Çöp At",      sx, sy + 66, 87, 20,
                    b -> throwAll(client, chest.getScreenHandler(), id, true));
                btn(screen, "⚔ Sadece Ekip", sx, sy + 88, 87, 20,
                    b -> moveOnlyWeapons(client, chest.getScreenHandler(), id));
            }

            // Envanter ekranı
            if (screen instanceof InventoryScreen inv) {
                int x = W / 2 - 88, y = H / 2 - 83;
                int id = inv.getScreenHandler().syncId;
                btn(screen, "🛡 Zırh Giy",  x - 54, y,      52, 18,
                    b -> quickEquipArmor(client, inv.getScreenHandler(), id));
                btn(screen, "🗑 Temizle",   x - 54, y + 20, 52, 18,
                    b -> { for (int i = 9; i < 45; i++) client.interactionManager.clickSlot(id, i, 1, SlotActionType.THROW, client.player); });
                btn(screen, "⚔ Silahlar",  x - 54, y + 40, 52, 18,
                    b -> moveOnlyWeapons(client, inv.getScreenHandler(), id));
            }
        });

        // ── TICK ──────────────────────────────────────────────────────────────
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;

            // Tuş toggle'ları
            boolean r = key(client, GLFW.GLFW_KEY_R);
            if (r && !rLast) { hudOn = !hudOn; msg(client, hudOn ? "§dHUD §aAçıldı" : "§dHUD §cKapatıldı"); }
            rLast = r;

            boolean n = key(client, GLFW.GLFW_KEY_N);
            if (n && !nLast) { tagOn = !tagOn; msg(client, tagOn ? "§dBar §aAçıldı" : "§dBar §cKapatıldı"); }
            nLast = n;

            boolean m = key(client, GLFW.GLFW_KEY_M);
            if (m && !mLast) { espOn = !espOn; msg(client, espOn ? "§dESP §aAçıldı" : "§dESP §cKapatıldı"); }
            mLast = m;

            // Auto-sprint
            if (client.options.forwardKey.isPressed()
                    && !client.player.horizontalCollision
                    && !client.player.isSneaking()
                    && client.player.getHungerManager().getFoodLevel() > 6)
                client.player.setSprinting(true);

            // Kalıcı Gece Görüşü
            if (!client.player.hasStatusEffect(StatusEffects.NIGHT_VISION))
                client.player.addStatusEffect(
                    new StatusEffectInstance(StatusEffects.NIGHT_VISION, 400, 0, false, false, false));

            // Hedef algılama
            boolean show = false;

            // Önce crosshair hedefine bak
            if (client.crosshairTarget instanceof EntityHitResult e
                    && e.getEntity() instanceof PlayerEntity p && p.isAlive()) {
                target = p; show = true;
            }

            // Sonra FOV içindeki en yakın oyuncuya bak
            if (!show) {
                Vec3d eye  = client.player.getCameraPosVec(1f);
                Vec3d look = client.player.getRotationVec(1f).normalize();
                List<PlayerEntity> near = client.world.getEntitiesByClass(
                    PlayerEntity.class,
                    client.player.getBoundingBox().expand(RANGE),
                    e -> e != client.player && e.isAlive());
                PlayerEntity best = null; double bd = DOT;
                for (PlayerEntity c : near) {
                    double d = look.dotProduct(c.getCameraPosVec(1f).subtract(eye).normalize());
                    if (d > bd) { bd = d; best = c; }
                }
                if (best != null) { target = best; show = true; }
            }

            if (!show) target = null;
            alpha = (show && hudOn)
                ? Math.min(1f, alpha + FADE)
                : Math.max(0f, alpha - FADE);
        });

        // ── HUD RENDER ────────────────────────────────────────────────────────
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.options.hudHidden) return;

            HitXConfig cfg = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();
            int sw = mc.getWindow().getScaledWidth();
            int sh = mc.getWindow().getScaledHeight();
            float delta = tickCounter.getTickDelta(true);
            long t = System.currentTimeMillis() - startTime;

            // ── Renk paleti ──────────────────────────────────────────────────
            // Pembe → Magenta → Beyaz animasyonlu flop
            int cMain = animColor(t, 0,   1.00f);   // Ana renk
            int cSec  = animColor(t, 120, 1.00f);   // İkincil
            int cTri  = animColor(t, 240, 1.00f);   // Üçüncül

            // ── Sol üst köşe bilgi paneli ─────────────────────────────────────
            // Arka plan kutusu
            int panW = 110, panH = 52;
            roundRect(ctx, 2, 2, panW, panH, 0xBB000000);
            // Üst çizgi (animasyonlu)
            ctx.fill(4, 3, 4 + panW - 4, 4, cMain);

            ctx.drawText(mc.textRenderer, "⚡ FPS " + mc.getCurrentFps(), 7, 8,  cMain, true);
            ctx.drawText(mc.textRenderer, "HUD " + status(hudOn) + " [R]",  7, 19, cSec,  true);
            ctx.drawText(mc.textRenderer, "Bar " + status(tagOn) + " [N]",  7, 30, cTri,  true);
            ctx.drawText(mc.textRenderer, "ESP " + status(espOn) + " [M]",  7, 41, cMain, true);

            // ── KAFADAKI BARLAR (tagOn) ──────────────────────────────────────
            if (tagOn && mc.world != null) {
                for (PlayerEntity pl : mc.world.getPlayers()) {
                    if (pl == mc.player || !pl.isAlive()) continue;
                    double dist = mc.player.distanceTo(pl);
                    if (dist > RANGE + 1.5) continue;

                    // Pozisyon interpolasyonu (smooth)
                    double px3 = lerp(pl.lastRenderX, pl.getX(), delta);
                    double py3 = lerp(pl.lastRenderY, pl.getY(), delta);
                    double pz3 = lerp(pl.lastRenderZ, pl.getZ(), delta);

                    // Bar konumu: başın 0.45 blok üstü
                    double[] sc = proj(mc, new Vec3d(px3, py3 + pl.getHeight() + 0.45, pz3), sw, sh);
                    if (sc == null) continue;

                    int bx  = (int) sc[0];
                    int by  = (int) sc[1];

                    // Mesafeye göre dinamik genişlik (uzakta küçül)
                    float distRatio = (float)(1.0 - Math.min(dist / (RANGE + 2), 0.45));
                    int bw = (int)(MAX_BAR_W * distRatio);
                    int bh = Math.max(3, (int)(BAR_H * distRatio));

                    float hp     = pl.getHealth();
                    float maxHp  = pl.getMaxHealth();
                    float ratio  = Math.max(0f, hp / maxHp);
                    int   fill   = Math.max(1, (int)(ratio * bw));

                    // Bar rengi: yüksek HP → animasyonlu, düşük → kırmızı
                    int barColor;
                    if (ratio > 0.5f) {
                        barColor = animColor(t, pl.getId() * 40, 1.0f);
                    } else if (ratio > 0.25f) {
                        barColor = lerpColor(0xFFFF8800, animColor(t, pl.getId() * 40, 1.0f), ratio * 2f);
                    } else {
                        // Düşük canda kırmızı + titreşim efekti
                        float pulse = (float)((Math.sin(t / 150.0) + 1.0) / 2.0) * 0.5f + 0.5f;
                        barColor = argb((int)(255 * pulse), 255, 30, 30);
                    }

                    // Arka plan + çerçeve
                    ctx.fill(bx - bw/2 - 2, by - 2,      bx + bw/2 + 2, by + bh + 2, 0xCC000000); // dış gölge
                    ctx.fill(bx - bw/2 - 1, by - 1,      bx + bw/2 + 1, by + bh + 1, 0xFF1A1A1A); // çerçeve
                    ctx.fill(bx - bw/2,     by,           bx + bw/2,     by + bh,     0xFF0A0A0A); // iç bg
                    // Dolu kısım
                    ctx.fill(bx - bw/2,     by,           bx - bw/2 + fill, by + bh,  barColor);
                    // Üst parlaklık çizgisi
                    ctx.fill(bx - bw/2,     by,           bx - bw/2 + fill, by + 1,   0x55FFFFFF);

                    // İsim (sadece 5 blok içinde)
                    if (dist < RANGE - 0.5) {
                        String name = pl.getName().getString();
                        int nameColor = animColor(t, pl.getId() * 40, 1.0f);
                        // İsim arka planı
                        int nw = mc.textRenderer.getWidth(name);
                        ctx.fill(bx - nw/2 - 2, by - 12, bx + nw/2 + 2, by - 2, 0xAA000000);
                        ctx.drawText(mc.textRenderer, name, bx - nw/2, by - 11, nameColor, true);
                    }

                    // HP sayısı (sağ tarafta, sadece yakındaysa)
                    if (dist < 5.0) {
                        String hpStr = (int)Math.ceil(hp) + "❤";
                        ctx.drawText(mc.textRenderer, hpStr, bx + bw/2 + 4, by, 0xFFFF4444, true);
                    }
                }
            }

            // ── HEDEF HUD (alt kısım, büyük panel) ──────────────────────────
            if (alpha <= 0.01f || !hudOn) return;

            float hp   = target != null ? target.getHealth()    : 0f;
            float mhp  = target != null ? target.getMaxHealth() : 20f;
            float r    = Math.max(0f, hp / mhp);
            int   a    = (int)(alpha * 255);

            HitXConfig.VisualsConfig vis = cfg.visuals;
            float scale = cfg.hudScale / 100f;

            // Panel boyutları
            int panelW = 170, panelH = 54;
            int panelX = (sw * cfg.hudX) / 100 - panelW / 2;
            int panelY = (sh * cfg.hudY) / 100 - panelH / 2;

            ctx.getMatrices().push();
            ctx.getMatrices().translate(panelX + panelW / 2f, panelY + panelH / 2f, 200);
            ctx.getMatrices().scale(scale, scale, 1);
            ctx.getMatrices().translate(-panelW / 2f, -panelH / 2f, 0);

            // Arka plan (rounded emüle)
            int bg = compose(Math.min(a, 220), 0x0D0D0D);
            roundRect(ctx, 0, 0, panelW, panelH, bg);

            // Üst animasyonlu çizgi (tam genişlikte)
            int lineColor = animColorAlpha(t, 0, alpha);
            ctx.fill(6, 0, panelW - 6, 2, lineColor);

            // Sol kenar çizgisi
            ctx.fill(0, 6, 2, panelH - 6, lineColor);

            // Oyuncu kafası (sol)
            if (target != null) {
                try {
                    Identifier skin = mc.getSkinProvider()
                        .getSkinTextures(target.getGameProfile()).texture();
                    int hx = 8, hy = (panelH - 22) / 2;
                    // Çerçeve
                    ctx.fill(hx - 2, hy - 2, hx + 24, hy + 24, compose(Math.min(a, 200), 0x1A1A1A));
                    ctx.fill(hx - 1, hy - 1, hx + 23, hy + 23, compose(Math.min(a, 150), 0x000000));
                    // Skin katmanları
                    ctx.drawTexture(skin, hx, hy, 22, 22, 8,  8, 8, 8, 64, 64); // iç
                    ctx.drawTexture(skin, hx, hy, 22, 22, 40, 8, 8, 8, 64, 64); // dış (şapka vb.)
                    // Parlaklık overlay
                    ctx.fill(hx, hy, hx + 22, hy + 4, compose(30, 0xFFFFFF));
                } catch (Exception ignored) {}
            }

            // "TARGET" etiketi
            ctx.drawText(mc.textRenderer, "◆ TARGET", 36, 5, animColorAlpha(t, 0, alpha), true);

            // İsim
            String name = target != null ? target.getName().getString() : "---";
            ctx.drawText(mc.textRenderer, name, 36, 15, compose(a, 0xFFFFFF), true);

            // HP yazısı (sağ üst)
            String hpStr = (int)Math.ceil(hp) + " / " + (int)mhp + " ❤";
            ctx.drawText(mc.textRenderer, hpStr,
                panelW - mc.textRenderer.getWidth(hpStr) - 5, 5,
                animColorAlpha(t, 0, alpha), true);

            // HP barı (ana)
            int barX = 36, barY = 28, barW = panelW - 44, barH2 = 8;
            int fillW = Math.max(1, (int)(r * barW));

            // Bar bg
            ctx.fill(barX, barY, barX + barW, barY + barH2, compose(Math.min(a, 180), 0x111111));

            // Bar dolgu (renk HP'ye göre)
            int barFill;
            if (r > 0.5f) {
                barFill = animColorAlpha(t, 0, alpha);
            } else if (r > 0.25f) {
                barFill = compose(a, 0xFF8800);
            } else {
                float pulse = (float)((Math.sin(t / 200.0) + 1.0) / 2.0);
                barFill = compose((int)(a * (0.7f + 0.3f * pulse)), 0xFF2222);
            }
            ctx.fill(barX, barY, barX + fillW, barY + barH2, barFill);

            // Bar üst parlaklık
            ctx.fill(barX, barY, barX + fillW, barY + 2, compose(50, 0xFFFFFF));

            // HP yüzde yazısı (bar içinde)
            String pct = (int)(r * 100) + "%";
            ctx.drawText(mc.textRenderer, pct,
                barX + barW/2 - mc.textRenderer.getWidth(pct)/2,
                barY + 1, compose(a, 0xFFFFFF), true);

            // Mesafe göstergesi (sağ alt)
            if (target != null) {
                double dist = MinecraftClient.getInstance().player.distanceTo(target);
                String distStr = String.format("%.1fm", dist);
                ctx.drawText(mc.textRenderer, distStr,
                    panelW - mc.textRenderer.getWidth(distStr) - 5, panelH - 10,
                    compose(a / 2, 0xAAAAAA), true);
            }

            // Alt çizgi
            ctx.fill(6, panelH - 2, panelW - 6, panelH, lineColor);

            ctx.getMatrices().pop();
        });
    }

    // ════════════════════════════════════════════════════════════════════════════
    // YARDIMCI METOTLAR
    // ════════════════════════════════════════════════════════════════════════════

    /** Pembe-Beyaz-Magenta animasyonlu renk (alfa tam) */
    private int animColor(long t, int offset, float alphaMult) {
        double wave = (Math.sin((t + offset) / 350.0) + 1.0) / 2.0;
        int a = (int)(255 * alphaMult);
        int rr = 255;
        int gg = (int)(80  + 175 * wave);   // 80 (magenta) → 255 (beyaz)
        int bb = (int)(180 + 75  * wave);   // 180 (pembe) → 255 (beyaz)
        return argb(a, rr, gg, bb);
    }

    /** Animasyonlu renk, alpha float destekli */
    private int animColorAlpha(long t, int offset, float alphaMult) {
        double wave = (Math.sin((t + offset) / 350.0) + 1.0) / 2.0;
        int a = (int)(255 * alphaMult);
        int rr = 255;
        int gg = (int)(80  + 175 * wave);
        int bb = (int)(180 + 75  * wave);
        return argb(a, rr, gg, bb);
    }

    /** İki ARGB rengi arası lineer interpolasyon */
    private int lerpColor(int a, int b, float t) {
        int aa = (a >> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return argb(
            (int)(aa + (ba - aa) * t),
            (int)(ar + (br - ar) * t),
            (int)(ag + (bg - ag) * t),
            (int)(ab + (bb - ab) * t));
    }

    /** ARGB int oluştur */
    private int argb(int a, int r, int g, int b) {
        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    /** Alpha + RGB hex birleştir */
    private int compose(int a, int rgb) {
        return ((a & 0xFF) << 24) | (rgb & 0x00FFFFFF);
    }

    /** Dikdörtgen çiz (rounded emülasyonu, 4 köşe kesilmiş) */
    private void roundRect(net.minecraft.client.gui.DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x + 2, y,     x + w - 2, y + h,     color);
        ctx.fill(x,     y + 2, x + w,     y + h - 2, color);
    }

    /** 3D → 2D ekran projeksiyonu */
    private double[] proj(MinecraftClient mc, Vec3d world, int sw, int sh) {
        try {
            var cam = mc.gameRenderer.getCamera();
            Vec3d rel = world.subtract(cam.getPos());
            if (mc.player.getRotationVec(1f).dotProduct(rel.normalize()) < 0) return null;

            double yr = Math.toRadians(cam.getYaw());
            double pr = Math.toRadians(cam.getPitch());

            double rx  = rel.x * Math.cos(yr)  - rel.z * Math.sin(yr);
            double ry  = rel.y;
            double rz  = rel.x * Math.sin(yr)  + rel.z * Math.cos(yr);

            double ry2 = ry * Math.cos(pr) - rz * Math.sin(pr);
            double rz2 = ry * Math.sin(pr) + rz * Math.cos(pr);

            if (rz2 <= 0.1) return null;

            double fov = Math.toRadians(mc.options.getFov().getValue());
            d
