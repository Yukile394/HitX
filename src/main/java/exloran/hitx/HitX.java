package exloran.hitx;

import com.mojang.blaze3d.systems.RenderSystem;
import exloran.hitx.listener.OverlayReloadListener;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

public class HitX implements ClientModInitializer {

    // ── Modüller ─────────────────────────────────────────────
    public static boolean hitBoxActive      = false;
    public static boolean triggerBotActive  = false;
    public static boolean aimAssistActive   = false;
    public static boolean nightVisionActive = false;
    public static boolean auraActive        = false;
    public static boolean elytraTargetActive= false;
    public static boolean hitColorActive    = false;

    // ── Ayarlar ──────────────────────────────────────────────
    public static float auraRange        = 3.5f;
    public static float auraSpeed        = 0.14f;
    public static boolean auraAutoAttack = true;
    public static float elytraRange      = 7.0f;

    public static float aimRange         = 4.5f;
    public static float aimSpeed         = 0.08f;
    public static float aimFov           = 90f;
    public static boolean aimElytra      = true;
    public static float aimElytraRange   = 6.0f;

    public static int triggerDelay       = 50;
    public static boolean trigBlkShield  = true;
    public static boolean trigBlkGap     = true;
    public static boolean trigBlkSelf    = true;

    // HitColor
    public static int hitColorR = 255, hitColorG = 50, hitColorB = 50, hitColorA = 200;

    // Gece görüşü süresi: 70 dk = 84000 tick
    private static final int NV_DURATION = 84000;

    // ── Tuşlar ───────────────────────────────────────────────
    public static int keyAura        = GLFW.GLFW_KEY_R;
    public static int keyAimAssist   = GLFW.GLFW_KEY_J;
    public static int keyTriggerBot  = GLFW.GLFW_KEY_K;
    public static int keyNightVision = GLFW.GLFW_KEY_N;
    public static int keyHitbox      = GLFW.GLFW_KEY_H;

    // ── İç durum ─────────────────────────────────────────────
    private boolean mLast, kAuraLast, kAimLast, kTrigLast, kNvLast, kHbLast;
    private long lastAttack = 0L, nvTick = 0L;

    public static LivingEntity auraLocked = null;
    private LivingEntity aimLocked = null;

    // Smooth aim buffers
    private float auraYaw=0,auraPitch=0;
    private final float[] aYaw=new float[4], aPitch=new float[4];
    private int aBuf=0;
    private float aimYaw=0,aimPitch=0;
    private final float[] iYaw=new float[6], iPitch=new float[6];
    private int iBuf=0;

