package exloran.hitx;

// ═══════════════════════════════════════════════════════════════
//  NeonBoxRenderer.java  — UPDATED
//
//  DEĞİŞİKLİKLER:
//  1) Jump Ring → tam DAİRE (rx=rz), çok daha KALIN (lw=6f)
//  2) Hit Ring  → vururken entity ayağında tam yuvarlak halka
//  3) Animasyonlu [FAKE]-stili player tag (isim + HP bar + nabız)
// ═══════════════════════════════════════════════════════════════

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public class NeonBoxRenderer {

    public static void register() {
        WorldRenderEvents.LAST.register(NeonBoxRenderer::render);
        // Animasyonlu HUD tag (2D)
        HudRenderCallback.EVENT.register(NeonBoxRenderer::renderHud);
    }

    // ══════════════════════════════════════════════════════════
    private static void render(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        MatrixStack ms = ctx.matrixStack();
        if (ms == null) return;

        Vec3d cam  = ctx.camera().getPos();
        float td   = ctx.tickCounter().getTickDelta(false);
        long  time = System.currentTimeMillis();

        ms.push();

        // ── 1. Trail (yürüme izi) ────────────────────────────
        drawTrail(ms, cam, time);

        // ── 2. Jump Rings (TAM DAİRE, KALIN) ─────────────────
        drawJumpRings(ms, cam);

        // ── 3. Hit Rings (vururken zemin halkası) ─────────────
        drawHitRings(ms, cam);

        ms.pop();

        // ── 4. Neon kutu (crosshair entity) ─────────────────
        if (HitX.hitBoxActive) {
            LivingEntity target = null;
            if (mc.crosshairTarget instanceof EntityHitResult ehr
                    && ehr.getEntity() instanceof LivingEntity le
                    && le != mc.player && le.isAlive())
                target = le;

            if (target != null) {
                double ex = target.prevX + (target.getX()-target.prevX)*td - cam.x;
                double ey = target.prevY + (target.getY()-target.prevY)*td - cam.y;
                double ez = target.prevZ + (target.getZ()-target.prevZ)*td - cam.z;

                ms.push();
                ms.translate(ex, ey, ez);

                float hw = 0.35f, hh = 1.85f;
                float pulse = (float)(Math.sin(time*0.004)*0.25+0.75);
                float r = 0.30f*pulse, g = 0.15f*pulse, b = 1.0f*pulse;

                drawBox(ms.peek().getPositionMatrix(), hw, hh, r, g, b, 0.95f, 2.5f);
                drawBox(ms.peek().getPositionMatrix(), hw*0.80f, hh*0.87f, r, g, b, 0.28f, 1.1f);
                drawCorners(ms.peek().getPositionMatrix(), hw, hh, r, g, b);
                drawRotatingLines(ms, time, hw, hh, r, g, b);

                ms.pop();

                // Kırmızı zemin elipsi entity ayağında
                ms.push();
                ms.translate(ex, ey+0.01, ez);
                drawGroundEllipse(ms, time, hw*1.2f);
                ms.pop();
            }
        }
    }

    // ══════════════════════════════════════════════════════════
    //  HUD — Animasyonlu [FAKE]-stili Player Tag
    //  Fotoğraftaki gibi: isim + HP bar + nabız efekti
    // ══════════════════════════════════════════════════════════
    private static void renderHud(DrawContext ctx, net.minecraft.client.render.RenderTickCounter rtc) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.options == null) return;
        if (!HitX.hitBoxActive) return;

        LivingEntity target = null;
        if (mc.crosshairTarget instanceof EntityHitResult ehr
                && ehr.getEntity() instanceof LivingEntity le
                && le != mc.player && le.isAlive())
            target = le;
        if (target == null) return;

        long time = System.currentTimeMillis();
        TextRenderer tr = mc.textRenderer;
        MatrixStack ms = ctx.getMatrices();

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        // Kutu boyutları
        int bw = 110, bh = 44;
        int bx = sw / 2 + 8;
        int by = sh / 2 - bh / 2;

        // Nabız: alpha titreme efekti
        float pulse = (float)(Math.sin(time * 0.005) * 0.15 + 0.85);

        // Arka plan (yuvarlak köşe, yarı saydam siyah)
        int bgColor = 0xCC0A0A14;
        HitX.fillRound(ms, bx, by, bw, bh, 6f, bgColor);

        // Çerçeve: kırmızı/pembe nabız
        int borderAlpha = (int)(pulse * 200);
        int borderColor = (borderAlpha << 24) | 0xFF3333;
        HitX.outlineRound(ms, bx, by, bw, bh, 6f, borderColor | 0xFF000000);

        // İsim
        String name = target.getName().getString();
        // "[Fake]" prefix göster (Soup Visual stili)
        String displayName = "§c[Fake] §f" + name;
        ctx.drawTextWithShadow(tr, displayName, bx + 6, by + 5, 0xFFFFFFFF);

        // HP değerleri
        float hp    = target.getHealth();
        float maxHp = target.getMaxHealth();
        float hpRatio = Math.max(0, Math.min(1, hp / maxHp));

        // HP bar arka planı
        int barX = bx + 6, barY = by + 17, barW = bw - 12, barH = 6;
        ctx.fill(barX, barY, barX + barW, barY + barH, 0xFF222233);

        // HP bar dolgu (yeşil→sarı→kırmızı)
        int barFill = (int)(hpRatio * barW);
        int hpColor;
        if (hpRatio > 0.6f)      hpColor = 0xFF44EE44;
        else if (hpRatio > 0.3f) hpColor = 0xFFEEDD00;
        else                     hpColor = 0xFFEE2222;
        if (barFill > 0)
            ctx.fill(barX, barY, barX + barFill, barY + barH, hpColor);

        // HP bar parlama (nabız)
        int glowAlpha = (int)(pulse * 80);
        if (barFill > 0)
            ctx.fill(barX, barY, barX + barFill, barY + 2,
                (glowAlpha << 24) | (hpColor & 0x00FFFFFF));

        // HP sayı metni
        String hpText = "HP: §f" + (int)hp + " §8/ " + (int)maxHp;
        ctx.drawTextWithShadow(tr, hpText, bx + 6, by + 26, 0xFFAAAAAA);

        // Animasyonlu "kalp" ikonu (nabız)
        float heartScale = 0.85f + (float)(Math.sin(time * 0.006) * 0.12);
        ms.push();
        ms.translate(bx + bw - 14, by + 14, 0);
        ms.scale(heartScale, heartScale, 1f);
        ctx.drawTextWithShadow(tr, "§c❤", -4, -4, 0xFFFF4444);
        ms.pop();

        // Animasyonlu köşe süs (dönen renk)
        float[] cornerRgb = HitX.hsv((time % 3000) / 3000f, 0.9f, 1.0f);
        int cornerColor = 0xFF000000
            | ((int)(cornerRgb[0]*255) << 16)
            | ((int)(cornerRgb[1]*255) << 8)
            | (int)(cornerRgb[2]*255);
        // Sol üst köşe L
        ctx.fill(bx,    by,    bx+8,  by+2,  cornerColor);
        ctx.fill(bx,    by,    bx+2,  by+8,  cornerColor);
        // Sağ alt köşe L
        ctx.fill(bx+bw-8, by+bh-2, bx+bw, by+bh, cornerColor);
        ctx.fill(bx+bw-2, by+bh-8, bx+bw, by+bh, cornerColor);
    }

    // ══════════════════════════════════════════════════════════
    //  TRAIL — oyuncunun arkasında bıraktığı renkli iz
    // ══════════════════════════════════════════════════════════
    private static void drawTrail(MatrixStack ms, Vec3d cam, long time) {
        if (HitX.trail.isEmpty()) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.lineWidth(3.0f);

        HitX.TrailPoint[] pts = HitX.trail.toArray(new HitX.TrailPoint[0]);

        for (int i = 0; i < pts.length - 1; i++) {
            HitX.TrailPoint p1 = pts[i];
            HitX.TrailPoint p2 = pts[i+1];

            float life1 = 1f - (p1.age / (float)HitX.TRAIL_LIFE);
            float life2 = 1f - (p2.age / (float)HitX.TRAIL_LIFE);

            Tessellator tess = Tessellator.getInstance();
            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINES,
                                           VertexFormats.POSITION_COLOR);

            buf.vertex(ms.peek().getPositionMatrix(),
                (float)(p1.x-cam.x), (float)(p1.y-cam.y), (float)(p1.z-cam.z))
               .color(p1.r, p1.g, p1.b, life1 * 0.90f);
            buf.vertex(ms.peek().getPositionMatrix(),
                (float)(p2.x-cam.x), (float)(p2.y-cam.y), (float)(p2.z-cam.z))
               .color(p2.r, p2.g, p2.b, life2 * 0.90f);

            BufferRenderer.drawWithGlobalProgram(buf.end());
        }

        // Her trail noktasında küçük yuvarlak parlama (düz quad)
        for (HitX.TrailPoint tp : pts) {
            float life = 1f - (tp.age / (float)HitX.TRAIL_LIFE);
            if (life < 0.05f) continue;
            float s = 0.06f * life;
            float ox = (float)(tp.x-cam.x);
            float oy = (float)(tp.y-cam.y);
            float oz = (float)(tp.z-cam.z);

            Tessellator tess = Tessellator.getInstance();
            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS,
                                           VertexFormats.POSITION_COLOR);
            buf.vertex(ms.peek().getPositionMatrix(), ox-s, oy, oz-s).color(tp.r,tp.g,tp.b,life*0.7f);
            buf.vertex(ms.peek().getPositionMatrix(), ox+s, oy, oz-s).color(tp.r,tp.g,tp.b,life*0.7f);
            buf.vertex(ms.peek().getPositionMatrix(), ox+s, oy, oz+s).color(tp.r,tp.g,tp.b,life*0.7f);
            buf.vertex(ms.peek().getPositionMatrix(), ox-s, oy, oz+s).color(tp.r,tp.g,tp.b,life*0.7f);
            BufferRenderer.drawWithGlobalProgram(buf.end());
        }

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    // ══════════════════════════════════════════════════════════
    //  JUMP RING — zıplayınca TAM DAİRE, KALIN halka
    //  DÜZELTİLDİ: rx=rz (daire), lineWidth=6f, iç halka da kalın
    // ══════════════════════════════════════════════════════════
    private static void drawJumpRings(MatrixStack ms, Vec3d cam) {
        if (HitX.jumpRings.isEmpty()) return;

        for (HitX.JumpRing jr : HitX.jumpRings) {
            float life = 1f - (jr.age / (float)HitX.JUMP_RING_LIFE);
            float alpha = life * 0.90f;
            if (alpha < 0.02f) continue;

            // Cyan/yeşil renk, solar
            float[] rgb = HitX.hsv(0.48f + life*0.04f, 0.85f, 1.0f);

            ms.push();
            ms.translate(jr.x-cam.x, jr.y-cam.y+0.02, jr.z-cam.z);

            Matrix4f mat = ms.peek().getPositionMatrix();

            // Ana halka — tam DAİRE (rx = rz), çok KALIN (6f)
            drawCircle(mat, jr.radius, rgb[0], rgb[1], rgb[2], alpha, 6.0f, 80);

            // Dış parlama katmanı (biraz daha büyük, yarı saydam)
            drawCircle(mat, jr.radius * 1.08f, rgb[0], rgb[1], rgb[2], alpha * 0.35f, 3.5f, 64);

            // İç parlama (küçük, yumuşak)
            drawCircle(mat, jr.radius * 0.88f, rgb[0], rgb[1], rgb[2], alpha * 0.45f, 2.5f, 56);

            ms.pop();
        }
    }

    // ══════════════════════════════════════════════════════════
    //  HIT RING — vururken entity ayağında kalın yuvarlak halka
    // ══════════════════════════════════════════════════════════
    private static void drawHitRings(MatrixStack ms, Vec3d cam) {
        if (HitX.hitRings.isEmpty()) return;

        for (HitX.HitRing hr : HitX.hitRings) {
            float life = 1f - (hr.age / (float)HitX.HIT_RING_LIFE);
            float alpha = life * 0.95f;
            if (alpha < 0.02f) continue;

            ms.push();
            ms.translate(hr.x - cam.x, hr.y - cam.y + 0.02, hr.z - cam.z);

            Matrix4f mat = ms.peek().getPositionMatrix();

            // Kırmızı ana halka — tam DAİRE, çok KALIN
            drawCircle(mat, hr.radius, 1.0f, 0.1f, 0.1f, alpha, 7.0f, 80);
            // Dış glow
            drawCircle(mat, hr.radius * 1.10f, 1.0f, 0.3f, 0.05f, alpha * 0.4f, 3.5f, 64);
            // İç glow
            drawCircle(mat, hr.radius * 0.85f, 1.0f, 0.05f, 0.05f, alpha * 0.5f, 2.5f, 56);

            ms.pop();
        }
    }

    // ══════════════════════════════════════════════════════════
    //  NEON BOX
    // ══════════════════════════════════════════════════════════
    private static void drawBox(Matrix4f mat, float hw, float hh,
                                float r, float g, float b, float alpha, float lw) {
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.lineWidth(lw);

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINES,
                                       VertexFormats.POSITION_COLOR);
        float x0=-hw,x1=hw,y0=0,y1=hh,z0=-hw,z1=hw;
        edge(buf,mat, x0,y0,z0, x1,y0,z0, r,g,b,alpha);
        edge(buf,mat, x1,y0,z0, x1,y0,z1, r,g,b,alpha);
        edge(buf,mat, x1,y0,z1, x0,y0,z1, r,g,b,alpha);
        edge(buf,mat, x0,y0,z1, x0,y0,z0, r,g,b,alpha);
        edge(buf,mat, x0,y1,z0, x1,y1,z0, r,g,b,alpha);
        edge(buf,mat, x1,y1,z0, x1,y1,z1, r,g,b,alpha);
        edge(buf,mat, x1,y1,z1, x0,y1,z1, r,g,b,alpha);
        edge(buf,mat, x0,y1,z1, x0,y1,z0, r,g,b,alpha);
        edge(buf,mat, x0,y0,z0, x0,y1,z0, r,g,b,alpha);
        edge(buf,mat, x1,y0,z0, x1,y1,z0, r,g,b,alpha);
        edge(buf,mat, x1,y0,z1, x1,y1,z1, r,g,b,alpha);
        edge(buf,mat, x0,y0,z1, x0,y1,z1, r,g,b,alpha);
        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.enableDepthTest(); RenderSystem.disableBlend();
    }

    private static void drawRotatingLines(MatrixStack ms, long time,
                                          float hw, float hh,
                                          float r, float g, float b) {
        float ay = (float)(time * 0.0009 * 90.0);
        float ax = (float)(time * 0.0009 * 60.0);

        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.lineWidth(2.8f);

        Tessellator tess = Tessellator.getInstance();

        ms.push();
        ms.translate(0, hh*0.5f, 0);
        ms.multiply(new Quaternionf().rotateY((float)Math.toRadians(ay)));
        Matrix4f m = ms.peek().getPositionMatrix();
        { BufferBuilder buf=tess.begin(VertexFormat.DrawMode.DEBUG_LINES,VertexFormats.POSITION_COLOR);
          buf.vertex(m,-hw*1.15f,0,0).color(r,g,b,0.92f);buf.vertex(m,hw*1.15f,0,0).color(r,g,b,0.92f);
          BufferRenderer.drawWithGlobalProgram(buf.end()); }
        { BufferBuilder buf=tess.begin(VertexFormat.DrawMode.DEBUG_LINES,VertexFormats.POSITION_COLOR);
          buf.vertex(m,0,0,-hw*1.15f).color(r,g,b,0.92f);buf.vertex(m,0,0,hw*1.15f).color(r,g,b,0.92f);
          BufferRenderer.drawWithGlobalProgram(buf.end()); }
        ms.pop();

        ms.push();
        ms.translate(0, hh*0.5f, 0);
        ms.multiply(new Quaternionf().rotateX((float)Math.toRadians(ax)));
        Matrix4f m2 = ms.peek().getPositionMatrix();
        float hs = hh*0.52f;
        { BufferBuilder buf=tess.begin(VertexFormat.DrawMode.DEBUG_LINES,VertexFormats.POSITION_COLOR);
          buf.vertex(m2,-hw,-hs,0).color(r,g,b,0.92f);buf.vertex(m2,hw,hs,0).color(r,g,b,0.92f);
          BufferRenderer.drawWithGlobalProgram(buf.end()); }
        { BufferBuilder buf=tess.begin(VertexFormat.DrawMode.DEBUG_LINES,VertexFormats.POSITION_COLOR);
          buf.vertex(m2,hw,-hs,0).color(r,g,b,0.92f);buf.vertex(m2,-hw,hs,0).color(r,g,b,0.92f);
          BufferRenderer.drawWithGlobalProgram(buf.end()); }
        ms.pop();

        RenderSystem.enableDepthTest(); RenderSystem.disableBlend();
    }

    private static void drawCorners(Matrix4f mat, float hw, float hh, float r, float g, float b) {
        float cl = Math.min(hw, hh*0.5f)*0.38f;
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.lineWidth(2.5f);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINES,VertexFormats.POSITION_COLOR);
        float x0=-hw,x1=hw,y0=0,y1=hh,z0=-hw,z1=hw;
        corner(buf,mat,x0,y0,z0,+cl,0,0,0,+cl,0,0,0,+cl,r,g,b,1f);
        corner(buf,mat,x1,y0,z0,-cl,0,0,0,+cl,0,0,0,+cl,r,g,b,1f);
        corner(buf,mat,x0,y0,z1,+cl,0,0,0,+cl,0,0,0,-cl,r,g,b,1f);
        corner(buf,mat,x1,y0,z1,-cl,0,0,0,+cl,0,0,0,-cl,r,g,b,1f);
        corner(buf,mat,x0,y1,z0,+cl,0,0,0,-cl,0,0,0,+cl,r,g,b,1f);
        corner(buf,mat,x1,y1,z0,-cl,0,0,0,-cl,0,0,0,+cl,r,g,b,1f);
        corner(buf,mat,x0,y1,z1,+cl,0,0,0,-cl,0,0,0,-cl,r,g,b,1f);
        corner(buf,mat,x1,y1,z1,-cl,0,0,0,-cl,0,0,0,-cl,r,g,b,1f);
        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.enableDepthTest(); RenderSystem.disableBlend();
    }

    private static void drawGroundEllipse(MatrixStack ms, long time, float radius) {
        float pulse = (float)(Math.sin(time*0.005)*0.3+0.7);
        float rotA  = (float)(time*0.0009*45.0);

        ms.push();
        ms.multiply(new Quaternionf().rotateY((float)Math.toRadians(rotA)));
        Matrix4f mat = ms.peek().getPositionMatrix();
        drawFlatEllipse(mat, radius, radius*0.55f, 1.0f, 0.05f*pulse, 0.05f*pulse, 0.85f*pulse, 2.2f, 64);
        float r2 = radius*0.55f;
        float p2 = (float)(Math.sin(time*0.007+1.0)*0.3+0.7);
        drawFlatEllipse(mat, r2, r2*0.55f, 1.0f, 0.18f*p2, 0.18f*p2, 0.55f*p2, 1.5f, 48);
        ms.pop();
    }

    // ══════════════════════════════════════════════════════════
    //  TAM DAİRE çizici (rx = rz) — Jump Ring ve Hit Ring için
    // ══════════════════════════════════════════════════════════
    private static void drawCircle(Matrix4f mat, float radius,
                                   float r, float g, float b, float alpha,
                                   float lw, int segs) {
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.lineWidth(lw);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP,
                                       VertexFormats.POSITION_COLOR);
        for (int i = 0; i <= segs; i++) {
            double a = 2.0 * Math.PI * i / segs;
            // rx = rz = radius → TAM DAİRE
            buf.vertex(mat, (float)(Math.cos(a)*radius), 0f, (float)(Math.sin(a)*radius))
               .color(r, g, b, alpha);
        }
        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.enableDepthTest(); RenderSystem.disableBlend();
    }

    // Elips (eski kod uyumu için bırakıldı)
    private static void drawFlatEllipse(Matrix4f mat, float rx, float rz,
                                         float r, float g, float b, float alpha,
                                         float lw, int segs) {
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.lineWidth(lw);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP,
                                       VertexFormats.POSITION_COLOR);
        for (int i=0; i<=segs; i++) {
            double a = 2.0*Math.PI*i/segs;
            buf.vertex(mat,(float)(Math.cos(a)*rx),0f,(float)(Math.sin(a)*rz)).color(r,g,b,alpha);
        }
        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.enableDepthTest(); RenderSystem.disableBlend();
    }

    // ── Yardımcılar ──────────────────────────────────────────
    private static void edge(BufferBuilder buf, Matrix4f mat,
                              float x0,float y0,float z0,
                              float x1,float y1,float z1,
                              float r,float g,float b,float a) {
        buf.vertex(mat,x0,y0,z0).color(r,g,b,a);
        buf.vertex(mat,x1,y1,z1).color(r,g,b,a);
    }

    private static void corner(BufferBuilder buf, Matrix4f mat,
                                float px,float py,float pz,
                                float dx,float dy,float dz,
                                float ex,float ey,float ez,
                                float fx,float fy,float fz,
                                float r,float g,float b,float a) {
        buf.vertex(mat,px,py,pz).color(r,g,b,a); buf.vertex(mat,px+dx,py+dy,pz+dz).color(r,g,b,a);
        buf.vertex(mat,px,py,pz).color(r,g,b,a); buf.vertex(mat,px+ex,py+ey,pz+ez).color(r,g,b,a);
        buf.vertex(mat,px,py,pz).color(r,g,b,a); buf.vertex(mat,px+fx,py+fy,pz+fz).color(r,g,b,a);
    }
}
