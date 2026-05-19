package exloran.hitx;

// ═══════════════════════════════════════════════════════════════
//  NeonBoxRenderer.java
//
//  Çizer:
//  1) Oyuncunun arkasında renkli TRAIL (yürüme izi) — otomatik renk değişimi
//  2) Zıplayınca genişleyen JUMP RING (zemin halkası)
//  3) Crosshair'daki entity'ye NEON KUTU + dönen çizgiler + köşe süsleri
//  4) Entity altında kırmızı dönen ZEMIN ELİPSİ
// ═══════════════════════════════════════════════════════════════

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
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

        // ── 2. Jump Rings ────────────────────────────────────
        drawJumpRings(ms, cam);

        ms.pop();

        // ── 3. Neon kutu (crosshair entity) ─────────────────
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
    //  JUMP RING — zıplayınca genişleyen halka
    //  Soup Visual'daki gibi: zıplayınca zemin halkası çıkar,
    //  genişler ve solar (renk: cyan/yeşil)
    // ══════════════════════════════════════════════════════════
    private static void drawJumpRings(MatrixStack ms, Vec3d cam) {
        if (HitX.jumpRings.isEmpty()) return;

        for (HitX.JumpRing jr : HitX.jumpRings) {
            float life = 1f - (jr.age / (float)HitX.JUMP_RING_LIFE);
            float alpha = life * 0.85f;
            if (alpha < 0.02f) continue;

            // Renk: life yüksekken parlak cyan, solar
            float[] rgb = HitX.hsv(0.48f + life*0.04f, 0.85f, 1.0f);

            ms.push();
            ms.translate(jr.x-cam.x, jr.y-cam.y+0.02, jr.z-cam.z);

            Matrix4f mat = ms.peek().getPositionMatrix();

            // Dış halka
            drawFlatEllipse(mat, jr.radius, jr.radius * 0.55f,
                rgb[0], rgb[1], rgb[2], alpha, 2.5f, 64);
            // İç parlama halkası (daha kalın, yarı saydam)
            drawFlatEllipse(mat, jr.radius * 0.72f, jr.radius * 0.40f,
                rgb[0], rgb[1], rgb[2], alpha * 0.5f, 1.5f, 48);

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
        // Alt
        edge(buf,mat, x0,y0,z0, x1,y0,z0, r,g,b,alpha);
        edge(buf,mat, x1,y0,z0, x1,y0,z1, r,g,b,alpha);
        edge(buf,mat, x1,y0,z1, x0,y0,z1, r,g,b,alpha);
        edge(buf,mat, x0,y0,z1, x0,y0,z0, r,g,b,alpha);
        // Üst
        edge(buf,mat, x0,y1,z0, x1,y1,z0, r,g,b,alpha);
        edge(buf,mat, x1,y1,z0, x1,y1,z1, r,g,b,alpha);
        edge(buf,mat, x1,y1,z1, x0,y1,z1, r,g,b,alpha);
        edge(buf,mat, x0,y1,z1, x0,y1,z0, r,g,b,alpha);
        // Dikey
        edge(buf,mat, x0,y0,z0, x0,y1,z0, r,g,b,alpha);
        edge(buf,mat, x1,y0,z0, x1,y1,z0, r,g,b,alpha);
        edge(buf,mat, x1,y0,z1, x1,y1,z1, r,g,b,alpha);
        edge(buf,mat, x0,y0,z1, x0,y1,z1, r,g,b,alpha);
        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.enableDepthTest(); RenderSystem.disableBlend();
    }

    // ── Dönen çizgiler (0.9 hız) ─────────────────────────────
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

    // ── Köşe L süsleri ───────────────────────────────────────
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

    // ── Kırmızı dönen zemin elipsi (entity altı) ─────────────
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

    // ── Yatay elips halkası (XZ düzlemi) ─────────────────────
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