    // ══════════════════════════════════════════════════════════
    @Override
    public void onInitializeClient() {
        AutoConfig.register(HitXConfig.class, GsonConfigSerializer::new);
        ClientTickEvents.END_WORLD_TICK.register(w -> OverlayReloadListener.callEvent());

        // 3D Neon kutu renderer
        NeonBoxRenderer.register();

        // HUD
        HudRenderCallback.EVENT.register((ctx, tick) -> {
            MinecraftClient c = MinecraftClient.getInstance();
            if (c.player == null || c.world == null) return;
            if (c.getDebugHud().shouldShowDebugHud()) return;
            renderHUD(c, ctx);
        });

        // Ana tick
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            long h = client.getWindow().getHandle();

            // Menü aç
            boolean mNow = GLFW.glfwGetKey(h, GLFW.GLFW_KEY_M) == GLFW.GLFW_PRESS;
            if (mNow && !mLast) client.setScreen(new HitXMenu());
            mLast = mNow;

            if (client.currentScreen == null) {
                boolean kAura = GLFW.glfwGetKey(h, keyAura)        == GLFW.GLFW_PRESS;
                boolean kAim  = GLFW.glfwGetKey(h, keyAimAssist)   == GLFW.GLFW_PRESS;
                boolean kTrig = GLFW.glfwGetKey(h, keyTriggerBot)  == GLFW.GLFW_PRESS;
                boolean kNv   = GLFW.glfwGetKey(h, keyNightVision) == GLFW.GLFW_PRESS;
                boolean kHb   = GLFW.glfwGetKey(h, keyHitbox)      == GLFW.GLFW_PRESS;

                if (kAura && !kAuraLast) { auraActive=!auraActive; auraLocked=null; bar(client, auraActive?"§aAura":"§cAura"); }
                if (kAim  && !kAimLast)  { aimAssistActive=!aimAssistActive; aimLocked=null; bar(client, aimAssistActive?"§aAimAssist":"§cAimAssist"); }
                if (kTrig && !kTrigLast) { triggerBotActive=!triggerBotActive; bar(client, triggerBotActive?"§aTriggerBot":"§cTriggerBot"); }
                if (kNv   && !kNvLast)   { nightVisionActive=!nightVisionActive; if(!nightVisionActive) client.player.removeStatusEffect(StatusEffects.NIGHT_VISION); bar(client, nightVisionActive?"§aGece Gorusu":"§cGece Gorusu"); }
                if (kHb   && !kHbLast)   { hitBoxActive=!hitBoxActive; bar(client, hitBoxActive?"§aHitbox":"§cHitbox"); }

                kAuraLast=kAura; kAimLast=kAim; kTrigLast=kTrig; kNvLast=kNv; kHbLast=kHb;
            }

            // Hitbox genişlet
            if (hitBoxActive) {
                HitXConfig cfg = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();
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

            // Gece görüşü — 70 dakika sürer
            if (nightVisionActive) {
                nvTick++;
                if (nvTick % 60 == 0) {
                    StatusEffectInstance cur = client.player.getStatusEffect(StatusEffects.NIGHT_VISION);
                    if (cur == null || cur.getDuration() < 200)
                        client.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, NV_DURATION, 0, false, false));
                }
            }

