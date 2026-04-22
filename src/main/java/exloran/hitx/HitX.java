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
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import org.lwjgl.glfw.GLFW;

public class HitX implements ClientModInitializer {

    // ── Modül Durumları ──────────────────────────────────────
    public static boolean hitBoxActive      = false;
    public static boolean triggerBotActive  = false;
    public static boolean aimAssistActive   = false;
    public static boolean nightVisionActive = false;

    // ── AimAssist Ayarları ───────────────────────────────────
    public static float   aimAssistRange    = 3.2f;
    public static float   elytraRange       = 5.5f;
    public static boolean elytraTarget      = true;
    public static float   aimAssistSpeed    = 0.15f;   // 0.05 – 1.0 arası
    public static boolean recoilActive      = false;   // Sarsılma (recoil)
    public static float   recoilStrength    = 0.3f;

    // ── TriggerBot Ayarları ──────────────────────────────────
    public static int     triggerDelay      = 0;       // ms gecikme (0-500)

    // ── Keybind Tuşları ──────────────────────────────────────
    public static int keyHitbox     = GLFW.GLFW_KEY_H;
    public static int keyAimAssist  = GLFW.GLFW_KEY_J;
    public static int keyTriggerBot = GLFW.GLFW_KEY_K;
    public static int keyNightVision= GLFW.GLFW_KEY_N;

    // ── İç Değişkenler ───────────────────────────────────────
    private boolean mLast              = false;
    private boolean kHitboxLast        = false;
    private boolean kAimAssistLast     = false;
    private boolean kTriggerLast       = false;
    private boolean kNightVisionLast   = false;

    private long    lastTriggerTime    = 0;

    @Override
    public void onInitializeClient() {
        AutoConfig.register(HitXConfig.class, GsonConfigSerializer::new);

        ClientTickEvents.END_WORLD_TICK.register((client) -> OverlayReloadListener.callEvent());

        // Envanter butonları
        ScreenEvents.AFTER_INIT.register((client, screen, W, H) -> {
            if (screen instanceof InventoryScreen inv) {
                int bx = W / 2 + 92, by = H / 2 - 50;
                int id = inv.getScreenHandler().syncId;
                iconBtn(screen, new ItemStack(Items.DIAMOND_CHESTPLATE), "§bZırhı Giy", bx, by, 22, 22, b -> {
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
            HitXConfig config = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();

            // M → Menü aç
            boolean mNow = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_M) == GLFW.GLFW_PRESS;
            if (mNow && !mLast) client.setScreen(new HitXMenu());
            mLast = mNow;

            // Keybindlar (sadece oyun ekranında)
            if (client.currentScreen == null) {
                boolean kH  = GLFW.glfwGetKey(handle, keyHitbox)      == GLFW.GLFW_PRESS;
                boolean kA  = GLFW.glfwGetKey(handle, keyAimAssist)   == GLFW.GLFW_PRESS;
                boolean kT  = GLFW.glfwGetKey(handle, keyTriggerBot)  == GLFW.GLFW_PRESS;
                boolean kNV = GLFW.glfwGetKey(handle, keyNightVision) == GLFW.GLFW_PRESS;

                if (kH  && !kHitboxLast)     hitBoxActive      = !hitBoxActive;
                if (kA  && !kAimAssistLast) {
                    aimAssistActive = !aimAssistActive;
                    sendMsg(client, aimAssistActive
                            ? "§aSarsılma Aktif" : "§cSarsılma Kapalı");
                }
                if (kT  && !kTriggerLast) {
                    triggerBotActive = !triggerBotActive;
                    sendMsg(client, triggerBotActive
                            ? "§aGece Görüşü Aktif" : "§cGece Görüşü Kapalı");
                }
                if (kNV && !kNightVisionLast) {
                    nightVisionActive = !nightVisionActive;
                    if (nightVisionActive)
                        applyNightVision(client);
                    else
                        removeNightVision(client);
                }

                kHitboxLast     = kH;
                kAimAssistLast  = kA;
                kTriggerLast    = kT;
                kNightVisionLast= kNV;
            }

            // HitBoxes genişletme
            if (hitBoxActive) {
                for (Entity e : client.world.getEntities()) {
                    if (e instanceof LivingEntity le && le != client.player) {
                        float hw   = (0.6f * config.xzExpand) / 2f;
                        float tall = 1.8f * config.yExpand;
                        le.setBoundingBox(new Box(
                            le.getX() - hw, le.getY() + config.yOffset, le.getZ() - hw,
                            le.getX() + hw, le.getY() + tall + config.yOffset, le.getZ() + hw));
                    }
                }
            }

            // Night Vision sürekli yenile
            if (nightVisionActive) applyNightVision(client);

            // Dövüş mantığı
            handleCombat(client);
        });
    }

    // ── Night Vision ─────────────────────────────────────────
    private void applyNightVision(MinecraftClient client) {
        client.player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
            net.minecraft.entity.effect.StatusEffects.NIGHT_VISION, 300, 0, false, false));
    }
    private void removeNightVision(MinecraftClient client) {
        client.player.removeStatusEffect(net.minecraft.registry.entry.RegistryEntry.of(
            net.minecraft.entity.effect.StatusEffects.NIGHT_VISION));
    }

