package exloran.hitx;

// ═══════════════════════════════════════════════════════════════
//  NeonBoxRenderer.java
//
//  - Sadece crosshair'daki entity'ye (bakılan kişiye) neon kutu çizer
//  - Crosshair'dan çekilince efekt kaybolur
//  - Dönen çizgiler: 0.9 hız (sabit animasyon)
//  - Entity'nin ayak altında kırmızı elips zemin halkası
//  - Köşe süslemeleri (L şeklinde)
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

public class NeonBoxRenderer {

    public static void register() {
        WorldRenderEvents.LAST.register(NeonBoxRenderer::onWorldRender);
    }

    // ══════════════════════════════════════════════════════════
    //  Ana render callback
    // ══════════════════════════════════════════════════════════
    private static void onWorldRender(WorldRenderContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        if (!HitX.hitBoxActive) return;

        // Sadece crosshair'daki entity'yi al
        LivingEntity target = null;
        if (client.crosshairTarget instanceof EntityHitResult ehr
                && ehr.getEntity() instanceof LivingEntity le
                && le != client.player
                && le.isAlive()) {
            target = le;
        }
        if (target == null) return;

        MatrixStack ms = ctx.matrixStack();
        if (ms == null) return;

        Vec3d cam = ctx.camera().getPos();
        long time = System.currentTimeMillis();

        // Interpolated pozisyon
        float td = ctx.tickCounter().getTickDelta(false);
        double ex = target.prevX + (target.getX() - target.prevX) * td;
        double ey = target.prevY + (target.getY() - target.prevY) * td;
        double ez = target.prevZ + (target.getZ() - target.prevZ) * td;

        double ox = ex - cam.x;
        double oy = ey - cam.y;
        double oz = ez - cam.z;

        // Sabit boyutlar — her zaman aynı (hitbox boyutuna göre değil)
        float hw = 0.35f;   // yatay yarı genişlik
        float hh = 1.85f;   // yükseklik

        // Pulse efekti
        float pulse = (float)(Math.sin(time * 0.004) * 0.25 + 0.75);

        // Neon mavi/mor renk
        float r = 0.35f * pulse;
        float g = 0.20f * pulse;
        float b = 1.00f * pulse;

        ms.push();
        ms.translate(ox, oy, oz);
        Matrix4f mat = ms.peek().getPositionMatrix();

        // ── 1. Dış neon kutu ─────────────────────────────────
        drawBox(mat, hw, hh, r, g, b, 0.95f, 2.5f);

        // ── 2. İç kutu (ince, şeffaf) ────────────────────────
        drawBox(mat, hw * 0.80f, hh * 0.87f, r, g, b, 0.30f, 1.2f);

        // ── 3. Köşe L süslemeleri ────────────────────────────
        drawCornerAccents(mat, hw, hh, r, g, b);

        // ── 4. Dönen çizgiler (0.9 hız) ─────────────────────
        drawRotatingLines(mat, ms, time, hw, hh, r, g, b);

        ms.pop();

        // ── 5. Kırmızı zemin elipsi (ayak hizasında) ────────
        ms.push();
        ms.translate(ox, oy + 0.01, oz); // zeminde hafif üstte (z-fighting önlemi)
        drawGroundEllipse(ms, time, hw * 1.15f);
        ms.pop();
    }

