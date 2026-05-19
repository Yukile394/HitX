package exloran.hitx;

import com.mojang.blaze3d.systems.RenderSystem;
import exloran.hitx.listener.OverlayReloadListener;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class HitX implements ClientModInitializer {

    // ── Modüller ─────────────────────────────────────────────
    public static boolean hitBoxActive     = false;
    public static boolean triggerBotActive = false;
    public static boolean hitColorActive   = false;

    // ── HitColor ─────────────────────────────────────────────
    public static float hitColorR = 1.0f, hitColorG = 0.08f, hitColorB = 0.08f;

    // ── Hitbox boyut ─────────────────────────────────────────
    public static float hitboxXZ = 1.0f;
    public static float hitboxY  = 1.0f;

    // ── TriggerBot ───────────────────────────────────────────
    public static int     triggerDelay  = 50;
    public static boolean trigBlkShield = true;
    public static boolean trigBlkGap    = true;

    // ══════════════════════════════════════════════════════════
    //  Trail (yürüme izi) veri sınıfı
    // ══════════════════════════════════════════════════════════
    public static class TrailPoint {
        public double x, y, z;
        public int    age;
        public float  r, g, b;
        public TrailPoint(double x, double y, double z, float r, float g, float b) {
            this.x=x; this.y=y; this.z=z; this.r=r; this.g=g; this.b=b;
        }
    }
    public static final List<TrailPoint> trail      = new ArrayList<>();
    public static final int              TRAIL_LIFE  = 22;

    // ══════════════════════════════════════════════════════════
    //  Jump Ring veri sınıfı
    // ══════════════════════════════════════════════════════════
    public static class JumpRing {
        public double x, y, z;
        public float  radius;
        public int    age;
        public JumpRing(double x, double y, double z) {
            this.x=x; this.y=y; this.z=z; this.radius=0.05f;
        }
    }
    public static final List<JumpRing> jumpRings    = new ArrayList<>();
    public static final int            JUMP_RING_LIFE = 22; // biraz uzun yaşasın

    // ══════════════════════════════════════════════════════════
    //  Hit Ring veri sınıfı — vururken zemin halkası
    // ══════════════════════════════════════════════════════════
    public static class HitRing {
        public double x, y, z;
        public float  radius;
        public int    age;
        public HitRing(double x, double y, double z) {
            this.x=x; this.y=y; this.z=z; this.radius=0.1f;
        }
    }
    public static final List<HitRing> hitRings    = new ArrayList<>();
    public static final int           HIT_RING_LIFE = 14; // hızlı solar

    // ── Trail hue (otomatik renk döngüsü) ───────────────────
    private static float trailHue = 0f;

    // ── İç durum ─────────────────────────────────────────────
    private boolean mLast, kTrigLast, kHbLast;
    private long    lastAttack   = 0L;
    private boolean wasOnGround  = true;

    // ══════════════════════════════════════════════════════════
    //  HSV → RGB
    // ══════════════════════════════════════════════════════════
    public static float[] hsv(float h, float s, float v) {
        int i = (int)(h * 6) % 6;
        float f = h * 6 - (int)(h * 6);
        float p = v*(1-s), q = v*(1-f*s), t = v*(1-(1-f)*s);
        return switch(i) {
            case 0 -> new float[]{v,t,p};
            case 1 -> new float[]{q,v,p};
            case 2 -> new float[]{p,v,t};
            case 3 -> new float[]{p,q,v};
            case 4 -> new float[]{t,p,v};
            default -> new float[]{v,p,q};
        };
    }

    // ══════════════════════════════════════════════════════════
    @Override
    public void onInitializeClient() {
        AutoConfig.register(HitXConfig.class, GsonConfigSerializer::new);
        ClientTickEvents.END_WORLD_TICK.register(w -> OverlayReloadListener.callEvent());

        NeonBoxRenderer.register();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            long hw = client.getWindow().getHandle();

            // M → menü
            boolean mNow = GLFW.glfwGetKey(hw, GLFW.GLFW_KEY_M) == GLFW.GLFW_PRESS;
            if (mNow && !mLast) client.setScreen(new HitXMenu());
            mLast = mNow;

            if (client.currentScreen == null) {
                boolean kT = GLFW.glfwGetKey(hw, GLFW.GLFW_KEY_K) == GLFW.GLFW_PRESS;
                boolean kH = GLFW.glfwGetKey(hw, GLFW.GLFW_KEY_H) == GLFW.GLFW_PRESS;
                if (kT && !kTrigLast) { triggerBotActive=!triggerBotActive; bar(client, triggerBotActive?"§aTriggerBot":"§cTriggerBot"); }
                if (kH && !kHbLast)   { hitBoxActive=!hitBoxActive; bar(client, hitBoxActive?"§aHitbox":"§cHitbox"); }
                kTrigLast=kT; kHbLast=kH;
            }

            // Hitbox genişlet
            if (hitBoxActive) {
                for (Entity e : client.world.getEntities()) {
                    if (e instanceof LivingEntity le && le != client.player) {
                        float hxz = (0.6f * hitboxXZ) / 2f;
                        float hy2 = 1.8f * hitboxY;
                        le.setBoundingBox(new Box(
                            le.getX()-hxz, le.getY(), le.getZ()-hxz,
                            le.getX()+hxz, le.getY()+hy2, le.getZ()+hxz));
                    }
                }
            }

            // Trail — otomatik renk + hareket ederken iz bırak
            trailHue = (trailHue + 0.010f) % 1.0f;
            float[] rgb = hsv(trailHue, 1.0f, 1.0f);
            boolean moving = client.player.getVelocity().horizontalLength() > 0.04;
            if (moving && client.player.age % 2 == 0) {
                trail.add(new TrailPoint(
                    client.player.getX(),
                    client.player.getY() + 0.03,
                    client.player.getZ(),
                    rgb[0], rgb[1], rgb[2]));
            }
            Iterator<TrailPoint> it = trail.iterator();
            while (it.hasNext()) { TrailPoint tp = it.next(); tp.age++; if (tp.age > TRAIL_LIFE) it.remove(); }

            // Jump Ring — zıplayınca çıksın (TAM DAİRE, KALIN)
            boolean onGround = client.player.isOnGround();
            if (!onGround && wasOnGround) {
                jumpRings.add(new JumpRing(
                    client.player.getX(), client.player.getY(), client.player.getZ()));
            }
            wasOnGround = onGround;
            Iterator<JumpRing> ji = jumpRings.iterator();
            while (ji.hasNext()) {
                JumpRing jr = ji.next();
                jr.age++;
                // Daha hızlı büyüsün, daha geniş → fotoğraftaki kadar kalın görünsün
                jr.radius = 0.15f + (jr.age / (float)JUMP_RING_LIFE) * 2.8f;
                if (jr.age > JUMP_RING_LIFE) ji.remove();
            }

            // Hit Ring — tick'te güncelle + yaşlandır
            Iterator<HitRing> hi = hitRings.iterator();
            while (hi.hasNext()) {
                HitRing hr = hi.next();
                hr.age++;
                // Hızla genişle, hortum gibi dışa doğru
                hr.radius = 0.1f + (hr.age / (float)HIT_RING_LIFE) * 1.6f;
                if (hr.age > HIT_RING_LIFE) hi.remove();
            }

            handleTrigger(client);
        });
    }

    // ── TriggerBot ───────────────────────────────────────────
    private void handleTrigger(MinecraftClient c) {
        if (!triggerBotActive) return;
        LivingEntity atk = null;
        if (c.crosshairTarget instanceof net.minecraft.util.hit.EntityHitResult hr
                && hr.getEntity() instanceof LivingEntity le && le.isAlive())
            atk = le;
        if (atk == null) return;
        if (trigBlkShield && atk.isBlocking()) return;
        if (trigBlkGap    && isEating(atk)) return;
        if (c.player.isBlocking()) return;
        if (c.player.getAttackCooldownProgress(0.5f) < 1.0f) return;
        long now = System.currentTimeMillis();
        if (now - lastAttack < triggerDelay) return;

        // ── HIT RING SPAWN: vururken entity ayağında tam daire ──
        hitRings.add(new HitRing(atk.getX(), atk.getY(), atk.getZ()));

        c.interactionManager.attackEntity(c.player, atk);
        c.player.swingHand(Hand.MAIN_HAND);
        lastAttack = now;
    }

    // ══════════════════════════════════════════════════════════
    //  Rounded rect helpers
    // ══════════════════════════════════════════════════════════
    public static void fillRound(MatrixStack ms, float x, float y, float w, float h, float r, int color) {
        float a=((color>>24)&0xFF)/255f, rv=((color>>16)&0xFF)/255f,
              gv=((color>>8)&0xFF)/255f, b=(color&0xFF)/255f;
        if (a<=0) return;
        r = Math.min(r, Math.min(w,h)*0.499f);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        Matrix4f m4 = ms.peek().getPositionMatrix();
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
        buf.vertex(m4, x+w/2f, y+h/2f, 0).color(rv,gv,b,a);
        float[] cx={x+w-r,x+r,x+r,x+w-r}, cy={y+r,y+r,y+h-r,y+h-r}, sa={270f,180f,90f,0f};
        for (int i=0;i<4;i++) for (int j=0;j<=12;j++) {
            double ang=Math.toRadians(sa[i]+j*7.5);
            buf.vertex(m4,(float)(cx[i]+Math.cos(ang)*r),(float)(cy[i]+Math.sin(ang)*r),0).color(rv,gv,b,a);
        }
        double ca=Math.toRadians(sa[0]);
        buf.vertex(m4,(float)(cx[0]+Math.cos(ca)*r),(float)(cy[0]+Math.sin(ca)*r),0).color(rv,gv,b,a);
        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.disableBlend();
    }

    public static void outlineRound(MatrixStack ms, float x, float y, float w, float h, float r, int color) {
        float a=((color>>24)&0xFF)/255f, rv=((color>>16)&0xFF)/255f,
              gv=((color>>8)&0xFF)/255f, b=(color&0xFF)/255f;
        if (a<=0) return;
        r = Math.min(r, Math.min(w,h)*0.499f);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.lineWidth(1.3f);
        Matrix4f m4 = ms.peek().getPositionMatrix();
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);
        float[] cx={x+w-r,x+r,x+r,x+w-r}, cy={y+r,y+r,y+h-r,y+h-r}, sa={270f,180f,90f,0f};
        for (int i=0;i<4;i++) for (int j=0;j<=12;j++) {
            double ang=Math.toRadians(sa[i]+j*7.5);
            buf.vertex(m4,(float)(cx[i]+Math.cos(ang)*r),(float)(cy[i]+Math.sin(ang)*r),0).color(rv,gv,b,a);
        }
        double ca=Math.toRadians(sa[0]);
        buf.vertex(m4,(float)(cx[0]+Math.cos(ca)*r),(float)(cy[0]+Math.sin(ca)*r),0).color(rv,gv,b,a);
        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.disableBlend();
    }

    // ══════════════════════════════════════════════════════════
    //  ANA MENÜ
    // ══════════════════════════════════════════════════════════
    public class HitXMenu extends Screen {

        private static final int MW = 260, MH = 130;
        private static final int SP_W = 230, SP_H = 170;
        private String settingsFor = null;

        protected HitXMenu() { super(Text.literal("HitX")); }
        @Override public boolean shouldPause() { return false; }

        @Override
        public void render(DrawContext ctx, int mx, int my, float delta) {
            MatrixStack ms = ctx.getMatrices();
            int ox = ox(), oy = oy();

            ctx.fill(0, 0, width, height, 0x55000000);
            fillRound(ms, ox, oy, MW, MH, 8f, 0xF2101018);
            outlineRound(ms, ox, oy, MW, MH, 8f, 0xFF282840);
            fillRound(ms, ox, oy, MW, 24, 8f, 0xFF0A0A14);
            ctx.fill(ox, oy+12, ox+MW, oy+24, 0xFF0A0A14);
            ctx.drawCenteredTextWithShadow(textRenderer, "§5◈ §dHitX §5◈", ox+MW/2, oy+7, 0xFFCC88FF);

            String[] mods = {"TriggerBot", "Hitboxes", "HitColor"};
            int row = oy + 32;
            for (String mod : mods) {
                boolean on  = isOn(mod);
                boolean hov = hovI(mx,my, ox+6, row, MW-36, 22);
                boolean hovGear = hovI(mx,my, ox+MW-32, row+4, 26, 14);

                if (on)       fillRound(ms, ox+6, row, MW-36, 22, 5f, 0xFF1E0040);
                else if (hov) fillRound(ms, ox+6, row, MW-36, 22, 5f, 0xFF161628);
                if (on) {
                    ctx.fill(ox+6, row+3, ox+8, row+19, 0xFFBB55FF);
                    ctx.fill(ox+8, row+3, ox+10, row+19, 0x44BB55FF);
                }
                ctx.drawTextWithShadow(textRenderer, mod, ox+14, row+7,
                    on ? 0xFFEEDDFF : (hov ? 0xFFAAA8CC : 0xFF505060));
                ctx.drawTextWithShadow(textRenderer, on?"§a●":"§8●",
                    ox+MW-44, row+7, 0xFFFFFFFF);

                boolean gearActive = settingsFor != null && settingsFor.equals(mod);
                fillRound(ms, ox+MW-32, row+4, 26, 14, 4f,
                    gearActive ? 0xFF3A1A6A : (hovGear ? 0xFF201A38 : 0xFF0E0E1E));
                outlineRound(ms, ox+MW-32, row+4, 26, 14, 4f,
                    gearActive ? 0xFF8844CC : 0xFF333344);
                ctx.drawCenteredTextWithShadow(textRenderer, "§7⚙", ox+MW-32+13, row+4, 0xFF9988BB);

                row += 28;
            }

            ctx.drawTextWithShadow(textRenderer, "§8LClick=toggle  ⚙=settings  M=close",
                ox+5, oy+MH-10, 0xFF242434);

            if (settingsFor != null) renderSettingsPanel(ctx, ms, mx, my);
            super.render(ctx, mx, my, delta);
        }

        private void renderSettingsPanel(DrawContext ctx, MatrixStack ms, int mx, int my) {
            int ox = ox() + MW + 6, oy = oy();
            fillRound(ms, ox, oy, SP_W, SP_H, 8f, 0xF2101018);
            outlineRound(ms, ox, oy, SP_W, SP_H, 8f, 0xFF8844CC);
            fillRound(ms, ox, oy, SP_W, 22, 8f, 0xFF0A0A14);
            ctx.fill(ox, oy+11, ox+SP_W, oy+22, 0xFF0A0A14);
            ctx.drawCenteredTextWithShadow(textRenderer, "§d" + settingsFor + " §8Ayarları",
                ox+SP_W/2, oy+6, 0xFFCC88FF);

            int py = oy + 28;
            switch (settingsFor) {
                case "TriggerBot" -> {
                    py = slider(ctx, ms, mx, my, ox+8, py, SP_W-16, "Gecikme", triggerDelay, 0, 300, "ms");
                    py = checkbox(ctx, ms, mx, my, ox+8, py, "Kalkan Engelle", trigBlkShield);
                    py = checkbox(ctx, ms, mx, my, ox+8, py, "Yemek Engelle",  trigBlkGap);
                }
                case "Hitboxes" -> {
                    py = slider(ctx, ms, mx, my, ox+8, py, SP_W-16, "XZ Büyütme",
                        Math.round(hitboxXZ*10), 5, 30,
                        "x" + String.format("%.1f", hitboxXZ));
                    py = slider(ctx, ms, mx, my, ox+8, py, SP_W-16, "Y  Büyütme",
                        Math.round(hitboxY*10), 5, 30,
                        "x" + String.format("%.1f", hitboxY));
                }
                case "HitColor" -> {
                    int pc = packRgb(hitColorR, hitColorG, hitColorB, 1f);
                    fillRound(ms, ox+8, py, SP_W-16, 18, 4f, pc);
                    outlineRound(ms, ox+8, py, SP_W-16, 18, 4f, 0xFF555566);
                    ctx.drawCenteredTextWithShadow(textRenderer, "Renk Önizleme",
                        ox+SP_W/2, py+5, 0xFFFFFFFF);
                    py += 24;
                    py = slider(ctx, ms, mx, my, ox+8, py, SP_W-16, "§cKırmızı",
                        Math.round(hitColorR*255), 0, 255, "");
                    py = slider(ctx, ms, mx, my, ox+8, py, SP_W-16, "§aYeşil",
                        Math.round(hitColorG*255), 0, 255, "");
                    py = slider(ctx, ms, mx, my, ox+8, py, SP_W-16, "§9Mavi",
                        Math.round(hitColorB*255), 0, 255, "");
                }
            }
        }

        private int slider(DrawContext ctx, MatrixStack ms, int mx, int my,
                           int x, int y, int w, String label, int val, int min, int max, String suf) {
            String disp = label + ": §f" + val + (suf.isEmpty()?"":"§8 "+suf);
            ctx.drawTextWithShadow(textRenderer, disp, x, y, 0xFF9999BB);
            y += 11;
            fillRound(ms, x, y, w, 7, 3f, 0xFF0C0C20);
            float t = (float)(val-min)/(max-min);
            int fw = Math.max(7, (int)(t*w));
            fillRound(ms, x, y, fw, 7, 3f, 0xFF7722CC);
            fillRound(ms, x+fw-5, y-2, 10, 11, 5f, 0xFFDD99FF);
            outlineRound(ms, x+fw-5, y-2, 10, 11, 5f, 0xFF9944EE);
            return y + 20;
        }

        private int checkbox(DrawContext ctx, MatrixStack ms, int mx, int my,
                             int x, int y, String label, boolean val) {
            boolean hov = hovI(mx,my,x,y,14,14);
            fillRound(ms, x, y, 14, 14, 3f, val ? 0xFF8833CC : 0xFF141424);
            outlineRound(ms, x, y, 14, 14, 3f, val ? 0xFFBB55EE : 0xFF444455);
            if (val) ctx.drawTextWithShadow(textRenderer, "§f✔", x+2, y+2, 0xFFFFFFFF);
            ctx.drawTextWithShadow(textRenderer, label, x+18, y+3,
                hov ? 0xFFCCCCDD : 0xFF888899);
            return y + 20;
        }

        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            int ox = ox(), oy = oy();
            String[] mods = {"TriggerBot", "Hitboxes", "HitColor"};
            int row = oy + 32;
            for (String mod : mods) {
                if (btn==0 && hovD(mx,my, ox+6, row, MW-36, 22)) { toggle(mod); return true; }
                if (hovD(mx,my, ox+MW-32, row+4, 26, 14)) {
                    settingsFor = mod.equals(settingsFor) ? null : mod; return true;
                }
                row += 28;
            }
            if (settingsFor != null) {
                int spox = ox+MW+6, spoy = oy;
                handleSettingsClick(mx, my, spox, spoy);
            }
            return super.mouseClicked(mx, my, btn);
        }

        @Override
        public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
            if (settingsFor!=null && btn==0) { handleSettingsClick(mx,my, ox()+MW+6, oy()); return true; }
            return super.mouseDragged(mx,my,btn,dx,dy);
        }

        private void handleSettingsClick(double mx, double my, int ox, int oy) {
            int py = oy + 28;
            switch (settingsFor) {
                case "TriggerBot" -> {
                    int sy = py+11;
                    if (hovD(mx,my, ox+8,sy, SP_W-16,7)) {
                        triggerDelay = (int)MathHelper.clamp(
                            ((mx-(ox+8))/(SP_W-16))*300, 0, 300);
                    }
                    py += 31;
                    if (hovD(mx,my, ox+8,py,14,14)) trigBlkShield=!trigBlkShield;
                    py += 20;
                    if (hovD(mx,my, ox+8,py,14,14)) trigBlkGap=!trigBlkGap;
                }
                case "Hitboxes" -> {
                    int sy = py+11;
                    if (hovD(mx,my, ox+8,sy, SP_W-16,7)) {
                        hitboxXZ = MathHelper.clamp(
                            (float)((mx-(ox+8))/(SP_W-16)) * 2.5f + 0.5f, 0.5f, 3.0f);
                        hitboxXZ = Math.round(hitboxXZ*10)/10f;
                    }
                    py += 31;
                    sy = py+11;
                    if (hovD(mx,my, ox+8,sy, SP_W-16,7)) {
                        hitboxY = MathHelper.clamp(
                            (float)((mx-(ox+8))/(SP_W-16)) * 2.5f + 0.5f, 0.5f, 3.0f);
                        hitboxY = Math.round(hitboxY*10)/10f;
                    }
                }
                case "HitColor" -> {
                    py += 24;
                    int sy = py+11;
                    if (hovD(mx,my, ox+8,sy, SP_W-16,7))
                        hitColorR = MathHelper.clamp((float)((mx-(ox+8))/(SP_W-16)),0,1);
                    py += 31; sy = py+11;
                    if (hovD(mx,my, ox+8,sy, SP_W-16,7))
                        hitColorG = MathHelper.clamp((float)((mx-(ox+8))/(SP_W-16)),0,1);
                    py += 31; sy = py+11;
                    if (hovD(mx,my, ox+8,sy, SP_W-16,7))
                        hitColorB = MathHelper.clamp((float)((mx-(ox+8))/(SP_W-16)),0,1);
                }
            }
        }

        private boolean isOn(String n) {
            return switch(n) {
                case "TriggerBot" -> triggerBotActive;
                case "Hitboxes"   -> hitBoxActive;
                case "HitColor"   -> hitColorActive;
                default -> false;
            };
        }
        private void toggle(String n) {
            switch(n) {
                case "TriggerBot" -> triggerBotActive = !triggerBotActive;
                case "Hitboxes"   -> hitBoxActive     = !hitBoxActive;
                case "HitColor"   -> hitColorActive   = !hitColorActive;
            }
        }
        private int ox() { return width/2-MW/2; }
        private int oy() { return height/2-MH/2; }
        private boolean hovI(int mx,int my,int x,int y,int w,int h){ return mx>=x&&mx<=x+w&&my>=y&&my<=y+h; }
        private boolean hovD(double mx,double my,double x,double y,double w,double h){ return mx>=x&&mx<=x+w&&my>=y&&my<=y+h; }
    }

    // ── Util ─────────────────────────────────────────────────
    public static int packRgb(float r, float g, float b, float a) {
        return ((int)(a*255)&0xFF)<<24|((int)(r*255)&0xFF)<<16|((int)(g*255)&0xFF)<<8|((int)(b*255)&0xFF);
    }
    private boolean isEating(LivingEntity e) {
        if (e==null||!e.isUsingItem()) return false;
        ItemStack u=e.getActiveItem(); if(u.isEmpty()) return false;
        return u.getComponents().contains(net.minecraft.component.DataComponentTypes.FOOD)
            ||u.getItem()==Items.MILK_BUCKET||u.getItem()==Items.POTION
            ||u.getItem()==Items.SPLASH_POTION||u.getItem()==Items.LINGERING_POTION;
    }
    private void bar(MinecraftClient c, String m) {
        if (c.player!=null) c.player.sendMessage(Text.literal("§8[§dHitX§8] §r"+m), true);
    }
}