    // ── AimAssist + TriggerBot ────────────────────────────────
    private void handleCombat(MinecraftClient client) {
        if (!aimAssistActive && !triggerBotActive) return;
        if (client.player.getAttackCooldownProgress(0.5f) < 1.0f) return;

        LivingEntity target = null;

        // TriggerBot – nişan alınan entity
        if (triggerBotActive && client.crosshairTarget instanceof net.minecraft.util.hit.EntityHitResult hit) {
            if (hit.getEntity() instanceof LivingEntity le && le.isAlive()) target = le;
        }

        // AimAssist – en yakın düşman
        if (aimAssistActive && target == null) {
            double maxDist = client.player.isFallFlying() ? elytraRange : aimAssistRange;
            double best    = Double.MAX_VALUE;
            for (Entity e : client.world.getEntities()) {
                if (e instanceof LivingEntity le && le != client.player && le.isAlive()) {
                    double d = client.player.distanceTo(le);
                    if (d <= maxDist && d < best) { best = d; target = le; }
                }
            }
            // Yaw/Pitch yönlendirme (smooth)
            if (target != null) smoothAim(client, target);
        }

        if (target != null) {
            long now = System.currentTimeMillis();
            if (now - lastTriggerTime >= triggerDelay) {
                client.interactionManager.attackEntity(client.player, target);
                client.player.swingHand(Hand.MAIN_HAND);
                // Recoil (sarsılma) simülasyonu
                if (recoilActive) {
                    client.player.setPitch(client.player.getPitch() - recoilStrength);
                }
                lastTriggerTime = now;
            }
        }
    }

    private void smoothAim(MinecraftClient client, LivingEntity target) {
        Vec3d eyePos    = client.player.getEyePos();
        Vec3d targetPos = target.getEyePos();
        double dx = targetPos.x - eyePos.x;
        double dy = targetPos.y - eyePos.y;
        double dz = targetPos.z - eyePos.z;
        double horDist  = Math.sqrt(dx * dx + dz * dz);
        float  targetYaw   = (float)(Math.toDegrees(Math.atan2(-dx, dz)));
        float  targetPitch = (float)(Math.toDegrees(-Math.atan2(dy, horDist)));
        float  curYaw   = client.player.getYaw();
        float  curPitch = client.player.getPitch();
        float  speed    = aimAssistSpeed;
        client.player.setYaw(lerpAngle(curYaw, targetYaw, speed));
        client.player.setPitch(lerp(curPitch, targetPitch, speed));
    }

    private float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private float lerpAngle(float a, float b, float t) {
        float diff = ((b - a + 540) % 360) - 180;
        return a + diff * t;
    }

    private void sendMsg(MinecraftClient client, String msg) {
        if (client.player != null)
            client.player.sendMessage(Text.literal("§8[§dHitX§8] §r" + msg), true);
    }

    // ═══════════════════════════════════════════════════════════
    //  MENÜ
    // ═══════════════════════════════════════════════════════════
    public class HitXMenu extends Screen {

        private String openTab     = "Hitboxes";
        private int    bindingFor  = -1;

        // Sekme isimleri
        private static final String[] TABS = {"Hitboxes","AimAssist","TriggerBot","HitColor","Keybinds"};

        protected HitXMenu() { super(Text.literal("HitX Menu")); }

        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            HitXConfig cfg = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();

            int W = 420, H = 300;
            int ox = width  / 2 - W / 2;
            int oy = height / 2 - H / 2;

            // Arka plan
            fillRounded(ctx, ox, oy, ox + W, oy + H, 0xF0131313, 8);

            // Başlık çubuğu
            fillRounded(ctx, ox, oy, ox + W, oy + 24, 0xFF1E0033, 8);
            ctx.drawCenteredTextWithShadow(textRenderer, "§d§lHITX §8│ §7Menu", ox + W / 2, oy + 8, 0xFFFFFF);