    // ══════════════════════════════════════════════════════════
    //  3D Neon Kutu — 12 kenar
    // ══════════════════════════════════════════════════════════
    private static void drawBox(Matrix4f mat,
                                float hw, float hh,
                                float r, float g, float b, float alpha,
                                float lineWidth) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.lineWidth(lineWidth);

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINES,
                                       VertexFormats.POSITION_COLOR);

        float x0=-hw, x1=hw, y0=0f, y1=hh, z0=-hw, z1=hw;

        // Alt dörtgen
        edge(buf,mat, x0,y0,z0, x1,y0,z0, r,g,b,alpha);
        edge(buf,mat, x1,y0,z0, x1,y0,z1, r,g,b,alpha);
        edge(buf,mat, x1,y0,z1, x0,y0,z1, r,g,b,alpha);
        edge(buf,mat, x0,y0,z1, x0,y0,z0, r,g,b,alpha);
        // Üst dörtgen
        edge(buf,mat, x0,y1,z0, x1,y1,z0, r,g,b,alpha);
        edge(buf,mat, x1,y1,z0, x1,y1,z1, r,g,b,alpha);
        edge(buf,mat, x1,y1,z1, x0,y1,z1, r,g,b,alpha);
        edge(buf,mat, x0,y1,z1, x0,y1,z0, r,g,b,alpha);
        // Dikey kenarlar
        edge(buf,mat, x0,y0,z0, x0,y1,z0, r,g,b,alpha);
        edge(buf,mat, x1,y0,z0, x1,y1,z0, r,g,b,alpha);
        edge(buf,mat, x1,y0,z1, x1,y1,z1, r,g,b,alpha);
        edge(buf,mat, x0,y0,z1, x0,y1,z1, r,g,b,alpha);

        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    // ══════════════════════════════════════════════════════════
    //  Dönen çizgiler — 0.9 hız, ortada döner
    // ══════════════════════════════════════════════════════════
    private static void drawRotatingLines(Matrix4f baseMat, MatrixStack ms,
                                          long time,
                                          float hw, float hh,
                                          float r, float g, float b) {
        float alpha = 0.92f;
        float lw    = 2.8f;

        // Hız: 0.9 — time * 0.0009 (derece/ms cinsinden)
        float angleY = (float)(time * 0.0009 * 90.0); // hızı 0.9 olarak ayarlandı
        float angleX = (float)(time * 0.0009 * 60.0);

        // ── Yatay dönen çift çizgi (XZ düzlemi) ─────────────
        ms.push();
        ms.translate(0, hh * 0.5f, 0);
        ms.multiply(new org.joml.Quaternionf().rotateY(org.joml.Math.toRadians(angleY)));
        Matrix4f rotMat = ms.peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.lineWidth(lw);

        Tessellator tess = Tessellator.getInstance();
        {
            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            buf.vertex(rotMat, -hw*1.2f, 0f, 0f).color(r, g, b, alpha);
            buf.vertex(rotMat,  hw*1.2f, 0f, 0f).color(r, g, b, alpha);
            BufferRenderer.drawWithGlobalProgram(buf.end());
        }
        {
            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            buf.vertex(rotMat, 0f, 0f, -hw*1.2f).color(r, g, b, alpha);
            buf.vertex(rotMat, 0f, 0f,  hw*1.2f).color(r, g, b, alpha);
            BufferRenderer.drawWithGlobalProgram(buf.end());
        }
        ms.pop();

        // ── Dikey dönen çapraz şeritler (XY düzlemi) ────────
        ms.push();
        ms.translate(0, hh * 0.5f, 0);
        ms.multiply(new org.joml.Quaternionf().rotateX(org.joml.Math.toRadians(angleX)));
        Matrix4f rotMat2 = ms.peek().getPositionMatrix();

        float hs = hh * 0.52f;
        {
            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            buf.vertex(rotMat2, -hw, -hs, 0f).color(r, g, b, alpha);
            buf.vertex(rotMat2,  hw,  hs, 0f).color(r, g, b, alpha);
            BufferRenderer.drawWithGlobalProgram(buf.end());
        }
        {
            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            buf.vertex(rotMat2,  hw, -hs, 0f).color(r, g, b, alpha);
            buf.vertex(rotMat2, -hw,  hs, 0f).color(r, g, b, alpha);
            BufferRenderer.drawWithGlobalProgram(buf.end());
        }
        ms.pop();

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    // ══════════════════════════════════════════════════════════
    //  Köşe L süslemeleri
    // ══════════════════════════════════════════════════════════
    private static void drawCornerAccents(Matrix4f mat, float hw, float hh, float r, float g, float b) {
        float cLen = Math.min(hw, hh * 0.5f) * 0.4f;
        float alpha = 1.0f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.lineWidth(2.5f);

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        float x0=-hw, x1=hw, y0=0, y1=hh, z0=-hw, z1=hw;

        corner(buf,mat, x0,y0,z0, +cLen,0,0, 0,+cLen,0, 0,0,+cLen, r,g,b,alpha);
        corner(buf,mat, x1,y0,z0, -cLen,0,0, 0,+cLen,0, 0,0,+cLen, r,g,b,alpha);
        corner(buf,mat, x0,y0,z1, +cLen,0,0, 0,+cLen,0, 0,0,-cLen, r,g,b,alpha);
        corner(buf,mat, x1,y0,z1, -cLen,0,0, 0,+cLen,0, 0,0,-cLen, r,g,b,alpha);
        corner(buf,mat, x0,y1,z0, +cLen,0,0, 0,-cLen,0, 0,0,+cLen, r,g,b,alpha);
        corner(buf,mat, x1,y1,z0, -cLen,0,0, 0,-cLen,0, 0,0,+cLen, r,g,b,alpha);
        corner(buf,mat, x0,y1,z1, +cLen,0,0, 0,-cLen,0, 0,0,-cLen, r,g,b,alpha);
        corner(buf,mat, x1,y1,z1, -cLen,0,0, 0,-cLen,0, 0,0,-cLen, r,g,b,alpha);

        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    // ══════════════════════════════════════════════════════════
    //  Kırmızı zemin elipsi — entity'nin ayaklarının altında
    //  Dönerek animasyon yapar (dış + iç halka)
    // ══════════════════════════════════════════════════════════
    private static void drawGroundEllipse(MatrixStack ms, long time, float radius) {
        // Zemine yatay (XZ düzleminde) çizilmesi için Y ekseninde 90 derece döndür
        ms.push();
        // Kırmızı parlak renk (pulse)
        float pulse = (float)(Math.sin(time * 0.005) * 0.3 + 0.7);
        float r = 1.0f;
        float g = 0.05f * pulse;
        float b = 0.05f * pulse;
        float alpha = 0.85f * pulse;

        // Dönen animasyon (0.9 hız)
        float rotAngle = (float)(time * 0.0009 * 45.0);
        ms.multiply(new org.joml.Quaternionf().rotateY(org.joml.Math.toRadians(rotAngle)));

        Matrix4f mat = ms.peek().getPositionMatrix();

        // ── Dış elips halkası ────────────────────────────────
        drawEllipseRing(mat, radius, radius * 0.55f, r, g, b, alpha, 2.2f, 64);

        // ── İç elips halkası (küçük, daha parlak) ────────────
        float r2 = radius * 0.55f;
        float pulse2 = (float)(Math.sin(time * 0.007 + 1.0) * 0.3 + 0.7);
        drawEllipseRing(mat, r2, r2 * 0.55f, 1.0f, 0.2f * pulse2, 0.2f * pulse2, 0.6f * pulse2, 1.5f, 48);

        ms.pop();
    }

    /**
     * XZ düzleminde elips çizer (y=0 sabit).
     * rx = X yarıçapı, rz = Z yarıçapı
     */
    private static void drawEllipseRing(Matrix4f mat, float rx, float rz,
                                         float r, float g, float b, float alpha,
                                         float lineWidth, int segments) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.lineWidth(lineWidth);

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP,
                                       VertexFormats.POSITION_COLOR);

        for (int i = 0; i <= segments; i++) {
            double angle = 2.0 * Math.PI * i / segments;
            float px = (float)(Math.cos(angle) * rx);
            float pz = (float)(Math.sin(angle) * rz);
            buf.vertex(mat, px, 0f, pz).color(r, g, b, alpha);
        }

        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    // ══════════════════════════════════════════════════════════
    //  YARDIMCILAR
    // ══════════════════════════════════════════════════════════
    private static void edge(BufferBuilder buf, Matrix4f mat,
                              float x0,float y0,float z0,
                              float x1,float y1,float z1,
                              float r,float g,float b,float a) {
        buf.vertex(mat, x0, y0, z0).color(r, g, b, a);
        buf.vertex(mat, x1, y1, z1).color(r, g, b, a);
    }

    private static void corner(BufferBuilder buf, Matrix4f mat,
                                float px,float py,float pz,
                                float dx,float dy,float dz,
                                float ex,float ey,float ez,
                                float fx,float fy,float fz,
                                float r,float g,float b,float a) {
        buf.vertex(mat,px,py,pz).color(r,g,b,a);
        buf.vertex(mat,px+dx,py+dy,pz+dz).color(r,g,b,a);
        buf.vertex(mat,px,py,pz).color(r,g,b,a);
        buf.vertex(mat,px+ex,py+ey,pz+ez).color(r,g,b,a);
        buf.vertex(mat,px,py,pz).color(r,g,b,a);
        buf.vertex(mat,px+fx,py+fy,pz+fz).color(r,g,b,a);
    }
}
