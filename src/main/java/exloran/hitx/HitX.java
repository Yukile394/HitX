package com.exloran.hitx;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
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
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class HitX implements ClientModInitializer {

    // ─── Durum ────────────────────────────────────────────────────────────────
    private boolean hudOn    = true;
    private boolean tagOn    = true;
    private boolean kbOn     = true;   // YENİ: Özel hotbar aç/kapat [H]

    private PlayerEntity target    = null;
    private float        alpha     = 0f;
    private float        selectItemX = 0f;

    // YENİ: Hedef kilitlenme efekti için ekstra alpha pulse
    private float lockPulse = 0f;
    private boolean lockPulseDir = true;

    // YENİ: Kombo sayacı — her hedef bulunduğunda artar, hedef kaybolunca sıfırlanır
    private int   comboCount     = 0;
    private float comboAlpha     = 0f;
    private long  lastTargetTime = 0;

    // YENİ: Ping göstergesi için geçmiş FPS tamponu
    private final int[]  fpsHistory   = new int[60];
    private int          fpsHistoryIdx = 0;

    // ─── Tuş geçmiş durumları ─────────────────────────────────────────────────
    private boolean rLast = false, nLast = false, pLast = false, hLast = false;

    // ─── Sabitler ─────────────────────────────────────────────────────────────
    private static final double RANGE = 6.5;
    private static final double DOT   = 0.97;
    private static final float  FADE  = 0.12f;

    // ─── Partiküller ──────────────────────────────────────────────────────────
    private final List<TargetParticle> particles = new ArrayList<>();

    // ─── Config cache ─────────────────────────────────────────────────────────
    // Her tick'te AutoConfig.getConfigHolder çağırmak yerine cache'liyoruz.
    private HitXConfig cachedConfig = null;

    // ─── Night Vision refresh eşiği ───────────────────────────────────────────
    // 400 tick = 20 saniye; 100 kala yenilemek daha temiz.
    private static final int NV_REFRESH_THRESHOLD = 100;

    // =========================================================================
    //  INIT
    // =========================================================================
    @Override
    public void onInitializeClient() {
        AutoConfig.register(HitXConfig.class, GsonConfigSerializer::new);

        // ── Envanter & Sandık ekranlarına buton enjeksiyonu ───────────────────
        ScreenEvents.AFTER_INIT.register((client, screen, W, H) -> {

            if (screen instanceof GenericContainerScreen chest) {
                int sx = W / 2 + 92, sy = H / 2 - 80;
                int id = chest.getScreenHandler().syncId;

                iconBtn(screen, new ItemStack(Items.HOPPER),     "Herşeyi Al",  sx, sy,      24, 20,
                    b -> { int s = chest.getScreenHandler().getInventory().size(); for (int i = 0; i < s; i++) client.interactionManager.clickSlot(id, i, 0, SlotActionType.QUICK_MOVE, client.player); });

                iconBtn(screen, new ItemStack(Items.CHEST),      "Herşeyi Koy", sx, sy + 24, 24, 20,
                    b -> { int s = chest.getScreenHandler().getInventory().size(); for (int i = s; i < s + 36; i++) client.interactionManager.clickSlot(id, i, 0, SlotActionType.QUICK_MOVE, client.player); });

                iconBtn(screen, new ItemStack(Items.DROPPER),    "Herşeyi At",  sx, sy + 48, 24, 20,
                    b -> { for (int i = 0; i < chest.getScreenHandler().slots.size(); i++) client.interactionManager.clickSlot(id, i, 1, SlotActionType.THROW, client.player); });

                iconBtn(screen, new ItemStack(Items.LAVA_BUCKET),"Çöpleri At",  sx, sy + 72, 24, 20,
                    b -> { for (int i = 0; i < chest.getScreenHandler().slots.size(); i++) { ItemStack st = chest.getScreenHandler().getSlot(i).getStack(); if (isTrash(st)) client.interactionManager.clickSlot(id, i, 1, SlotActionType.THROW, client.player); } });

                // YENİ: Sadece araç almak için "Araçları Al" butonu
                iconBtn(screen, new ItemStack(Items.IRON_PICKAXE),"Araçları Al", sx, sy + 96, 24, 20,
                    b -> { for (int i = 0; i < chest.getScreenHandler().slots.size(); i++) { ItemStack st = chest.getScreenHandler().getSlot(i).getStack(); if (isTool(st)) client.interactionManager.clickSlot(id, i, 0, SlotActionType.QUICK_MOVE, client.player); } });
            }

            if (screen instanceof InventoryScreen inv) {
                int x = W / 2 - 25, y = H / 2 - 83;
                int id = inv.getScreenHandler().syncId;

                iconBtn(screen, new ItemStack(Items.DIAMOND_CHESTPLATE), "Zırhı Giy", x,      y, 24, 20,
                    b -> { for (int i = 9; i < 45; i++) { ItemStack st = inv.getScreenHandler().getSlot(i).getStack(); if (isArmor(st)) client.interactionManager.clickSlot(id, i, 0, SlotActionType.QUICK_MOVE, client.player); } });

                iconBtn(screen, new ItemStack(Items.SPONGE),             "Temizle",   x + 28, y, 24, 20,
                    b -> { for (int i = 9; i < 45; i++) client.interactionManager.clickSlot(id, i, 1, SlotActionType.THROW, client.player); });

                // YENİ: Yiyecekleri hotbar'a taşı
                iconBtn(screen, new ItemStack(Items.COOKED_BEEF),        "Yiyecek →", x + 56, y, 24, 20,
                    b -> { for (int i = 9; i < 45; i++) { ItemStack st = inv.getScreenHandler().getSlot(i).getStack(); if (isFood(st)) client.interactionManager.clickSlot(id, i, 0, SlotActionType.QUICK_MOVE, client.player); } });
            }
        });

        // ── Oyun ticki ────────────────────────────────────────────────────────
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;

            // Config cache — her tick yenilenmez, sadece değişince
            if (cachedConfig == null) {
                cachedConfig = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();
            }

            handleKeybinds(client);
            handleAutoSprint(client);
            handleNightVision(client);
            handleTargetTracking(client);
            handleParticles(client);
            updateFpsHistory(client);
        });

        // ── HUD render ────────────────────────────────────────────────────────
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.options.hudHidden) return;

            int sw    = mc.getWindow().getScaledWidth();
            int sh    = mc.getWindow().getScaledHeight();
            float delta = tickCounter.getTickDelta(true);

            // Flop renkleri hesapla (sadece bir kez)
            int flop0   = getPinkWhiteFlop(0,   1.0f);
            int flop100 = getPinkWhiteFlop(100, 1.0f);
            int flop200 = getPinkWhiteFlop(200, 1.0f);

            renderInfoOverlay(ctx, mc, flop0, flop100, flop200);
            if (kbOn) renderPadejHotbar(ctx, mc, sw, sh, delta, flop0);
            if (tagOn && mc.world != null) renderHealthTags(ctx, mc, sw, sh, delta);
            renderTargetHud(ctx, mc, sw, sh, flop0);
            renderComboIndicator(ctx, mc, sw, sh, delta);
        });
    }

    // =========================================================================
    //  TICK HELPERS
    // =========================================================================

    private void handleKeybinds(MinecraftClient client) {
        long win = client.getWindow().getHandle();

        boolean r = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;
        if (r && !rLast) {
            hudOn = !hudOn;
            client.player.sendMessage(Text.literal(hudOn ? "§dHUD §fAçıldı" : "§7HUD §fKapatıldı"), true);
        }
        rLast = r;

        boolean n = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_N) == GLFW.GLFW_PRESS;
        if (n && !nLast) {
            tagOn = !tagOn;
            client.player.sendMessage(Text.literal(tagOn ? "§dHP Bar §fAçıldı" : "§7HP Bar §fKapatıldı"), true);
        }
        nLast = n;

        boolean p = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_P) == GLFW.GLFW_PRESS;
        if (p && !pLast) {
            cachedConfig.particleOn = !cachedConfig.particleOn;
            client.player.sendMessage(Text.literal(cachedConfig.particleOn ? "§dPartiküller §fAçıldı" : "§7Partiküller §fKapatıldı"), true);
        }
        pLast = p;

        // YENİ: [H] tuşu ile hotbar aç/kapat
        boolean h = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_H) == GLFW.GLFW_PRESS;
        if (h && !hLast) {
            kbOn = !kbOn;
            client.player.sendMessage(Text.literal(kbOn ? "§dHotbar §fAçıldı" : "§7Hotbar §fKapatıldı"), true);
        }
        hLast = h;
    }

    private void handleAutoSprint(MinecraftClient client) {
        if (client.options.forwardKey.isPressed()
                && !client.player.horizontalCollision
                && !client.player.isSneaking()
                && client.player.getHungerManager().getFoodLevel() > 6) {
            client.player.setSprinting(true);
        }
    }

    private void handleNightVision(MinecraftClient client) {
        StatusEffectInstance nv = client.player.getStatusEffect(StatusEffects.NIGHT_VISION);
        // Sadece süresi dolmak üzereyse yenile, yoksa ekle — gereksiz yenilemeyi engeller
        if (nv == null || nv.getDuration() < NV_REFRESH_THRESHOLD) {
            client.player.addStatusEffect(
                new StatusEffectInstance(StatusEffects.NIGHT_VISION, 800, 0, false, false, false)
            );
        }
    }

    private void handleTargetTracking(MinecraftClient client) {
        boolean show = false;
        PlayerEntity found = null;

        // Önce crosshair hedefi dene
        if (client.crosshairTarget instanceof EntityHitResult e
                && e.getEntity() instanceof PlayerEntity pl
                && pl.isAlive()) {
            found = pl;
            show  = true;
        }

        // Yoksa dot-product ile en yakın oyuncuyu bul
        if (!show) {
            Vec3d eye  = client.player.getCameraPosVec(1f);
            Vec3d look = client.player.getRotationVec(1f).normalize();
            List<PlayerEntity> near = client.world.getEntitiesByClass(
                PlayerEntity.class,
                client.player.getBoundingBox().expand(RANGE),
                ent -> ent != client.player && ent.isAlive()
            );
            double bestDot = DOT;
            for (PlayerEntity c : near) {
                double d = look.dotProduct(c.getCameraPosVec(1f).subtract(eye).normalize());
                if (d > bestDot) { bestDot = d; found = c; }
            }
            if (found != null) show = true;
        }

        // Hedef değişince kombo arttır
        if (show && found != target) {
            comboCount++;
            comboAlpha = 1.5f; // fade-in için
        }
        if (!show) {
            // Hedef kaybolunca 3 saniye bekle, sonra sıfırla
            if (target != null) lastTargetTime = System.currentTimeMillis();
            if (System.currentTimeMillis() - lastTargetTime > 3000) comboCount = 0;
        }

        target = show ? found : null;
        alpha  = show && hudOn
            ? Math.min(1f, alpha + FADE)
            : Math.max(0f, alpha - FADE);

        // Lock-pulse animasyonu
        if (show && alpha > 0.9f) {
            lockPulse += lockPulseDir ? 0.05f : -0.05f;
            if (lockPulse >= 1f) { lockPulse = 1f; lockPulseDir = false; }
            if (lockPulse <= 0f) { lockPulse = 0f; lockPulseDir = true;  }
        }
    }

    private void handleParticles(MinecraftClient client) {
        if (cachedConfig.particleOn && hudOn && target != null && alpha > 0.1f) {
            if (client.world.random.nextFloat() < 0.35f) {
                // YENİ: partiküller HUD sınırları içinde başlasın (merkezden dışa fırlasın)
                float px = 77.5f + (client.world.random.nextFloat() - 0.5f) * 130f;
                float py = 23f   + (client.world.random.nextFloat() - 0.5f) * 40f;
                particles.add(new TargetParticle(px, py));
            }
        }
        // ConcurrentModificationException'dan kaçın: iterator ile sil
        Iterator<TargetParticle> it = particles.iterator();
        while (it.hasNext()) { if (it.next().update()) it.remove(); }
    }

    private void updateFpsHistory(MinecraftClient client) {
        fpsHistory[fpsHistoryIdx % 60] = client.getCurrentFps();
        fpsHistoryIdx++;
    }

    // =========================================================================
    //  RENDER HELPERS
    // =========================================================================

    /** Sol üst bilgi katmanı: FPS, ortalama FPS, tuş durumları */
    private void renderInfoOverlay(DrawContext ctx, MinecraftClient mc, int c0, int c1, int c2) {
        int fps = mc.getCurrentFps();
        int avgFps = 0;
        for (int v : fpsHistory) avgFps += v;
        avgFps /= 60;

        // FPS rengi: yüksekse yeşilimsi, düşükse kırmızımsı flop
        String fpsLabel = fps < 30 ? "§c" : fps < 60 ? "§e" : "§a";
        ctx.drawText(mc.textRenderer, fpsLabel + "FPS " + fps + " §7(" + avgFps + " avg)", 5, 5,  0xFFFFFFFF, true);
        ctx.drawText(mc.textRenderer, "§d[R] §fHUD " + (hudOn          ? "§aON" : "§7OFF"),  5, 14, 0xFFFFFFFF, true);
        ctx.drawText(mc.textRenderer, "§d[P] §fPRT " + (cachedConfig != null && cachedConfig.particleOn ? "§aON" : "§7OFF"), 5, 23, 0xFFFFFFFF, true);
        ctx.drawText(mc.textRenderer, "§d[H] §fKB  " + (kbOn           ? "§aON" : "§7OFF"),  5, 32, 0xFFFFFFFF, true);
        ctx.drawText(mc.textRenderer, "§d[N] §fBAR " + (tagOn           ? "§aON" : "§7OFF"), 5, 41, 0xFFFFFFFF, true);

        // YENİ: Sağ üstte koordinatlar
        if (MinecraftClient.getInstance().player != null) {
            var pos = MinecraftClient.getInstance().player.getBlockPos();
            String coords = "§7XYZ §f" + pos.getX() + " §7/ §f" + pos.getY() + " §7/ §f" + pos.getZ();
            int tw = MinecraftClient.getInstance().textRenderer.getWidth(coords);
            ctx.drawText(mc.textRenderer, coords, MinecraftClient.getInstance().getWindow().getScaledWidth() - tw - 5, 5, 0xFFFFFFFF, true);
        }
    }

    /** Özel pembe-beyaz animasyonlu hotbar */
    private void renderPadejHotbar(DrawContext ctx, MinecraftClient mc, int sw, int sh, float delta, int flop) {
        PlayerInventory inv = mc.player.getInventory();
        int w = 182, h = 22;
        int x = (sw - w) / 2;
        int y = sh - 25;

        // Lerp hızını delta'ya bağlı yap — frame rate bağımsız
        float lerpSpeed = 1f - (float) Math.pow(0.1, delta);
        selectItemX = lerp(selectItemX, inv.selectedSlot * 20f, lerpSpeed);

        // Dış çerçeve (hafif şeffaf)
        ctx.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0x88000000);

        // İç dolgu
        ctx.fill(x, y, x + w, y + h, 0x55111111);

        // Seçili slot highlight
        int sx = (int)(x + selectItemX);
        ctx.fill(sx, y, sx + 22, y + 22, applyAlpha(flop, 80));  // dolgu
        ctx.fill(sx,      y,      sx + 22, y + 1,      flop);     // üst kenar
        ctx.fill(sx,      y + 21, sx + 22, y + 22,     flop);     // alt kenar
        ctx.fill(sx,      y,      sx + 1,  y + 22,     flop);     // sol kenar
        ctx.fill(sx + 21, y,      sx + 22, y + 22,     flop);     // sağ kenar

        // YENİ: Tüm slotlar için hafif bölücü çizgiler
        for (int i = 1; i < 9; i++) {
            int lx = x + i * 20;
            ctx.fill(lx, y + 2, lx + 1, y + h - 2, 0x22FFFFFF);
        }

        // Eşyaları çiz
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.main.get(i);
            int ix = x + i * 20 + 3;
            int iy = y + 3;
            ctx.drawItem(s, ix, iy);
            ctx.drawItemInSlot(mc.textRenderer, s, ix, iy);
        }

        // YENİ: Mevcut eşya adını hotbar'ın üstünde göster
        ItemStack held = inv.main.get(inv.selectedSlot);
        if (!held.isEmpty()) {
            String name = held.getName().getString();
            int nameW = mc.textRenderer.getWidth(name);
            int nameX = (sw - nameW) / 2;
            // Soluk beyaz arka plan gölgesi
            ctx.fill(nameX - 2, y - 14, nameX + nameW + 2, y - 3, 0x55000000);
            ctx.drawText(mc.textRenderer, name, nameX, y - 12, flop, true);
        }
    }

    /** Oyunculara oyun dünyasında HP barı yansıt */
    private void renderHealthTags(DrawContext ctx, MinecraftClient mc, int sw, int sh, float delta) {
        for (PlayerEntity pl : mc.world.getPlayers()) {
            if (pl == mc.player || !pl.isAlive()) continue;
            double dist = mc.player.distanceTo(pl);
            if (dist > RANGE + 1) continue;

            double wx = lerp(pl.lastRenderX, pl.getX(), delta);
            double wy = lerp(pl.lastRenderY, pl.getY(), delta);
            double wz = lerp(pl.lastRenderZ, pl.getZ(), delta);
            double[] sc = proj(mc, new Vec3d(wx, wy + pl.getHeight() + 0.3, wz), sw, sh);
            if (sc == null) continue;

            int bx = (int) sc[0] - 20;
            int py = (int) sc[1];
            int bw = 40;
            float r = pl.getHealth() / pl.getMaxHealth();
            long now = System.currentTimeMillis();

            // HP bar arka planı + dolgu
            ctx.fill(bx - 1, py - 1, bx + bw + 1, py + 4, 0xAA000000);
            ctx.fill(bx, py, bx + (int)(r * bw), py + 3, getHealthColor(r, now, pl.getId()));

            // YENİ: Oyuncu adı ve mesafe
            String label = pl.getName().getString() + " §7" + (int) dist + "m";
            int lw = mc.textRenderer.getWidth(label);
            ctx.drawText(mc.textRenderer, label, (int) sc[0] - lw / 2, py - 10, 0xFFFFFFFF, true);
        }
    }

    /** Ana hedef HUD paneli */
    private void renderTargetHud(DrawContext ctx, MinecraftClient mc, int sw, int sh, int flop) {
        if (alpha <= 0.01f || !hudOn) return;

        int bW = 160, bH = 52;
        int bX = (sw * cachedConfig.hudX) / 100 - bW / 2;
        int bY = (sh * cachedConfig.hudY) / 100 - bH / 2;
        int hpColor = getPinkWhiteFlop(0, alpha);
        float scale = cachedConfig.hudScale / 100f;

        ctx.getMatrices().push();
        ctx.getMatrices().translate(bX + bW / 2f, bY + bH / 2f, 0);
        ctx.getMatrices().scale(scale, scale, 1);
        ctx.getMatrices().translate(-bW / 2f, -bH / 2f, 0);

        // Arka plan
        int bgAlpha = (int)(alpha * 190);
        ctx.fill(0, 0, bW, bH, (bgAlpha << 24) | 0x050505);

        // Üst kenarlık
        ctx.fill(0, 0, bW, 1, hpColor);
        // Alt kenarlık (daha soluk)
        ctx.fill(0, bH - 1, bW, bH, applyAlpha(hpColor, (int)(alpha * 80)));
        // Sol k