            // Sol sekme çubuğu
            fillRounded(ctx, ox, oy + 24, ox + 110, oy + H, 0xFF1A1A1A, 0);
            int ty = oy + 32;
            for (String tab : TABS) {
                boolean sel = tab.equals(openTab);
                int tbg = sel ? 0xFF5500AA : (isHover(mx, my, ox + 6, ty, 98, 20) ? 0xFF2A2A2A : 0xFF222222);
                fillRounded(ctx, ox + 6, ty, ox + 104, ty + 20, tbg, 4);
                ctx.drawTextWithShadow(textRenderer, sel ? "§d" + tab : "§7" + tab, ox + 14, ty + 6, 0xFFFFFF);
                ty += 24;
            }

            // Sağ içerik paneli
            int cx = ox + 116, cy = oy + 30, cw = W - 122, ch = H - 36;
            fillRounded(ctx, cx - 4, cy - 4, cx + cw, cy + ch, 0xFF1E1E1E, 6);

            switch (openTab) {
                case "Hitboxes"   -> renderHitboxes(ctx, cfg, cx, cy, cw, mx, my);
                case "AimAssist"  -> renderAimAssist(ctx, cfg, cx, cy, cw, mx, my);
                case "TriggerBot" -> renderTriggerBot(ctx, cfg, cx, cy, cw, mx, my);
                case "HitColor"   -> renderHitColor(ctx, cfg, cx, cy, cw, mx, my);
                case "Keybinds"   -> renderKeybinds(ctx, cx, cy, cw, mx, my);
            }

            super.render(ctx, mx, my, d);
        }

        // ── Hitboxes Sekmesi ──────────────────────────────────
        private void renderHitboxes(DrawContext ctx, HitXConfig cfg, int cx, int cy, int cw, int mx, int my) {
            toggleRow(ctx, cx, cy,      cw, "Hitboxes", hitBoxActive, mx, my);
            label(ctx, cx, cy + 28,  "Genişlik (XZ): " + String.format("%.2f", cfg.xzExpand));
            slider(ctx, cx, cy + 40,  cw, (cfg.xzExpand - 0.5f) / 4.5f);
            label(ctx, cx, cy + 66,  "Yükseklik (Y): " + String.format("%.2f", cfg.yExpand));
            slider(ctx, cx, cy + 78,  cw, (cfg.yExpand - 0.5f) / 3.5f);
            label(ctx, cx, cy + 104, "Y Offset: " + String.format("%.2f", cfg.yOffset));
            slider(ctx, cx, cy + 116, cw, (cfg.yOffset + 1f) / 2f);
        }

        // ── AimAssist Sekmesi ─────────────────────────────────
        private void renderAimAssist(DrawContext ctx, HitXConfig cfg, int cx, int cy, int cw, int mx, int my) {
            toggleRow(ctx, cx, cy,       cw, "AimAssist", aimAssistActive, mx, my);
            toggleRow(ctx, cx, cy + 26,  cw, "Sarsılma (Recoil)", recoilActive, mx, my);
            label(ctx, cx, cy + 58,  "Menzil: " + String.format("%.1f", aimAssistRange));
            slider(ctx, cx, cy + 70,  cw, (aimAssistRange - 1f) / 9f);
            label(ctx, cx, cy + 96,  "Hız: " + String.format("%.2f", aimAssistSpeed));
            slider(ctx, cx, cy + 108, cw, (aimAssistSpeed - 0.05f) / 0.95f);
            label(ctx, cx, cy + 134, "Sarsılma Şiddeti: " + String.format("%.2f", recoilStrength));
            slider(ctx, cx, cy + 146, cw, recoilStrength / 2f);
        }

        // ── TriggerBot Sekmesi ────────────────────────────────
        private void renderTriggerBot(DrawContext ctx, HitXConfig cfg, int cx, int cy, int cw, int mx, int my) {
            toggleRow(ctx, cx, cy,      cw, "TriggerBot", triggerBotActive, mx, my);
            label(ctx, cx, cy + 32, "Gecikme (ms): " + triggerDelay);
            slider(ctx, cx, cy + 44, cw, triggerDelay / 500f);
        }

        // ── HitColor Sekmesi ──────────────────────────────────
        private void renderHitColor(DrawContext ctx, HitXConfig cfg, int cx, int cy, int cw, int mx, int my) {
            toggleRow(ctx, cx, cy,       cw, "HitColor", cfg.hitColorActive, mx, my);
            label(ctx, cx, cy + 32,  "§cKırmızı: " + cfg.hcRed);
            slider(ctx, cx, cy + 44,  cw, cfg.hcRed   / 255f);
            label(ctx, cx, cy + 68,  "§aYeşil: "   + cfg.hcGreen);
            slider(ctx, cx, cy + 80,  cw, cfg.hcGreen / 255f);
            label(ctx, cx, cy + 104, "§bMavi: "    + cfg.hcBlue);
            slider(ctx, cx, cy + 116, cw, cfg.hcBlue  / 255f);
            label(ctx, cx, cy + 140, "§7Alpha: "   + cfg.hcAlpha);
            slider(ctx, cx, cy + 152, cw, cfg.hcAlpha / 255f);
        }

