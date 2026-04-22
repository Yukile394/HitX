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
    public static boolean hitBoxActive       = false;
    public static boolean triggerBotActive   = false;
    public static boolean aimAssistActive    = false;
    public static boolean nightVisionActive  = false;

    // ── AimAssist Ayarları ───────────────────────────────────
    public static float   aimRange           = 4.5f;    // menzil
    public static float   aimSpeed           = 0.12f;   // smooth hız 0.01–0.5
    public static float   aimFov             = 90f;     // FOV limiti
    public static boolean aimAutoAttack      = false;   // oto vurma (ayrı toggle)
    public static boolean aimRecoil          = false;   // sarsılma
    public static float   aimRecoilStr       = 0.25f;   // sarsılma şiddeti
    public static boolean aimElytra          = true;    // elytra menzil farklı
    public static float   aimElytraRange     = 6.0f;

    // ── TriggerBot Ayarları ──────────────────────────────────
    public static int     triggerDelay       = 50;      // ms

    // ── Night Vision ─────────────────────────────────────────
    // (aktif/pasif keybind ve panelden kontrol)

    // ── Keybindlar ───────────────────────────────────────────
    public static int keyHitbox      = GLFW.GLFW_KEY_H;
    public static int keyAimAssist   = GLFW.GLFW_KEY_J;
    public static int keyTriggerBot  = GLFW.GLFW_KEY_K;
    public static int keyNightVision = GLFW.GLFW_KEY_N;

    // ── İç Durum ─────────────────────────────────────────────
    private boolean mLast = false, kHLast = false, kALast = false, kTLast = false, kNLast = false;
    private long    lastAttack = 0L;
    private long    nvTick     = 0L;
    private LivingEntity locked = null;

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
                            for (int i = 9; i < 45; i++)
                                if (isArmor(inv.getScreenHandler().getSlot(i).getStack()))
                                    client.interactionManager.clickSlot(id, i, 0, SlotActionType.QUICK_MOVE, client.player);
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

            if (client.currentScreen == null) {
                boolean kH = GLFW.glfwGetKey(handle, keyHitbox)      == GLFW.GLFW_PRESS;
                boolean kA = GLFW.glfwGetKey(handle, keyAimAssist)   == GLFW.GLFW_PRESS;
                boolean kT = GLFW.glfwGetKey(handle, keyTriggerBot)  == GLFW.GLFW_PRESS;
                boolean kN = GLFW.glfwGetKey(handle, keyNightVision) == GLFW.GLFW_PRESS;

                if (kH && !kHLast) hitBoxActive = !hitBoxActive;
                if (kA && !kALast) {
                    aimAssistActive = !aimAssistActive;
                    locked = null;
                    bar(client, aimAssistActive ? "§aAimAssist §7Açık" : "§cAimAssist §7Kapalı");
                }
                if (kT && !kTLast) {
                    triggerBotActive = !triggerBotActive;
                    bar(client, triggerBotActive ? "§aTriggerBot §7Açık" : "§cTriggerBot §7Kapalı");
                }
                if (kN && !kNLast) {
                    nightVisionActive = !nightVisionActive;
                    if (!nightVisionActive) client.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
                    bar(client, nightVisionActive ? "§aGece Görüşü §7Açık" : "§cGece Görüşü §7Kapalı");
                }
                kHLast = kH; kALast = kA; kTLast = kT; kNLast = kN;
            }

            // HitBoxes
            if (hitBoxActive) {
                for (Entity e : client.world.getEntities()) {
                    if (e instanceof LivingEntity le && le != client.player) {
                        float hw = (0.6f * cfg.xzExpand) / 2f;
                        float ht = 1.8f * cfg.yExpand;
                        le.setBoundingBox(new Box(
                                le.getX()-hw, le.getY()+cfg.yOffset, le.getZ()-hw,
                                le.getX()+hw, le.getY()+ht+cfg.yOffset, le.getZ()+hw));
                    }
                }
            }

            // Night Vision — her 4 tick'te yenile (sadece gerektiğinde)
            if (nightVisionActive) {
                nvTick++;
                if (nvTick % 4 == 0) {
                    StatusEffectInstance cur = client.player.getStatusEffect(StatusEffects.NIGHT_VISION);
                    if (cur == null || cur.getDuration() < 60) {
                        client.player.addStatusEffect(
                                new StatusEffectInstance(StatusEffects.NIGHT_VISION, 400, 0, false, false));
                    }
                }
            }

            handleCombat(client);
        });
    }

    // ════════════════════════════════════════════════════════
    //  COMBAT — AimAssist sadece kitler, oto vurma ayrı toggle
    // ════════════════════════════════════════════════════════
    private void handleCombat(MinecraftClient client) {
        // Kilit geçerliliği
        if (locked != null && (!locked.isAlive() ||
                client.player.distanceTo(locked) > aimRange + 2f)) locked = null;

        // Hedef seç
        if (aimAssistActive && locked == null) {
            float maxD  = (aimElytra && client.player.isFallFlying()) ? aimElytraRange : aimRange;
            double best = Double.MAX_VALUE;
            for (Entity e : client.world.getEntities()) {
                if (!(e instanceof LivingEntity le) || le == client.player || !le.isAlive()) continue;
                double d = client.player.distanceTo(le);
                if (d > maxD) continue;
                float  a = angleTo(client, le);
                if (a > aimFov) continue;
                double score = a * 0.5 + d * 0.5;
                if (score < best) { best = score; locked = le; }
            }
        }

        // AimAssist — sadece kamera döndür (Yeni Flawless Lock)
        if (aimAssistActive && locked != null) smoothAim(client, locked);

        // TriggerBot — nişandaki hedefe vur (kilitli hedef gerekmez)
        LivingEntity trigTarget = null;
        if (triggerBotActive &&
                client.crosshairTarget instanceof net.minecraft.util.hit.EntityHitResult hit &&
                hit.getEntity() instanceof LivingEntity le && le.isAlive()) {
            trigTarget = le;
        }

        // Oto Vurma — sadece aimAutoAttack açıksa VE kilitli hedef varsa
        LivingEntity autoTarget = null;
        if (aimAutoAttack && locked != null) autoTarget = locked;

        // Saldırılacak hedef
        LivingEntity atk = trigTarget != null ? trigTarget : autoTarget;
        if (atk == null) return;
        if (client.player.getAttackCooldownProgress(0.5f) < 1.0f) return;
        long now = System.currentTimeMillis();
        if (now - lastAttack < triggerDelay) return;

        client.interactionManager.attackEntity(client.player, atk);
        client.player.swingHand(Hand.MAIN_HAND);
        lastAttack = now;

        if (aimRecoil) client.player.setPitch(client.player.getPitch() - aimRecoilStr);
    }

    private float angleTo(MinecraftClient c, LivingEntity t) {
        Vec3d look = c.player.getRotationVec(1f);
        Vec3d toT  = t.getEyePos().subtract(c.player.getEyePos()).normalize();
        return (float) Math.toDegrees(Math.acos(MathHelper.clamp(look.dotProduct(toT), -1.0, 1.0)));
    }

    // YENİ FLAWLESS KİLİTLENME VE ANTİ-STUTTER SİSTEMİ
    private void smoothAim(MinecraftClient client, LivingEntity target) {
        // 1. Prediction (Hareket Tahmini): Hedef hareket ediyorsa önüne kilitlen
        double predictX = target.getX() + (target.getX() - target.prevX);
        double predictZ = target.getZ() + (target.getZ() - target.prevZ);
        
        // 2. Yükseklik: Ayaklara değil, tam boyun/göğüs hizasına kilitlen
        double targetY = target.getY() + (target.getEyeHeight(target.getPose()) * 0.75);

        // 3. Hedefe Giden Açıyı Hesapla (Pitch ve Yaw)
        float deltaYaw = MathHelper.wrapDegrees((float) Math.toDegrees(Math.atan2(predictZ - client.player.getZ(), predictX - client.player.getX())) - 90.0f - client.player.getYaw());
        float deltaPitch = ((float) -Math.toDegrees(Math.atan2(targetY - (client.player.getY() + client.player.getEyeHeight(client.player.getPose())), Math.sqrt(Math.pow(predictX - client.player.getX(), 2) + Math.pow(predictZ - client.player.getZ(), 2))))) - client.player.getPitch();

        // 4. GUI'deki "Smooth Hız" (aimSpeed) ile dönüş gücünü ayarla
        float newYaw = client.player.getYaw() + (deltaYaw * aimSpeed);
        float newPitch = MathHelper.clamp(client.player.getPitch() + (deltaPitch * aimSpeed), -90f, 90f);

        // 5. Anti-Stutter / GCD Fix (Mouse Sensitivity bazlı ekran titremesini engelleme)
        double gcdFix = (Math.pow(client.options.getMouseSensitivity().getValue() * 0.6 + 0.2, 3.0)) * 8.0 * 0.15;
        
        float finalYaw = (float) (newYaw - (newYaw - client.player.getYaw()) % gcdFix);
        float finalPitch = (float) (newPitch - (newPitch - client.player.getPitch()) % gcdFix);

        // 6. Sonucu uygula
        client.player.setYaw(finalYaw);
        client.player.setPitch(finalPitch);
    }

    private void bar(MinecraftClient c, String m) {
        if (c.player != null) c.player.sendMessage(Text.literal("§8[§dHitX§8] §r"+m), true);
    }

    // ════════════════════════════════════════════════════════
    //  MENÜ
    // ════════════════════════════════════════════════════════
    public class HitXMenu extends Screen {

        private static final String[] TABS = {
            "Hitboxes", "AimAssist", "TriggerBot", "NightVision", "HitColor", "Keybinds"
        };
        private static final int PW = 440, PH = 320;

        private String tab  = "AimAssist";
        private int    bind = -1;
        private int    dragSlot = -1, dCX, dCW;

        protected HitXMenu() { super(Text.literal("HitX")); }

        // ── Render ──────────────────────────────────────────
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            HitXConfig cfg = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();
            int ox = ox(), oy = oy();

            f(ctx, ox,      oy,      ox+PW,   oy+PH,   0xF2101010);
            f(ctx, ox,      oy,      ox+PW,   oy+26,   0xFF180028);
            f(ctx, ox,      oy+26,   ox+114,  oy+PH,   0xFF131313);

            ctx.drawCenteredTextWithShadow(textRenderer,
                    "§d§lHITX  §8│  §7Kontrol Paneli", ox+PW/2, oy+9, 0xFFFFFF);

            // Sekme listesi
            int ty = oy+32;
            for (String t : TABS) {
                boolean sel = t.equals(tab);
                boolean hov = hov(mx,my,ox+5,ty,104,22);
                f(ctx, ox+5, ty, ox+109, ty+22, sel?0xFF5500BB:(hov?0xFF232323:0xFF1A1A1A));
                if (sel) f(ctx, ox+5, ty, ox+7, ty+22, 0xFFCC00FF);
                ctx.drawTextWithShadow(textRenderer, t, ox+12, ty+7,
                        sel?0xFFFF99FF:(hov?0xFFDDDDDD:0xFF777777));
                ty += 26;
            }

            // İçerik alanı
            int cx=cx(), cy=cy(), cw=cw();
            f(ctx, cx-3, cy-3, cx+cw+3, oy+PH-4, 0xFF1A1A1A);

            switch (tab) {
                case "Hitboxes"    -> tHitboxes   (ctx,cfg,cx,cy,cw,mx,my);
                case "AimAssist"   -> tAimAssist  (ctx,cfg,cx,cy,cw,mx,my);
                case "TriggerBot"  -> tTriggerBot (ctx,cfg,cx,cy,cw,mx,my);
                case "NightVision" -> tNightVision(ctx,    cx,cy,cw,mx,my);
                case "HitColor"    -> tHitColor   (ctx,cfg,cx,cy,cw,mx,my);
                case "Keybinds"    -> tKeybinds   (ctx,    cx,cy,cw,mx,my);
            }

            super.render(ctx, mx, my, d);
        }

        // ── Sekme: Hitboxes ──────────────────────────────────
        private void tHitboxes(DrawContext ctx, HitXConfig cfg, int cx, int cy, int cw, int mx, int my) {
            tog(ctx,cx,cy,     cw,"Hitboxes",hitBoxActive,mx,my);
            lbl(ctx,cx,cy+30,  "Genişlik (XZ):  §e"+f2(cfg.xzExpand));
            sld(ctx,cx,cy+42,  cw,(cfg.xzExpand-0.5f)/4.5f,0);
            lbl(ctx,cx,cy+60,  "Yükseklik (Y):  §e"+f2(cfg.yExpand));
            sld(ctx,cx,cy+72,  cw,(cfg.yExpand-0.5f)/3.5f,1);
            lbl(ctx,cx,cy+90,  "Y Offset:       §e"+f2(cfg.yOffset));
            sld(ctx,cx,cy+102, cw,(cfg.yOffset+1f)/2f,2);
        }

        // ── Sekme: AimAssist ─────────────────────────────────
        private void tAimAssist(DrawContext ctx, HitXConfig cfg, int cx, int cy, int cw, int mx, int my) {
            tog(ctx,cx,cy,      cw,"AimAssist (Kilit)",   aimAssistActive, mx,my);
            tog(ctx,cx,cy+26,   cw,"Oto Vurma",           aimAutoAttack,   mx,my);
            tog(ctx,cx,cy+52,   cw,"Sarsılma (Recoil)",   aimRecoil,       mx,my);
            tog(ctx,cx,cy+78,   cw,"Elytra Menzili",      aimElytra,       mx,my);

            lbl(ctx,cx,cy+108,  "Menzil:           §e"+f1(aimRange)+" blok");
            sld(ctx,cx,cy+120,  cw,(aimRange-1f)/9f,10);
            lbl(ctx,cx,cy+138,  "Smooth Hız:       §e"+f2(aimSpeed));
            sld(ctx,cx,cy+150,  cw,(aimSpeed-0.01f)/0.49f,11);
            lbl(ctx,cx,cy+168,  "FOV Limiti:       §e"+f1(aimFov)+"°");
            sld(ctx,cx,cy+180,  cw,aimFov/180f,12);
            lbl(ctx,cx,cy+198,  "Sarsılma Şiddeti: §e"+f2(aimRecoilStr));
            sld(ctx,cx,cy+210,  cw,aimRecoilStr/2f,13);
        }

        // ── Sekme: TriggerBot ────────────────────────────────
        private void tTriggerBot(DrawContext ctx, HitXConfig cfg, int cx, int cy, int cw, int mx, int my) {
            tog(ctx,cx,cy,    cw,"TriggerBot",triggerBotActive,mx,my);
            lbl(ctx,cx,cy+30, "Gecikme:  §e"+triggerDelay+" ms");
            sld(ctx,cx,cy+42, cw,triggerDelay/500f,20);
            lbl(ctx,cx,cy+62, triggerBotActive?"§aNişandaki düşmana otomatik vurur":"§7Kapalı");
        }

        // ── Sekme: NightVision ───────────────────────────────
        private void tNightVision(DrawContext ctx, int cx, int cy, int cw, int mx, int my) {
            tog(ctx,cx,cy,    cw,"Gece Görüşü",nightVisionActive,mx,my);
            lbl(ctx,cx,cy+32, nightVisionActive
                    ?"§aAktif — ekran tam aydınlık görünür"
                    :"§7Kapalı — normal görüş");
            lbl(ctx,cx,cy+50, "§8Keybind: §dKeybinds §8sekmesinden ayarlanabilir");
        }

        // ── Sekme: HitColor ──────────────────────────────────
        private void tHitColor(DrawContext ctx, HitXConfig cfg, int cx, int cy, int cw, int mx, int my) {
            tog(ctx,cx,cy,cw,"HitColor",cfg.hitColorActive,mx,my);
            int pc = (cfg.hcAlpha<<24)|(cfg.hcRed<<16)|(cfg.hcGreen<<8)|cfg.hcBlue;
            f(ctx,cx+cw-28,cy+3,cx+cw-4,cy+19,0xFF000000);
            f(ctx,cx+cw-27,cy+4,cx+cw-5,cy+18,pc);
            lbl(ctx,cx,cy+30,  "§cKırmızı:  §e"+cfg.hcRed);
            sld(ctx,cx,cy+42,  cw,cfg.hcRed/255f,  30);
            lbl(ctx,cx,cy+58,  "§aYeşil:    §e"+cfg.hcGreen);
            sld(ctx,cx,cy+70,  cw,cfg.hcGreen/255f,31);
            lbl(ctx,cx,cy+86,  "§bMavi:     §e"+cfg.hcBlue);
            sld(ctx,cx,cy+98,  cw,cfg.hcBlue/255f, 32);
            lbl(ctx,cx,cy+114, "§7Alpha:    §e"+cfg.hcAlpha);
            sld(ctx,cx,cy+126, cw,cfg.hcAlpha/255f,33);
        }

        // ── Sekme: Keybinds ──────────────────────────────────
        private void tKeybinds(DrawContext ctx, int cx, int cy, int cw, int mx, int my) {
            lbl(ctx,cx,cy,    "§8Satıra tıkla → yeni tuşa bas");
            kb(ctx,cx,cy+16,  cw,"Hitboxes",    keyHitbox,      bind==0,mx,my);
            kb(ctx,cx,cy+42,  cw,"AimAssist",   keyAimAssist,   bind==1,mx,my);
            kb(ctx,cx,cy+68,  cw,"TriggerBot",  keyTriggerBot,  bind==2,mx,my);
            kb(ctx,cx,cy+94,  cw,"NightVision", keyNightVision, bind==3,mx,my);
        }

        // ── Çizim ────────────────────────────────────────────
        private void f(DrawContext c,int x1,int y1,int x2,int y2,int col){ c.fill(x1,y1,x2,y2,col); }

        private void tog(DrawContext ctx,int x,int y,int w,String name,boolean on,int mx,int my){
            boolean hov=hov(mx,my,x,y,w,22);
            f(ctx,x,y,x+w,y+22,on?(hov?0xFF006644:0xFF004433):(hov?0xFF2B2B2B:0xFF202020));
            f(ctx,x,y,x+3,y+22,on?0xFF00FFAA:0xFF444444);
            ctx.drawTextWithShadow(textRenderer,name,x+8,y+7,on?0xFF00FFCC:0xFF999999);
            ctx.drawTextWithShadow(textRenderer,on?"§a● AÇIK":"§c○ KAPALI",x+w-54,y+7,0xFFFFFFFF);
        }

        private void sld(DrawContext ctx,int x,int y,int w,float p,int sid){
            p=MathHelper.clamp(p,0f,1f);
            f(ctx,x,y,x+w,y+10,0xFF0C0C0C);
            f(ctx,x+1,y+1,x+w-1,y+9,0xFF1C1C1C);
            int fw=(int)((w-2)*p);
            if(fw>0) f(ctx,x+1,y+1,x+1+fw,y+9,0xFF7700DD);
            if(fw>3) f(ctx,x+fw-1,y+2,x+1+fw,y+8,0xFFBB66FF);
            int kx=x+1+(int)((w-6)*p);
            f(ctx,kx,y-2,kx+4,y+12,0xFFFFFFFF);
        }

        private void lbl(DrawContext ctx,int x,int y,String t){ ctx.drawTextWithShadow(textRenderer,t,x,y,0xFFBBBBBB); }

        private void kb(DrawContext ctx,int x,int y,int w,String name,int key,boolean wait,int mx,int my){
            boolean hov=hov(mx,my,x,y,w,22);
            f(ctx,x,y,x+w,y+22,wait?0xFF2A0055:(hov?0xFF282828:0xFF1E1E1E));
            f(ctx,x,y,x+3,y+22,wait?0xFFFF00FF:0xFF6600CC);
            ctx.drawTextWithShadow(textRenderer,name,x+8,y+7,0xFFCCCCCC);
            ctx.drawTextWithShadow(textRenderer,wait?"§e[ tuşa bas... ]":"§d[ "+kn(key)+" ]",x+w-90,y+7,0xFFFFFFFF);
        }

        private boolean hov(int mx,int my,int x,int y,int w,int h){ return mx>=x&&mx<=x+w&&my>=y&&my<=y+h; }
        private boolean hovD(double mx,double my,int x,int y,int w,int h){ return mx>=x&&mx<=x+w&&my>=y&&my<=y+h; }

                private int ox(){ return width/2-PW/2; }
        private int oy(){ return height/2-PH/2; }
        private int cx(){ return ox()+120; }
        private int cy(){ return oy()+32; }
        private int cw(){ return PW-126; }

        // ── Mouse Tıklama Mantığı ────────────────────────────
        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            HitXConfig cfg = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();
            int ox=ox(), oy=oy(), cx=cx(), cy=cy(), cw=cw();

            // Sekme seçimi
            int ty = oy + 32;
            for (String t : TABS) {
                if (hovD(mx, my, (double)ox + 5, (double)ty, 104, 22)) {
                    tab = t;
                    bind = -1;
                    dragSlot = -1;
                    return true;
                }
                ty += 26;
            }

            switch (tab) {
                case "Hitboxes" -> {
                    if (hovD(mx, my, (double)cx, (double)cy, cw, 22)) { hitBoxActive = !hitBoxActive; return true; }
                    if (cs(mx, my, cx, cy + 42, cw, 0)) { cfg.xzExpand = 0.5f + sv(mx, cx, cw) * 4.5f; sc(); return true; }
                    if (cs(mx, my, cx, cy + 72, cw, 1)) { cfg.yExpand = 0.5f + sv(mx, cx, cw) * 3.5f; sc(); return true; }
                    if (cs(mx, my, cx, cy + 102, cw, 2)) { cfg.yOffset = -1f + sv(mx, cx, cw) * 2f; sc(); return true; }
                }
                case "AimAssist" -> {
                    if (hovD(mx, my, (double)cx, (double)cy, cw, 22)) { 
                        aimAssistActive = !aimAssistActive; 
                        locked = null;
                        bar(client, aimAssistActive ? "§aAimAssist §7Açık" : "§cAimAssist §7Kapalı"); 
                        return true; 
                    }
                    if (hovD(mx, my, (double)cx, (double)cy + 26, cw, 22)) { aimAutoAttack = !aimAutoAttack; return true; }
                    if (hovD(mx, my, (double)cx, (double)cy + 52, cw, 22)) { aimRecoil = !aimRecoil; return true; }
                    if (hovD(mx, my, (double)cx, (double)cy + 78, cw, 22)) { aimElytra = !aimElytra; return true; }
                    if (cs(mx, my, cx, cy + 120, cw, 10)) { aimRange = 1f + sv(mx, cx, cw) * 9f; return true; }
                    if (cs(mx, my, cx, cy + 150, cw, 11)) { aimSpeed = 0.01f + sv(mx, cx, cw) * 0.49f; return true; }
                    if (cs(mx, my, cx, cy + 180, cw, 12)) { aimFov = sv(mx, cx, cw) * 180f; return true; }
                    if (cs(mx, my, cx, cy + 210, cw, 13)) { aimRecoilStr = sv(mx, cx, cw) * 2f; return true; }
                }
                case "TriggerBot" -> {
                    if (hovD(mx, my, (double)cx, (double)cy, cw, 22)) { 
                        triggerBotActive = !triggerBotActive;
                        bar(client, triggerBotActive ? "§aTriggerBot §7Açık" : "§cTriggerBot §7Kapalı"); 
                        return true; 
                    }
                    if (cs(mx, my, cx, cy + 42, cw, 20)) { triggerDelay = (int)(sv(mx, cx, cw) * 500); return true; }
                }
                case "NightVision" -> {
                    if (hovD(mx, my, (double)cx, (double)cy, cw, 22)) {
                        nightVisionActive = !nightVisionActive;
                        if (!nightVisionActive) client.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
                        bar(client, nightVisionActive ? "§aGece Görüşü §7Açık" : "§cGece Görüşü §7Kapalı");
                        return true;
                    }
                }
                case "HitColor" -> {
                    if (hovD(mx, my, (double)cx, (double)cy, cw, 22)) { cfg.hitColorActive = !cfg.hitColorActive; sc(); OverlayReloadListener.callEvent(); return true; }
                    if (cs(mx, my, cx, cy + 42, cw, 30)) { cfg.hcRed = (int)(sv(mx, cx, cw) * 255); sc(); OverlayReloadListener.callEvent(); return true; }
                    if (cs(mx, my, cx, cy + 70, cw, 31)) { cfg.hcGreen = (int)(sv(mx, cx, cw) * 255); sc(); OverlayReloadListener.callEvent(); return true; }
                    if (cs(mx, my, cx, cy + 98, cw, 32)) { cfg.hcBlue = (int)(sv(mx, cx, cw) * 255); sc(); OverlayReloadListener.callEvent(); return true; }
                    if (cs(mx, my, cx, cy + 126, cw, 33)) { cfg.hcAlpha = (int)(sv(mx, cx, cw) * 255); sc(); OverlayReloadListener.callEvent(); return true; }
                }
                case "Keybinds" -> {
                    if (hovD(mx, my, (double)cx, (double)cy + 16, cw, 22)) { bind = 0; return true; }
                    if (hovD(mx, my, (double)cx, (double)cy + 42, cw, 22)) { bind = 1; return true; }
                    if (hovD(mx, my, (double)cx, (double)cy + 68, cw, 22)) { bind = 2; return true; }
                    if (hovD(mx, my, (double)cx, (double)cy + 94, cw, 22)) { bind = 3; return true; }
                }
            }
            return super.mouseClicked(mx, my, btn);
        }

        @Override
        public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
            if (dragSlot == -1) return super.mouseDragged(mx, my, btn, dx, dy);
            HitXConfig cfg = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();
            float v = sv(mx, dCX, dCW);
            switch (dragSlot) {
                case 0 -> { cfg.xzExpand = 0.5f + v * 4.5f; sc(); }
                case 1 -> { cfg.yExpand = 0.5f + v * 3.5f; sc(); }
                case 2 -> { cfg.yOffset = -1f + v * 2f; sc(); }
                case 10 -> aimRange = 1f + v * 9f;
                case 11 -> aimSpeed = 0.01f + v * 0.49f;
                case 12 -> aimFov = v * 180f;
                case 13 -> aimRecoilStr = v * 2f;
                case 20 -> triggerDelay = (int)(v * 500);
                case 30 -> { cfg.hcRed = (int)(v * 255); sc(); OverlayReloadListener.callEvent(); }
                case 31 -> { cfg.hcGreen = (int)(v * 255); sc(); OverlayReloadListener.callEvent(); }
                case 32 -> { cfg.hcBlue = (int)(v * 255); sc(); OverlayReloadListener.callEvent(); }
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
        public boolean keyPressed(int k, int s, int m) {
            if (bind == 0) { keyHitbox = k; bind = -1; return true; }
            if (bind == 1) { keyAimAssist = k; bind = -1; return true; }
            if (bind == 2) { keyTriggerBot = k; bind = -1; return true; }
            if (bind == 3) { keyNightVision = k; bind = -1; return true; }
            return super.keyPressed(k, s, m);
        }

        @Override public boolean shouldPause() { return false; }

        private boolean cs(double mx, double my, int cx, int sy, int cw, int sid) {
            if (hovD(mx, my, (double)cx, (double)sy - 3, cw, 16)) { 
                dragSlot = sid; dCX = cx; dCW = cw; return true; 
            } 
            return false;
        }
        private float sv(double mx, int cx, int cw) { return MathHelper.clamp((float)((mx - cx) / cw), 0f, 1f); }
        private void sc() { AutoConfig.getConfigHolder(HitXConfig.class).save(); }
        private String kn(int k) { String n = GLFW.glfwGetKeyName(k, 0); return n != null ? n.toUpperCase() : "KEY_" + k; }
        private String f1(float v) { return String.format("%.1f", v); }
        private String f2(float v) { return String.format("%.2f", v); }
    }

    private void iconBtn(Screen s, ItemStack i, String t, int x, int y, int w, int h, ButtonWidget.PressAction p) {}
    private boolean isArmor(ItemStack s) { return s.getItem() instanceof net.minecraft.item.ArmorItem; }
        }
                    