            handleAura(client);
            handleAimAndTrigger(client);
        });
    }

    // ══════════════════════════════════════════════════════════
    //  HUD — Sol üst, CatLean tarzı
    // ══════════════════════════════════════════════════════════
    private void renderHUD(MinecraftClient c, DrawContext ctx) {
        if (c.options.hudHidden) return;
        int x = 4, y = 8;
        hudLine(ctx, c, x, y, "Offhand  I", true);          y += 11;
        hudLine(ctx, c, x, y, "Aura  R",    auraActive);    y += 11;
        if (aimAssistActive)   { hudLine(ctx,c,x,y,"AimAssist",true);   y+=11; }
        if (triggerBotActive)  { hudLine(ctx,c,x,y,"TriggerBot",true);  y+=11; }
        if (nightVisionActive) { hudLine(ctx,c,x,y,"Night Vision",true);y+=11; }
        if (hitBoxActive)      { hudLine(ctx,c,x,y,"Hitbox",true);      y+=11; }
        if (elytraTargetActive){ hudLine(ctx,c,x,y,"ElytraTarget",true);y+=11; }
        if (hitColorActive)    { hudLine(ctx,c,x,y,"HitColor",true);    y+=11; }
        LivingEntity t = auraLocked != null ? auraLocked : aimLocked;
        if (t != null && t.isAlive())
            ctx.drawTextWithShadow(c.textRenderer,
                "§7> §f" + t.getName().getString() + "  §c" + (int)t.getHealth() + "❤",
                x, y + 2, 0xFFFFFFFF);
    }

    private void hudLine(DrawContext ctx, MinecraftClient c, int x, int y, String name, boolean on) {
        ctx.fill(x, y, x+1, y+9, on ? 0xFF00CCFF : 0xFF333344);
        ctx.drawTextWithShadow(c.textRenderer, name, x+4, y, on ? 0xFFAAEEFF : 0xFF666677);
    }

    // ══════════════════════════════════════════════════════════
    //  AURA + ELYTRA TARGET
    // ══════════════════════════════════════════════════════════
    private void handleAura(MinecraftClient c) {
        boolean em = elytraTargetActive && c.player.isFallFlying();
        if (!auraActive && !em) { auraLocked = null; return; }
        float range = em ? elytraRange : auraRange;

        if (auraLocked != null && (!auraLocked.isAlive() || c.player.distanceTo(auraLocked) > range + 1.5f))
            auraLocked = null;

        if (auraLocked == null) {
            double best = Double.MAX_VALUE;
            for (Entity e : c.world.getEntities()) {
                if (!(e instanceof LivingEntity le) || le == c.player || !le.isAlive()) continue;
                double d = c.player.distanceTo(le);
                if (d > range) continue;
                double sc = angle(c,le)*0.45 + d*0.55;
                if (sc < best) { best=sc; auraLocked=le; }
            }
        }
        if (auraLocked == null) return;

        smoothAim(c, auraLocked, true);

        if (!auraAutoAttack) return;
        if (trigBlkShield && auraLocked.isBlocking()) return;
        if (trigBlkGap    && isEating(auraLocked)) return;
        if (trigBlkSelf   && c.player.isBlocking()) return;
        if (c.player.getAttackCooldownProgress(0.5f) >= 1.0f) {
            long now = System.currentTimeMillis();
            if (now - lastAttack >= triggerDelay) {
                c.interactionManager.attackEntity(c.player, auraLocked);
                c.player.swingHand(Hand.MAIN_HAND);
                lastAttack = now;
            }
        }
    }

    // ══════════════════════════════════════════════════════════
    //  AIMASSIST + TRIGGERBOT
    // ══════════════════════════════════════════════════════════
    private void handleAimAndTrigger(MinecraftClient c) {
        // AimAssist hedef
        if (aimAssistActive) {
            if (aimLocked != null && (!aimLocked.isAlive() || c.player.distanceTo(aimLocked) > aimRange + 2f))
                aimLocked = null;
            if (aimLocked == null) {
                float maxD = (aimElytra && c.player.isFallFlying()) ? aimElytraRange : aimRange;
                double best = Double.MAX_VALUE;
                for (Entity e : c.world.getEntities()) {
                    if (!(e instanceof LivingEntity le) || le==c.player || !le.isAlive()) continue;
                    double d = c.player.distanceTo(le); if (d > maxD) continue;
                    float ang = angle(c, le); if (ang > aimFov) continue;
                    double sc = ang*0.6 + d*0.4;
                    if (sc < best) { best=sc; aimLocked=le; }
                }
            }
            if (aimLocked != null) smoothAim(c, aimLocked, false);
        } else { aimLocked = null; }

        // TriggerBot
        if (!triggerBotActive) return;
        LivingEntity atk = null;
        if (c.crosshairTarget instanceof net.minecraft.util.hit.EntityHitResult hr
                && hr.getEntity() instanceof LivingEntity le && le.isAlive())
            atk = le;
        if (atk == null) return;
        if (trigBlkShield && atk.isBlocking()) return;
        if (trigBlkGap    && isEating(atk)) return;
        if (trigBlkSelf   && c.player.isBlocking()) return;
        if (c.player.getAttackCooldownProgress(0.5f) < 1.0f) return;
        long now = System.currentTimeMillis();
        if (now - lastAttack < triggerDelay) return;
        c.interactionManager.attackEntity(c.player, atk);
        c.player.swingHand(Hand.MAIN_HAND);
        lastAttack = now;
    }

    // ══════════════════════════════════════════════════════════
    //  SMOOTH AIM — GCD fix + velocity prediction + buffer
    // ══════════════════════════════════════════════════════════
    private void smoothAim(MinecraftClient c, LivingEntity tgt, boolean isAura) {
        double vx=tgt.getX()-tgt.prevX, vy=tgt.getY()-tgt.prevY, vz=tgt.getZ()-tgt.prevZ;
        double lead = isAura ? 1.8 : 2.2;
        double tx=tgt.getX()+vx*lead, tz=tgt.getZ()+vz*lead;
        double ty=tgt.getY()+vy*0.25+tgt.getEyeHeight(tgt.getPose())*(isAura?0.72:0.82);
        double dx=tx-c.player.getX(), dz=tz-c.player.getZ();
        double dy=ty-(c.player.getY()+c.player.getEyeHeight(c.player.getPose()));
        double hD=Math.sqrt(dx*dx+dz*dz);
        float tY=MathHelper.wrapDegrees((float)Math.toDegrees(Math.atan2(dz,dx))-90f);
        float tP=(float)-Math.toDegrees(Math.atan2(dy,hD));
        float dY=MathHelper.wrapDegrees(tY-c.player.getYaw());
        float dP=MathHelper.wrapDegrees(tP-c.player.getPitch());
        float spd = isAura ? auraSpeed : aimSpeed;
        float dF=MathHelper.clamp(c.player.distanceTo(tgt)/(isAura?auraRange:aimRange),0.2f,1f);
        float aF=MathHelper.clamp(angle(c,tgt)/12f,0.1f,1f);
        float ds=spd*dF*aF;
        double sens=c.options.getMouseSensitivity().getValue();
        double gcd=Math.pow(sens*0.6+0.2,3.0)*8.0*0.15; if(gcd<0.001)gcd=0.001;
        if (isAura) {
            auraYaw  +=(dY-auraYaw)  *ds*3f;
            auraPitch+=(dP-auraPitch)*ds*3f;
            aYaw[aBuf]=auraYaw; aPitch[aBuf]=auraPitch; aBuf=(aBuf+1)%4;
            float ay=0,ap=0; for(int i=0;i<4;i++){ay+=aYaw[i];ap+=aPitch[i];} ay/=4;ap/=4;
            c.player.setYaw(c.player.getYaw()+(float)(Math.round(ay/gcd)*gcd));
            c.player.setPitch(MathHelper.clamp(c.player.getPitch()+(float)(Math.round(ap/gcd)*gcd),-90,90));
        } else {
            aimYaw  +=(dY-aimYaw)  *ds*2.5f;
            aimPitch+=(dP-aimPitch)*ds*2.5f;
            iYaw[iBuf]=aimYaw; iPitch[iBuf]=aimPitch; iBuf=(iBuf+1)%6;
            float ay=0,ap=0; for(int i=0;i<6;i++){ay+=iYaw[i];ap+=iPitch[i];} ay/=6;ap/=6;
            c.player.setYaw(c.player.getYaw()+(float)(Math.round(ay/gcd)*gcd));
            c.player.setPitch(MathHelper.clamp(c.player.getPitch()+(float)(Math.round(ap/gcd)*gcd),-90,90));
        }
    }

    // ══════════════════════════════════════════════════════════
    //  ROUNDED RECT ─ Menü için
    // ══════════════════════════════════════════════════════════
    public static void fillRound(MatrixStack ms, float x, float y, float w, float h, float r, int color) {
        float a=((color>>24)&0xFF)/255f, rv=((color>>16)&0xFF)/255f,
              g=((color>>8)&0xFF)/255f,  b=(color&0xFF)/255f;
        if (a <= 0) return;
        r = Math.min(r, Math.min(w,h)*0.499f);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        Matrix4f m4 = ms.peek().getPositionMatrix();
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
        buf.vertex(m4, x+w/2f, y+h/2f, 0).color(rv,g,b,a);
        float[] cx={x+w-r,x+r,x+r,x+w-r}, cy={y+r,y+r,y+h-r,y+h-r}, sa={270f,180f,90f,0f};
        for (int i=0;i<4;i++) for (int j=0;j<=12;j++) {
            double ang=Math.toRadians(sa[i]+j*7.5);
            buf.vertex(m4,(float)(cx[i]+Math.cos(ang)*r),(float)(cy[i]+Math.sin(ang)*r),0).color(rv,g,b,a);
        }
        double ca=Math.toRadians(sa[0]);
        buf.vertex(m4,(float)(cx[0]+Math.cos(ca)*r),(float)(cy[0]+Math.sin(ca)*r),0).color(rv,g,b,a);
        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.disableBlend();
    }

    public static void outlineRound(MatrixStack ms, float x, float y, float w, float h, float r, int color) {
        float a=((color>>24)&0xFF)/255f, rv=((color>>16)&0xFF)/255f,
              g=((color>>8)&0xFF)/255f,  b=(color&0xFF)/255f;
        if (a <= 0) return;
        r = Math.min(r, Math.min(w,h)*0.499f);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.lineWidth(1.2f);
        Matrix4f m4 = ms.peek().getPositionMatrix();
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);
        float[] cx={x+w-r,x+r,x+r,x+w-r}, cy={y+r,y+r,y+h-r,y+h-r}, sa={270f,180f,90f,0f};
        for (int i=0;i<4;i++) for (int j=0;j<=12;j++) {
            double ang=Math.toRadians(sa[i]+j*7.5);
            buf.vertex(m4,(float)(cx[i]+Math.cos(ang)*r),(float)(cy[i]+Math.sin(ang)*r),0).color(rv,g,b,a);
        }
        double ca=Math.toRadians(sa[0]);
        buf.vertex(m4,(float)(cx[0]+Math.cos(ca)*r),(float)(cy[0]+Math.sin(ca)*r),0).color(rv,g,b,a);
        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.disableBlend();
    }

    // ══════════════════════════════════════════════════════════
    //  MENÜ — Fotoğraftaki gibi 4 sütun CatLean tarzı
    // ══════════════════════════════════════════════════════════
    public class HitXMenu extends Screen {

        private static final int MW = 720, MH = 430;
        private float anim = 0f;

        protected HitXMenu() { super(Text.literal("HitX")); }

        @Override public void tick() { super.tick(); anim += 0.04f; }
        @Override public boolean shouldPause() { return false; }

        @Override
        public void render(DrawContext ctx, int mx, int my, float delta) {
            MatrixStack ms = ctx.getMatrices();
            int ox = ox(), oy = oy();

            // Arka plan
            ctx.fill(0, 0, width, height, 0x66000000);

            // Ana panel
            fillRound(ms, ox, oy, MW, MH, 6f, 0xF0101015);
            outlineRound(ms, ox, oy, MW, MH, 6f, 0xFF1C1C2A);

            // Üst şerit
            fillRound(ms, ox, oy, MW, 22, 6f, 0xFF0C0C16);
            ctx.fill(ox, oy+11, ox+MW, oy+22, 0xFF0C0C16);

            // Üst sekmeler — Ui / Windows / Hud / Theme / Search
            String[] top = {"Ui","Windows","Hud","Theme","Search"};
            int ttx = ox + MW/2 - (top.length*52)/2;
            for (String t : top) {
                boolean hov = hov(mx,my,ttx,oy+2,50,16);
                ctx.drawCenteredTextWithShadow(textRenderer, t, ttx+25, oy+7,
                    hov ? 0xFFCCAAFF : 0xFF444455);
                if (hov) ctx.fill(ttx+8, oy+18, ttx+42, oy+20, 0xFF7744CC);
                ttx += 52;
            }

            // 4 Sütun
            int cW = (MW-10)/4, cH = MH-26;
            col(ctx,ms,mx,my, ox+2,          oy+24, "Pvp",      new String[]{"Attack","Legit","Protect"}, pvp(),  0, cW, cH);
            col(ctx,ms,mx,my, ox+3+cW,       oy+24, "Movement", new String[]{"Basic","Rage"},             mov(),  1, cW, cH);
            col(ctx,ms,mx,my, ox+4+cW*2,     oy+24, "Visual",   new String[]{"Cosmetic","Esp"},           vis(),  2, cW, cH);
            col(ctx,ms,mx,my, ox+5+cW*3,     oy+24, "Utility",  new String[]{"World","Equip","Player","Misc"}, util(), 3, cW, cH);

            // Sağ alt Theme Editor (fotoğraftaki gibi)
            int tex=ox+MW-88, tey=oy+MH-64;
            fillRound(ms,tex,tey,84,60,4f,0xFF0E0E18);
            outlineRound(ms,tex,tey,84,60,4f,0xFF252535);
            ctx.fill(tex+4,tey+4,tex+14,tey+14,0xFF2A2A3A);
            ctx.drawTextWithShadow(textRenderer,"§7Theme Editor",tex+18,tey+7,0xFF666677);
            ctx.drawTextWithShadow(textRenderer,"§8Enter name",tex+5,tey+20,0xFF333344);
            ctx.fill(tex+62,tey+19,tex+64,tey+29,0xFF5533AA);
            fillRound(ms,tex+3,tey+32,78,12,3f,0xFF180030);
            ctx.drawCenteredTextWithShadow(textRenderer,"§dCatLean",tex+42,tey+34,0xFFCC88FF);
            fillRound(ms,tex+3,tey+46,78,12,3f,0xFF001828);
            ctx.drawCenteredTextWithShadow(textRenderer,"§bStyleNew",tex+42,tey+48,0xFF88CCFF);

            // Alt bilgi
            ctx.drawTextWithShadow(textRenderer,"§820.04 (20.0) t/s",ox+3,oy+MH-10,0xFF2A2A3A);
            ctx.drawTextWithShadow(textRenderer,"§8CatLean public beta three",ox+MW-176,oy+MH-10,0xFF2A2A3A);

            super.render(ctx,mx,my,delta);
        }

        // ── Sütun ────────────────────────────────────────────
        private void col(DrawContext ctx, MatrixStack ms, int mx, int my,
                         int x, int y, String title, String[] tabs,
                         String[] mods, int ci, int cW, int cH) {
            fillRound(ms,x,y,cW-1,cH,4f,0xFF0C0C12);
            outlineRound(ms,x,y,cW-1,cH,4f,0xFF181826);
            int[] titleCols = {0xFFAA66FF, 0xFF55AAFF, 0xFF66FFAA, 0xFFFFAA44};
            ctx.drawCenteredTextWithShadow(textRenderer,title,x+cW/2,y+5,titleCols[ci]);

            // Sekmeler
            int tabW=(cW-8)/tabs.length, tx=x+4;
            for (String t : tabs) {
                boolean hov=hov(mx,my,tx,y+17,tabW,13);
                fillRound(ms,tx,y+17,tabW-1,13,3f,hov?0xFF1E003A:0xFF0E0E1C);
                ctx.drawCenteredTextWithShadow(textRenderer,t,tx+(tabW-1)/2,y+20,hov?0xFFBB88FF:0xFF3A3A4A);
                tx += tabW;
            }

            // Modüller
            int my2 = y+33;
            for (String mod : mods) {
                boolean on  = isOn(mod);
                boolean hov = hov(mx,my,x+2,my2,cW-4,15);
                if (on)  fillRound(ms,x+2,my2,cW-4,15,2f,0xFF160026);
                else if (hov) fillRound(ms,x+2,my2,cW-4,15,2f,0xFF141420);
                // Sol aktif çizgisi
                if (on) ctx.fill(x+2,my2,x+3,my2+15,titleCols[ci]);
                ctx.drawTextWithShadow(textRenderer,mod,x+7,my2+4,
                    on?0xFFEEDDFF:(hov?0xFFAAAAAA:0xFF444455));
                if (on) ctx.drawTextWithShadow(textRenderer,"§a●",x+cW-14,my2+4,0xFFFFFFFF);
                my2 += 16;
                if (my2 > y+cH-4) break;
            }
        }

        // ── Modül listeleri — SADECE aktif modüller ──────────
        private String[] pvp()  { return new String[]{"Aura","AimAssist","TriggerBot","Hitboxes","ElytraTarget"}; }
        private String[] mov()  { return new String[]{"Anti Knock Back","Avoid","Elytra Recast","Gui Move","Hole Anchor","Infinite Elytra","Legit Strafe","Move Fix","No Push","Sprint","Wind Hop"}; }
        private String[] vis()  { return new String[]{"HitColor","Night Vision","Absorption","Aspect","Buff Effect","Crystal Chams","Damage Particles","Damage Tint","Dismemberment","Free Look","Full Bright","Hand Chams","Hit Fx","No Camera Clip","Pop Chams","Sky Lanterns","Swing Animations","Totem Animation","Trajectories"}; }
        private String[] util() { return new String[]{"Auto Bone Meal","Auto Land","Auto Shear","Auto Sign","Auto Wither","Fake Player","Free Cam","No Render","Nuker","Pearl Chaser","Server Helper","Soil Ripper","Tps Sync","Zoom"}; }

        private boolean isOn(String n) {
            return switch(n) {
                case "Aura"           -> auraActive;
                case "AimAssist"      -> aimAssistActive;
                case "TriggerBot"     -> triggerBotActive;
                case "Hitboxes"       -> hitBoxActive;
                case "ElytraTarget"   -> elytraTargetActive;
                case "HitColor"       -> hitColorActive;
                case "Night Vision"   -> nightVisionActive;
                default -> false;
            };
        }

        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            int ox=ox(), oy=oy(), cW=(MW-10)/4;
            int[] startX = {ox+2, ox+3+cW, ox+4+cW*2, ox+5+cW*3};
            String[][] all = {pvp(), mov(), vis(), util()};
            for (int ci=0; ci<4; ci++) {
                int x=startX[ci], y=oy+24+33, cw=cW-4;
                for (String mod : all[ci]) {
                    if (hovD(mx,my,x+2,y,cw,15)) { toggle(mod); return true; }
                    y += 16;
                    if (y > oy+24+(MH-26)-4) break;
                }
            }
            return super.mouseClicked(mx,my,btn);
        }

        private void toggle(String n) {
            MinecraftClient c = MinecraftClient.getInstance();
            switch(n) {
                case "Aura"         -> { auraActive=!auraActive; auraLocked=null; bar(c,auraActive?"§aAura":"§cAura"); }
                case "AimAssist"    -> { aimAssistActive=!aimAssistActive; aimLocked=null; bar(c,aimAssistActive?"§aAimAssist":"§cAimAssist"); }
                case "TriggerBot"   -> triggerBotActive  = !triggerBotActive;
                case "Hitboxes"     -> hitBoxActive       = !hitBoxActive;
                case "ElytraTarget" -> elytraTargetActive = !elytraTargetActive;
                case "HitColor"     -> hitColorActive     = !hitColorActive;
                case "Night Vision" -> { nightVisionActive=!nightVisionActive; if(!nightVisionActive&&c.player!=null) c.player.removeStatusEffect(StatusEffects.NIGHT_VISION); }
            }
        }

        private int ox() { return width/2-MW/2; }
        private int oy() { return height/2-MH/2; }
        private boolean hov(int mx,int my,int x,int y,int w,int h){ return mx>=x&&mx<=x+w&&my>=y&&my<=y+h; }
        private boolean hovD(double mx,double my,double x,double y,double w,double h){ return mx>=x&&mx<=x+w&&my>=y&&my<=y+h; }
    }

    // ── Yardımcılar ──────────────────────────────────────────
    private float angle(MinecraftClient c, LivingEntity t) {
        Vec3d look = c.player.getRotationVec(1f);
        Vec3d toT  = t.getEyePos().subtract(c.player.getEyePos()).normalize();
        return (float)Math.toDegrees(Math.acos(MathHelper.clamp(look.dotProduct(toT),-1.0,1.0)));
    }

    private boolean isEating(LivingEntity e) {
        if (e==null || !e.isUsingItem()) return false;
        ItemStack u = e.getActiveItem();
        if (u.isEmpty()) return false;
        return u.getComponents().contains(net.minecraft.component.DataComponentTypes.FOOD)
            || u.getItem()==Items.MILK_BUCKET
            || u.getItem()==Items.POTION
            || u.getItem()==Items.SPLASH_POTION
            || u.getItem()==Items.LINGERING_POTION;
    }

    private void bar(MinecraftClient c, String m) {
        if (c.player != null)
            c.player.sendMessage(Text.literal("§8[§dHitX§8] §r" + m), true);
    }
}