        // ── Keybinds Sekmesi ──────────────────────────────────
        private void renderKeybinds(DrawContext ctx, int cx, int cy, int cw, int mx, int my) {
            keybindRow(ctx, cx, cy,       cw, "Hitboxes",    keyHitbox,      bindingFor == 0, mx, my);
            keybindRow(ctx, cx, cy + 28,  cw, "AimAssist",   keyAimAssist,   bindingFor == 1, mx, my);
            keybindRow(ctx, cx, cy + 56,  cw, "TriggerBot",  keyTriggerBot,  bindingFor == 2, mx, my);
            keybindRow(ctx, cx, cy + 84,  cw, "NightVision", keyNightVision, bindingFor == 3, mx, my);
        }

        // ── Yardımcı Çizim Metodları ──────────────────────────
        private void toggleRow(DrawContext ctx, int x, int y, int w, String name, boolean on, int mx, int my) {
            int bg = on ? 0xFF004433 : (isHover(mx, my, x, y, w, 20) ? 0xFF2E2E2E : 0xFF252525);
            fillRounded(ctx, x, y, x + w, y + 20, bg, 4);
            ctx.drawTextWithShadow(textRenderer, name, x + 8, y + 6, on ? 0xFF00FFCC : 0xFFBBBBBB);
            ctx.drawTextWithShadow(textRenderer, on ? "§aAÇIK" : "§cKAPALI", x + w - 44, y + 6, 0xFFFFFFFF);
        }

        private void label(DrawContext ctx, int x, int y, String text) {
            ctx.drawTextWithShadow(textRenderer, text, x, y, 0xFFCCCCCC);
        }

        private void slider(DrawContext ctx, int x, int y, int w, float pct) {
            pct = Math.max(0, Math.min(1, pct));
            fillRounded(ctx, x, y, x + w, y + 8, 0xFF111111, 3);
            ctx.fill(x + 1, y + 1, x + 1 + (int)((w - 2) * pct), y + 7, 0xFF9900FF);
            int kx = x + (int)((w - 4) * pct);
            ctx.fill(kx, y - 1, kx + 4, y + 9, 0xFFFFFFFF);
        }

        private void keybindRow(DrawContext ctx, int x, int y, int w, String mod, int key, boolean waiting, int mx, int my) {
            int bg = waiting ? 0xFF440066 : (isHover(mx, my, x, y, w, 20) ? 0xFF303030 : 0xFF252525);
            fillRounded(ctx, x, y, x + w, y + 20, bg, 4);
            ctx.drawTextWithShadow(textRenderer, mod, x + 8, y + 6, 0xFFCCCCCC);
            ctx.drawTextWithShadow(textRenderer, waiting ? "§e[tuşa bas...]" : "§d[" + keyName(key) + "]",
                    x + w - 80, y + 6, 0xFFFFFFFF);
        }

