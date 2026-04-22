package exloran.hitx;

import exloran.hitx.listener.OverlayReloadListener;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public class HitX implements ClientModInitializer {

    // ── Modül Durumları ──────────────────────────────────────
    public static boolean hitBoxActive      = false;
    public static boolean triggerBotActive  = false;
    public static boolean aimAssistActive   = false;
    public static boolean nightVisionActive = false;

    // ── AimAssist Ayarları ───────────────────────────────────
    public static float   aimAssistRange   = 4.5f;
    public static float   elytraRange      = 6.0f;
    public static float   aimAssistSpeed   = 0.12f;
    public static float   aimFovLimit      = 90.0f;

    // ── Recoil ───────────────────────────────────────────────
    public static boolean recoilActive     = false;
    public static float   recoilStrength   = 0.25f;

    // ── TriggerBot ───────────────────────────────────────────
    public static int     triggerDelay     = 50;

    // ── Keybindlar ───────────────────────────────────────────
    public static int keyHitbox      = GLFW.GLFW_KEY_H;
    public static int keyAimAssist   = GLFW.GLFW_KEY_J;
    public static int keyTriggerBot  = GLFW.GLFW_KEY_K;
    public static int keyNightVision = GLFW.GLFW_KEY_N;

    // ── İç Değişkenler ───────────────────────────────────────
    private boolean mLast            = false;
    private boolean kHitboxLast      = false;
    private boolean kAimAssistLast   = false;
    private boolean kTriggerLast     = false;
    private boolean kNightVisionLast = false;
    private long    lastAttackTime   = 0L;
    private long    nvTick           = 0L;
    private LivingEntity lockedTarget = null;

    @Override
    public void onInitializeClient() {
        AutoConfig.register(HitXConfig.class, GsonConfigSerializer::new);

        ClientTickEvents.END_WORLD_TICK.register(w -> OverlayReloadListener.callEvent());

        ScreenEvents.AFTER_INIT.register((client, screen, W, H) -> {
            if (screen instanceof InventoryScreen inv) {
                int id = inv.getScreenHandler().syncId;
                iconBtn(screen, new ItemStack(Items.DIAMOND_CHESTPLATE), "§bZırhı Giy",
                        W / 2 + 92, H / 2 - 50, 22, 22,
                        b -> {
                            for (int i = 9; i < 45; i++) {
                                if (isArmor(inv.getScreenHandler().getSlot(i).getStack()))
                                    client.interactionManager.clickSlot(id, i, 0, SlotActionType.QUICK_MOVE, client.player);
                            }
                        });
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            long handle = client.getWindow().getHandle();
            HitXConfig cfg = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();

            // M → Menü
            boolean mNow = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_M) == GLFW.GLFW_PRESS;
            if (mNow && !mLast) client.setScreen(new HitXMenu());
            mLast = mNow;

            // Keybindlar
            if (client.currentScreen == null) {
                boolean kH  = GLFW.glfwGetKey(handle, keyHitbox)      == GLFW.GLFW_PRESS;
                boolean kA  = GLFW.glfwGetKey(handle, keyAimAssist)   == GLFW.GLFW_PRESS;
                boolean kT  = GLFW.glfwGetKey(handle, keyTriggerBot)  == GLFW.GLFW_PRESS;
                boolean kNV = GLFW.glfwGetKey(handle, keyNightVision) == GLFW.GLFW_PRESS;

                if (kH && !kHitboxLast) hitBoxActive = !hitBoxActive;

                if (kA && !kAimAssistLast) {
                    aimAssistActive = !aimAssistActive;
                    lockedTarget = null;
                    sendBar(client, aimAssistActive ? "§aAimAssist Aktif" : "§cAimAssist Kapalı");
                }
                if (kT && !kTriggerLast) {
                    triggerBotActive = !triggerBotActive;
                    sendBar(client, triggerBotActive ? "§aTriggerBot Aktif" : "§cTriggerBot Kapalı");
                }
                if (kNV && !kNightVisionLast) {
                    nightVisionActive = !nightVisionActive;
                    if (!nightVisionActive)
                        client.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
                    sendBar(client, nightVisionActive ? "§aGece Görüşü Aktif" : "§cGece Görüşü Kapalı");
                }

                kHitboxLast      = kH;
                kAimAssistLast   = kA;
                kTriggerLast     = kT;
                kNightVisionLast = kNV;
            }

            // HitBoxes
            if (hitBoxActive) {
                for (Entity e : client.world.getEntities()) {
                    if (e instanceof LivingEntity le && le != client.player) {
                        float hw   = (0.6f * cfg.xzExpand) / 2f;
                        float tall = 1.8f * cfg.yExpand;
                        le.setBoundingBox(new Box(
                                le.getX() - hw, le.getY() + cfg.yOffset, le.getZ() - hw,
                                le.getX() + hw, le.getY() + tall + cfg.yOffset, le.getZ() + hw));
                    }
                }
            }

            // Night Vision — her 4 tick'te bir yenile
            if (nightVisionActive && ++nvTick % 4 == 0) {
                client.player.addStatusEffect(
                        new StatusEffectInstance(StatusEffects.NIGHT_VISION, 400, 0, false, false));
            }

            handleCombat(client);
        });
    }

    // ════════════════════════════════════════════════════════
    //  COMBAT
    // ════════════════════════════════════════════════════════
    private void handleCombat(MinecraftClient client) {
        if (!aimAssistActive && !triggerBotActive) { lockedTarget = null; return; }

        // Kilit geçerliliğini kontrol et
        if (lockedTarget != null &&
                (!lockedTarget.isAlive() ||
                 client.player.distanceTo(lockedTarget) > aimAssistRange + 1.5f)) {
            lockedTarget = null;
        }

        // Yeni hedef seç (en yakın + FOV içinde + en iyi açı)
        if (aimAssistActive && lockedTarget == null) {
            float maxDist  = client.player.isFallFlying() ? elytraRange : aimAssistRange;
            double best    = Double.MAX_VALUE;
            for (Entity e : client.world.getEntities()) {
                if (!(e instanceof LivingEntity le) || le == client.player || !le.isAlive()) continue;
                double dist  = client.player.distanceTo(le);
                if (dist > maxDist) continue;
                float  angle = angleTo(client, le);
                if (angle > aimFovLimit) continue;
                double score = angle * 0.5 + dist * 0.5;
                if (score < best) { best = score; lockedTarget = le; }
            }
        }

        // Smooth aim
        if (aimAssistActive && lockedTarget != null)
            smoothAim(client, lockedTarget);

        // TriggerBot hedefi
        LivingEntity trigTarget = null;
        if (triggerBotActive &&
                client.crosshairTarget instanceof net.minecraft.util.hit.EntityHitResult hit &&
                hit.getEntity() instanceof LivingEntity le && le.isAlive()) {
            trigTarget = le;
        }

        LivingEntity attackTarget = trigTarget != null ? trigTarget
                                  : (aimAssistActive ? lockedTarget : null);
        if (attackTarget == null) return;

        if (client.player.getAttackCooldownProgress(0.5f) < 1.0f) return;
        long now = System.currentTimeMillis();
        if (now - lastAttackTime < triggerDelay) return;

        client.interactionManager.attackEntity(client.player, attackTarget);
        client.player.swingHand(Hand.MAIN_HAND);
        lastAttackTime = now;

        if (recoilActive)
            client.player.setPitch(client.player.getPitch() - recoilStrength);
    }

    private float angleTo(MinecraftClient client, LivingEntity target) {
        Vec3d look = client.player.getRotationVec(1f);
        Vec3d toT  = target.getEyePos().subtract(client.player.getEyePos()).normalize();
        double dot = MathHelper.clamp(look.dotProduct(toT), -1.0, 1.0);
        return (float) Math.toDegrees(Math.acos(dot));
    }

    private void smoothAim(MinecraftClient client, LivingEntity target) {
        Vec3d eye = client.player.getEyePos();
        Vec3d tgt = target.getEyePos();
        double dx = tgt.x - eye.x, dy = tgt.y - eye.y, dz = tgt.z - eye.z;
        double hd = Math.sqrt(dx * dx + dz * dz);

        float wYaw   = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float wPitch = (float) Math.toDegrees(-Math.atan2(dy, hd));

        float newYaw   = lerpAngle(client.player.getYaw(),   wYaw,   aimAssistSpeed);
        float newPitch = lerpF    (client.player.getPitch(), wPitch, aimAssistSpeed);

        client.player.setYaw(newYaw);
        client.player.setPitch(MathHelper.clamp(newPitch, -90f, 90f));
    }

    private float lerpF(float a, float b, float t)     { return a + (b - a) * t; }
    private float lerpAngle(float c, float t, float s) { return c + (((t - c + 540f) % 360f) - 180f) * s; }

    private void sendBar(MinecraftClient c, String msg) {
        if (c.player != null)
            c.player.sendMessage(Text.literal("§8[§dHitX§8] §r" + msg), true);
    }

    // ════════════════════════════════════════════════════════
    //  MENÜ
    // ════════════════════════════════════════════════════════
    public class HitXMenu extends Screen {

        private static final String[] TABS = {"Hitboxes","AimAssist","TriggerBot","HitColor","Keybinds"};
        private static final int PW = 430, PH = 310;

        private String openTab    = "AimAssist";
        private int    bindingFor = -1;

        // Drag state
        private int  dragSlot = -1;
        private int  dCX, dCW;

        protected HitXMenu() { super(Text.literal("HitX")); }

        @Override
        public void render(DrawContext ctx, int mx, int my, float delta) {
            HitXConfig cfg = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();
            int ox = ox(), oy = oy();

            // Arka planlar
            f(ctx, ox,       oy,       ox + PW,  oy + PH,  0xF2101010);
            f(ctx, ox,       oy,       ox + PW,  oy + 26,  0xFF190028);
            f(ctx, ox,       oy + 26,  ox + 112, oy + PH,  0xFF141414);

            // Başlık
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "§d§lHITX  §8│  §7Kontrol Paneli", ox + PW / 2, oy + 9, 0xFFFFFF);

            // Sekmeler
            int ty = oy + 32;
            for (String tab : TABS) {
                boolean sel = tab.equals(openTab);
                boolean hov = hov(mx, my, ox + 6, ty, 100, 22);
                f(ctx, ox + 6, ty, ox + 106, ty + 22, sel ? 0xFF5500BB : (hov ? 0xFF252525 : 0xFF1C1C1C));
                if (sel) f(ctx, ox + 6, ty, ox + 8, ty + 22, 0xFFBB00FF);
                ctx.drawTextWithShadow(textRenderer, tab, ox + 13, ty + 7,
                        sel ? 0xFFFF99FF : (hov ? 0xFFDDDDDD : 0xFF777777));
                ty += 26;
            }

            // İçerik paneli
            int cx = cx(), cy = cy(), cw = cw();
            f(ctx, cx - 3, cy - 3, cx + cw + 3, oy + PH - 4, 0xFF1A1A1A);

            switch (openTab) {
                case "Hitboxes"   -> tabHitboxes  (ctx, cfg, cx, cy, cw, mx, my);
                case "AimAssist"  -> tabAimAssist (ctx, cfg, cx, cy, cw, mx, my);
                case "TriggerBot" -> tabTriggerBot(ctx, cfg, cx, cy, cw, mx, my);
                case "HitColor"   -> tabHitColor  (ctx, cfg, cx, cy, cw, mx, my);
                case "Keybinds"   -> tabKeybinds  (ctx,      cx, cy, cw, mx, my);
            }

            super.render(ctx, mx, my, delta);
        }

        // ── Sekmeler ────────────────────────────────────────
        private void tabHitboxes(DrawContext ctx, HitXConfig cfg, int cx, int cy, int cw, int mx, int my) {
            tog(ctx, cx, cy,      cw, "Hitboxes", hitBoxActive, mx, my);
            lbl(ctx, cx, cy + 30, "Genişlik (XZ):  §e" + sf2(cfg.xzExpand));
            sld(ctx, cx, cy + 42, cw, (cfg.xzExpand - 0.5f) / 4.5f, 0);
            lbl(ctx, cx, cy + 60, "Yükseklik (Y):  §e" + sf2(cfg.yExpand));
            sld(ctx, cx, cy + 72, cw, (cfg.yExpand - 0.5f) / 3.5f,  1);
            lbl(ctx, cx, cy + 90, "Y Offset:       §e" + sf2(cfg.yOffset));
            sld(ctx, cx, cy + 102,cw, (cfg.yOffset + 1f) / 2f,       2);
        }

        private void tabAimAssist(DrawContext ctx, HitXConfig cfg, int cx, int cy, int cw, int mx, int my) {
            tog(ctx, cx, cy,       cw, "AimAssist",         aimAssistActive, mx, my);
            tog(ctx, cx, cy + 26,  cw, "Sarsılma (Recoil)", recoilActive,    mx, my);
            lbl(ctx, cx, cy + 56,  "Menzil:            §e" + sf1(aimAssistRange) + " blok");
            sld(ctx, cx, cy + 68,  cw, (aimAssistRange - 1f) / 9f,         10);
            lbl(ctx, cx, cy + 86,  "Hız (Smooth):      §e" + sf2(aimAssistSpeed));
            sld(ctx, cx, cy + 98,  cw, (aimAssistSpeed - 0.01f) / 0.49f,   11);
            lbl(ctx, cx, cy + 116, "FOV Limiti:        §e" + sf1(aimFovLimit) + "°");
            sld(ctx, cx, cy + 128, cw, aimFovLimit / 180f,                 12);
            lbl(ctx, cx, cy + 146, "Sarsılma Şiddeti:  §e" + sf2(recoilStrength));
            sld(ctx, cx, cy + 158, cw, recoilStrength / 2f,                13);
        }

        private void tabTriggerBot(DrawContext ctx, HitXConfig cfg, int cx, int cy, int cw, int mx, int my) {
            tog(ctx, cx, cy,      cw, "TriggerBot", triggerBotActive, mx, my);
            lbl(ctx, cx, cy + 30, "Gecikme:  §e" + triggerDelay + " ms");
            sld(ctx, cx, cy + 42, cw, triggerDelay / 500f, 20);
            lbl(ctx, cx, cy + 60, triggerBotActive ? "§aNişandaki düşmana otomatik vurur" : "§7Kapalı");
        }

        private void tabHitColor(DrawContext ctx, HitXConfig cfg, int cx, int cy, int cw, int mx, int my) {
            tog(ctx, cx, cy, cw, "HitColor", cfg.hitColorActive, mx, my);
            // Önizleme kutusu
            int pc = (cfg.hcAlpha << 24) | (cfg.hcRed << 16) | (cfg.hcGreen << 8) | cfg.hcBlue;
            f(ctx, cx + cw - 28, cy + 3,  cx + cw - 4, cy + 19, 0xFF000000);
            f(ctx, cx + cw - 27, cy + 4,  cx + cw - 5, cy + 18, pc);
            lbl(ctx, cx, cy + 30,  "§cKırmızı:  §e" + cfg.hcRed);
            sld(ctx, cx, cy + 42,  cw, cfg.hcRed   / 255f, 30);
            lbl(ctx, cx, cy + 58,  "§aYeşil:    §e" + cfg.hcGreen);
            sld(ctx, cx, cy + 70,  cw, cfg.hcGreen / 255f, 31);
            lbl(ctx, cx, cy + 86,  "§bMavi:     §e" + cfg.hcBlue);
            sld(ctx, cx, cy + 98,  cw, cfg.hcBlue  / 255f, 32);
            lbl(ctx, cx, cy + 114, "§7Alpha:    §e" + cfg.hcAlpha);
            sld(ctx, cx, cy + 126, cw, cfg.hcAlpha / 255f, 33);
        }

        private void tabKeybinds(DrawContext ctx, int cx, int cy, int cw, int mx, int my) {
            lbl(ctx, cx, cy, "§8Satıra tıkla, ardından yeni tuşa bas");
            kb(ctx, cx, cy + 16,  cw, "Hitboxes",    keyHitbox,      bindingFor == 0, mx, my);
            kb(ctx, cx, cy + 42,  cw, "AimAssist",   keyAimAssist,   bindingFor == 1, mx, my);
            kb(ctx, cx, cy + 68,  cw, "TriggerBot",  keyTriggerBot,  bindingFor == 2, mx, my);
            kb(ctx, cx, cy + 94,  cw, "NightVision", keyNightVision, bindingFor == 3, mx, my);
        }

        // ── Çizim Bileşenleri ────────────────────────────────
        private void f(DrawContext ctx, int x1, int y1, int x2, int y2, int col) {
            ctx.fill(x1, y1, x2, y2, col);
        }

        private void tog(DrawContext ctx, int x, int y, int w, String name, boolean on, int mx, int my) {
            boolean hov = hov(mx, my, x, y, w, 22);
            f(ctx, x, y, x + w, y + 22, on ? (hov ? 0xFF006644 : 0xFF004433) : (hov ? 0xFF2D2D2D : 0xFF222222));
            f(ctx, x, y, x + 3, y + 22, on ? 0xFF00FFAA : 0xFF444444);
            ctx.drawTextWithShadow(textRenderer, name, x + 8, y + 7, on ? 0xFF00FFCC : 0xFF999999);
            ctx.drawTextWithShadow(textRenderer, on ? "§a● AÇIK" : "§c○ KAPALI", x + w - 54, y + 7, 0xFFFFFFFF);
        }

        private void sld(DrawContext ctx, int x, int y, int w, float p, int sid) {
            p = MathHelper.clamp(p, 0f, 1f);
            f(ctx, x,     y,     x + w,     y + 10, 0xFF0C0C0C);
            f(ctx, x + 1, y + 1, x + w - 1, y + 9,  0xFF1C1C1C);
            int fw = (int)((w - 2) * p);
            if (fw > 0) f(ctx, x + 1, y + 1, x + 1 + fw, y + 9, 0xFF7700DD);
            if (fw > 3) f(ctx, x + fw - 1, y + 2, x + 1 + fw, y + 8, 0xFFBB66FF);
            int kx = x + 1 + (int)((w - 6) * p);
            f(ctx, kx, y - 2, kx + 4, y + 12, 0xFFFFFFFF);
        }

        private void lbl(DrawContext ctx, int x, int y, String t) {
            ctx.drawTextWithShadow(textRenderer, t, x, y, 0xFFBBBBBB);
        }

        private void kb(DrawContext ctx, int x, int y, int w, String name, int key, boolean wait, int mx, int my) {
            boolean hov = hov(mx, my, x, y, w, 22);
            f(ctx, x, y, x + w, y + 22, wait ? 0xFF2A0055 : (hov ? 0xFF282828 : 0xFF1E1E1E));
            f(ctx, x, y, x + 3, y + 22, wait ? 0xFFFF00FF : 0xFF6600CC);
            ctx.drawTextWithShadow(textRenderer, name, x + 8, y + 7, 0xFFCCCCCC);
            ctx.drawTextWithShadow(textRenderer, wait ? "§e[ tuşa bas... ]" : "§d[ " + kn(key) + " ]",
                    x + w - 88, y + 7, 0xFFFFFFFF);
        }

        private boolean hov(int mx, int my, int x, int y, int w, int h) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }
        private boolean hovD(double mx, double my, int x, int y, int w, int h) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }

        // ── Koordinatlar ─────────────────────────────────────
        private int ox()  { return width  / 2 - PW / 2; }
        private int oy()  { return height / 2 - PH / 2; }
        private int cx()  { return ox() + 118; }
        private int cy()  { return oy() + 32;  }
        private int cw()  { return PW - 124;   }

        // ── Mouse Tıklama ────────────────────────────────────
        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            HitXConfig cfg = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();
            int ox = ox(), oy = oy(), cx = cx(), cy = cy(), cw = cw();

            // Sekme seçimi
            int ty = oy + 32;
            for (String tab : TABS) {
                if (hovD(mx, my, ox + 6, ty, 100, 22)) {
                    openTab = tab; bindingFor = -1; dragSlot = -1; return true;
                }
                ty += 26;
            }

            switch (openTab) {
                case "Hitboxes" -> {
                    if (hovD(mx, my, cx, cy, cw, 22))       { hitBoxActive = !hitBoxActive; return true; }
                    if (clickSld(mx, my, cx, cy + 42, cw, 0))  { cfg.xzExpand = 0.5f + sv(mx, cx, cw) * 4.5f; sc(); return true; }
                    if (clickSld(mx, my, cx, cy + 72, cw, 1))  { cfg.yExpand  = 0.5f + sv(mx, cx, cw) * 3.5f; sc(); return true; }
                    if (clickSld(mx, my, cx, cy +102, cw, 2))  { cfg.yOffset  = -1f  + sv(mx, cx, cw) * 2f;   sc(); return true; }
                }
                case "AimAssist" -> {
                    if (hovD(mx, my, cx, cy,      cw, 22)) {
                        aimAssistActive = !aimAssistActive; lockedTarget = null;
                        sendBar(client, aimAssistActive ? "§aAimAssist Aktif" : "§cAimAssist Kapalı"); return true;
                    }
                    if (hovD(mx, my, cx, cy + 26, cw, 22)) { recoilActive = !recoilActive; return true; }
                    if (clickSld(mx, my, cx, cy + 68,  cw, 10)) { aimAssistRange = 1f    + sv(mx, cx, cw) * 9f;    return true; }
                    if (clickSld(mx, my, cx, cy + 98,  cw, 11)) { aimAssistSpeed = 0.01f + sv(mx, cx, cw) * 0.49f; return true; }
                    if (clickSld(mx, my, cx, cy + 128, cw, 12)) { aimFovLimit    = sv(mx, cx, cw) * 180f;          return true; }
                    if (clickSld(mx, my, cx, cy + 158, cw, 13)) { recoilStrength = sv(mx, cx, cw) * 2f;            return true; }
                }
                case "TriggerBot" -> {
                    if (hovD(mx, my, cx, cy, cw, 22)) {
                        triggerBotActive = !triggerBotActive;
                        sendBar(client, triggerBotActive ? "§aTriggerBot Aktif" : "§cTriggerBot Kapalı"); return true;
                    }
                    if (clickSld(mx, my, cx, cy + 42, cw, 20)) { triggerDelay = (int)(sv(mx, cx, cw) * 500); return true; }
                }
                case "HitColor" -> {
                    if (hovD(mx, my, cx, cy, cw, 22)) { cfg.hitColorActive = !cfg.hitColorActive; sc(); OverlayReloadListener.callEvent(); return true; }
                    if (clickSld(mx, my, cx, cy + 42,  cw, 30)) { cfg.hcRed   = (int)(sv(mx, cx, cw)*255); sc(); OverlayReloadListener.callEvent(); return true; }
                    if (clickSld(mx, my, cx, cy + 70,  cw, 31)) { cfg.hcGreen = (int)(sv(mx, cx, cw)*255); sc(); OverlayReloadListener.callEvent(); return true; }
                    if (clickSld(mx, my, cx, cy + 98,  cw, 32)) { cfg.hcBlue  = (int)(sv(mx, cx, cw)*255); sc(); OverlayReloadListener.callEvent(); return true; }
                    if (clickSld(mx, my, cx, cy + 126, cw, 33)) { cfg.hcAlpha = (int)(sv(mx, cx, cw)*255); sc(); OverlayReloadListener.callEvent(); return true; }
                }
                case "Keybinds" -> {
                    if (hovD(mx, my, cx, cy + 16, cw, 22)) { bindingFor = 0; return true; }
                    if (hovD(mx, my, cx, cy + 42, cw, 22)) { bindingFor = 1; return true; }
                    if (hovD(mx, my, cx, cy + 68, cw, 22)) { bindingFor = 2; return true; }
                    if (hovD(mx, my, cx, cy + 94, cw, 22)) { bindingFor = 3; return true; }
                }
            }
            return super.mouseClicked(mx, my, btn);
        }

        // ── Sürükleme (akıcı slider) ─────────────────────────
        @Override
        public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
            if (dragSlot == -1) return super.mouseDragged(mx, my, btn, dx, dy);
            HitXConfig cfg = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();
            float v = sv(mx, dCX, dCW);
            switch (dragSlot) {
                case 0  -> { cfg.xzExpand      = 0.5f + v * 4.5f;    sc(); }
                case 1  -> { cfg.yExpand        = 0.5f + v * 3.5f;    sc(); }
                case 2  -> { cfg.yOffset        = -1f  + v * 2f;      sc(); }
                case 10 -> aimAssistRange       = 1f   + v * 9f;
                case 11 -> aimAssistSpeed       = 0.01f+ v * 0.49f;
                case 12 -> aimFovLimit          = v    * 180f;
                case 13 -> recoilStrength       = v    * 2f;
                case 20 -> triggerDelay         = (int)(v * 500);
                case 30 -> { cfg.hcRed   = (int)(v * 255); sc(); OverlayReloadListener.callEvent(); }
                case 31 -> { cfg.hcGreen = (int)(v * 255); sc(); OverlayReloadListener.callEvent(); }
                case 32 -> { cfg.hcBlue  = (int)(v * 255); sc(); OverlayReloadListener.callEvent(); }
                case 33 -> { cfg.hcAlpha = (int)(v * 255); sc(); OverlayReloadListener.callEvent(); }
            }
            return true;
        }

        @Override
        public boolean mouseReleased(double mx, double my, int btn) {
            dragSlot = -1;
            return super.mouseReleased(mx, my, btn);
        }

        @Override
        public boolean keyPressed(int k, int scan, int mod) {
            if (bindingFor == 0) { keyHitbox      = k; bindingFor = -1; return true; }
            if (bindingFor == 1) { keyAimAssist   = k; bindingFor = -1; return true; }
            if (bindingFor == 2) { keyTriggerBot  = k; bindingFor = -1; return true; }
            if (bindingFor == 3) { keyNightVision = k; bindingFor = -1; return true; }
            return super.keyPressed(k, scan, mod);
        }

        @Override public boolean shouldPause() { return false; }

        // ── Slider Yardımcıları ──────────────────────────────
        private boolean clickSld(double mx, double my, int cx, int sy, int cw, int sid) {
            if (hovD(mx, my, cx, sy - 3, cw, 16)) {
                dragSlot = sid; dCX = cx; dCW = cw; return true;
            }
            return false;
        }
        private float sv(double mx, int cx, int cw) {
            return MathHelper.clamp((float)((mx - cx) / cw), 0f, 1f);
        }
        private void sc() { AutoConfig.getConfigHolder(HitXConfig.class).save(); }
        private String kn(int key) {
            String n = GLFW.glfwGetKeyName(key, 0);
            return n != null ? n.toUpperCase() : "KEY_" + key;
        }
        private String sf1(float v) { return String.format("%.1f", v); }
        private String sf2(float v) { return String.format("%.2f", v); }
    }

    // ── Yardımcılar ───────────────────────────────────────────
    private void iconBtn(Screen s, ItemStack i, String t, int x, int y, int w, int h, ButtonWidget.PressAction p) {}
    private boolean isArmor(ItemStack s) { return s.getItem() instanceof net.minecraft.item.ArmorItem; }
}