        private boolean isHover(int mx, int my, int x, int y, int w, int h) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }

        private void fillRounded(DrawContext ctx, int x1, int y1, int x2, int y2, int col, int r) {
            ctx.fill(x1, y1, x2, y2, col);
        }

        // ── Tıklama ───────────────────────────────────────────
        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            HitXConfig cfg = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();
            int W = 420, H = 300;
            int ox = width  / 2 - W / 2;
            int oy = height / 2 - H / 2;

            // Sekme değiştirme
            int ty = oy + 32;
            for (String tab : TABS) {
                if (mx >= ox + 6 && mx <= ox + 104 && my >= ty && my <= ty + 20) {
                    openTab = tab; bindingFor = -1; return true;
                }
                ty += 24;
            }

            int cx = ox + 116, cy = oy + 30, cw = W - 122;

            switch (openTab) {
                case "Hitboxes" -> {
                    if (isHoverD(mx, my, cx, cy, cw, 20))          { hitBoxActive = !hitBoxActive; return true; }
                    if (isHoverD(mx, my, cx, cy + 46, cw, 8))      { cfg.xzExpand = 0.5f + (float)((mx - cx) / (double)cw) * 4.5f; saveConfig(); return true; }
                    if (isHoverD(mx, my, cx, cy + 84, cw, 8))      { cfg.yExpand  = 0.5f + (float)((mx - cx) / (double)cw) * 3.5f; saveConfig(); return true; }
                    if (isHoverD(mx, my, cx, cy + 122, cw, 8))     { cfg.yOffset  = -1f  + (float)((mx - cx) / (double)cw) * 2f;   saveConfig(); return true; }
                }
                case "AimAssist" -> {
                    if (isHoverD(mx, my, cx, cy,      cw, 20)) { aimAssistActive = !aimAssistActive;
                        sendMsg(client, aimAssistActive ? "§aSarsılma Aktif" : "§cSarsılma Kapalı"); return true; }
                    if (isHoverD(mx, my, cx, cy + 26, cw, 20)) { recoilActive = !recoilActive; return true; }
                    if (isHoverD(mx, my, cx, cy + 76, cw, 8))  { aimAssistRange = 1f + (float)((mx - cx) / (double)cw) * 9f; return true; }
                    if (isHoverD(mx, my, cx, cy + 114,cw, 8))  { aimAssistSpeed = 0.05f + (float)((mx - cx) / (double)cw) * 0.95f; return true; }
                    if (isHoverD(mx, my, cx, cy + 152,cw, 8))  { recoilStrength = (float)((mx - cx) / (double)cw) * 2f; return true; }
                }
                case "TriggerBot" -> {
                    if (isHoverD(mx, my, cx, cy,      cw, 20)) { triggerBotActive = !triggerBotActive;
                        sendMsg(client, triggerBotActive ? "§aGece Görüşü Aktif" : "§cGece Görüşü Kapalı"); return true; }
                    if (isHoverD(mx, my, cx, cy + 50, cw, 8))  { triggerDelay = (int)((mx - cx) / (double)cw * 500); return true; }
                }
                case "HitColor" -> {
                    if (isHoverD(mx, my, cx, cy,       cw, 20)) { cfg.hitColorActive = !cfg.hitColorActive; saveConfig(); OverlayReloadListener.callEvent(); return true; }
                    if (isHoverD(mx, my, cx, cy + 50,  cw, 8))  { cfg.hcRed   = (int)((mx - cx) / (double)cw * 255); saveConfig(); OverlayReloadListener.callEvent(); return true; }
                    if (isHoverD(mx, my, cx, cy + 86,  cw, 8))  { cfg.hcGreen = (int)((mx - cx) / (double)cw * 255); saveConfig(); OverlayReloadListener.callEvent(); return true; }
                    if (isHoverD(mx, my, cx, cy + 122, cw, 8))  { cfg.hcBlue  = (int)((mx - cx) / (double)cw * 255); saveConfig(); OverlayReloadListener.callEvent(); return true; }
                    if (isHoverD(mx, my, cx, cy + 158, cw, 8))  { cfg.hcAlpha = (int)((mx - cx) / (double)cw * 255); saveConfig(); OverlayReloadListener.callEvent(); return true; }
                }
                case "Keybinds" -> {
                    if (isHoverD(mx, my, cx, cy,      cw, 20)) { bindingFor = 0; return true; }
                    if (isHoverD(mx, my, cx, cy + 28, cw, 20)) { bindingFor = 1; return true; }
                    if (isHoverD(mx, my, cx, cy + 56, cw, 20)) { bindingFor = 2; return true; }
                    if (isHoverD(mx, my, cx, cy + 84, cw, 20)) { bindingFor = 3; return true; }
                }
            }
            return super.mouseClicked(mx, my, btn);
        }

        @Override
        public boolean keyPressed(int k, int s, int m) {
            if (bindingFor == 0) { keyHitbox      = k; bindingFor = -1; return true; }
            if (bindingFor == 1) { keyAimAssist   = k; bindingFor = -1; return true; }
            if (bindingFor == 2) { keyTriggerBot  = k; bindingFor = -1; return true; }
            if (bindingFor == 3) { keyNightVision = k; bindingFor = -1; return true; }
            return super.keyPressed(k, s, m);
        }

        private boolean isHoverD(double mx, double my, int x, int y, int w, int h) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }

        private void saveConfig() { AutoConfig.getConfigHolder(HitXConfig.class).save(); }
        private String keyName(int key) {
            String n = GLFW.glfwGetKeyName(key, 0);
            return n != null ? n.toUpperCase() : "KEY_" + key;
        }
    }

    // ── Yardımcılar ───────────────────────────────────────────
    private void iconBtn(Screen s, ItemStack i, String t, int x, int y, int w, int h, ButtonWidget.PressAction p) {}
    private boolean isArmor(ItemStack s) { return s.getItem() instanceof net.minecraft.item.ArmorItem; }
}
