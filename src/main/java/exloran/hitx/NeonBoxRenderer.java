package exloran.hitx;

// ═══════════════════════════════════════════════════════════════
//  NeonBoxRenderer.java
//  Dosya yolu: src/main/java/exloran/hitx/NeonBoxRenderer.java
//
//  Fotoğraftaki mavi/mor neon 3D kutu + dönen çizgiler efekti.
//  Vanilla MC partikeli kullanılmaz — tamamen özel VertexBuffer.
//  WorldRenderEvents.LAST ile 3D world-space'te çizilir.
// ═══════════════════════════════════════════════════════════════

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public class NeonBoxRenderer {

    // ── Kayıt ────────────────────────────────────────────────
    // HitX.onInitializeClient() içinden çağır:
    //   NeonBoxRenderer.register();
    public static void register() {
        WorldRenderEvents.LAST.register(NeonBoxRenderer::onWorldRender);
    }

    // ══════════════════════════════════════════════════════════
    //  Ana render callback — her frame çağrılır
    // ══════════════════════════════════════════════════════════
    private static void onWorldRender(WorldRenderContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        if (!HitX.espActive && !HitX.auraActive) return;

        MatrixStack ms = ctx.matrixStack();
        if (ms == null) return;

        // Kamera pozisyonu — entity koordinatlarını kamera-relative'e çevirmek için
        Vec3d cam = ctx.camera().getPos();

        long time = System.currentTimeMillis();

        ms.push();

        for (Entity e : client.world.getEntities()) {
            if (!(e instanceof LivingEntity le)) continue;
            if (le == client.player || !le.isAlive()) continue;

            // Yalnız espActive veya aura hedefiyse çiz
            boolean isAuraTarget = (le == HitX.auraLocked);
            if (!HitX.espActive && !isAuraTarget) continue;

            // Entity'nin bounding box'ı — interpolated pozisyon
            double ex = le.prevX + (le.getX() - le.prevX) * ctx.tickCounter().getTickDelta(false);
            double ey = le.prevY + (le.getY() - le.prevY) * ctx.tickCounter().getTickDelta(false);
            double ez = le.prevZ + (le.getZ() - le.prevZ) * ctx.tickCounter().getTickDelta(false);

            // Kamera-relative offset
            double ox = ex - cam.x;
            double oy = ey - cam.y;
            double oz = ez - cam.z;

            // Box boyutu
            float hw = le.getWidth()  * 0.5f + 0.05f;
            float hh = le.getHeight()        + 0.05f;

            // ── Renk seçimi ──────────────────────────────────
            // Aura hedefi: parlak mor/pembe  |  Normal: mavi/cyan
            float r, g, b;
            if (isAuraTarget) {
                // Neon mor — zamana göre pulse
                float pulse = (float)(Math.sin(time * 0.004) * 0.25 + 0.75);
                r = 0.75f * pulse;
                g = 0.0f;
                b = 1.0f * pulse;
            } else {
                // Neon mavi
                float pulse = (float)(Math.sin(time * 0.003 + ox) * 0.2 + 0.8);
                r = 0.3f * pulse;
                g = 0.5f * pulse;
                b = 1.0f * pulse;
            }

            ms.push();
            ms.translate(ox, oy, oz);

            Matrix4f mat = ms.peek().getPositionMatrix();

            // ── 1. Dış neon kutu (kalın, parlak) ─────────────
            drawBox(mat, hw, hh, r, g, b, 0.95f, 2.5f);

            // ── 2. İç kutu (daha ince, şeffaf) ───────────────
            drawBox(mat, hw * 0.82f, hh * 0.88f, r, g, b, 0.35f, 1.2f);

            // ── 3. Fotoğraftaki dönen çizgiler (X şeklinde) ──
            if (HitX.auraActive) {
                drawRotatingLines(mat, ms, time, hw, hh, r, g, b, isAuraTarget);
            }

            // ── 4. Köşe süslemeleri ───────────────────────────
            drawCornerAccents(mat, hw, hh, r, g, b);

            ms.pop();
        }

        ms.pop();
    }

    // ══════════════════════════════════════════════════════════
    //  3D Neon Kutu — 12 kenar, renkli glow
    // ══════════════════════════════════════════════════════════
    private static void drawBox(Matrix4f mat,
                                float hw, float hh,
                                float r, float g, float b, float alpha,
                                float lineWidth) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();  // Duvarların arkasından görünsün
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.lineWidth(lineWidth);

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINES,
                                       VertexFormats.POSITION_COLOR);

        float x0 = -hw, x1 = hw;
        float y0 = 0f,  y1 = hh;
        float z0 = -hw, z1 = hw;

        // Alt dörtgen
        edge(buf, mat, x0,y0,z0, x1,y0,z0, r,g,b,alpha);
        edge(buf, mat, x1,y0,z0, x1,y0,z1, r,g,b,alpha);
        edge(buf, mat, x1,y0,z1, x0,y0,z1, r,g,b,alpha);
        edge(buf, mat, x0,y0,z1, x0,y0,z0, r,g,b,alpha);
        // Üst dörtgen
        edge(buf, mat, x0,y1,z0, x1,y1,z0, r,g,b,alpha);
        edge(buf, mat, x1,y1,z0, x1,y1,z1, r,g,b,alpha);
        edge(buf, mat, x1,y1,z1, x0,y1,z1, r,g,b,alpha);
        edge(buf, mat, x0,y1,z1, x0,y1,z0, r,g,b,alpha);
        // Dikey kenarlar
        edge(buf, mat, x0,y0,z0, x0,y1,z0, r,g,b,alpha);
        edge(buf, mat, x1,y0,z0, x1,y1,z0, r,g,b,alpha);
        edge(buf, mat, x1,y0,z1, x1,y1,z1, r,g,b,alpha);
        edge(buf, mat, x0,y0,z1, x0,y1,z1, r,g,b,alpha);

        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    // ══════════════════════════════════════════════════════════
    //  Dönen çizgiler — fotoğraftaki X / çapraz neon şeritler
    //  MatrixStack döndürme kullanılır → zaman tabanlı animasyon
    // ══════════════════════════════════════════════════════════
    private static void drawRotatingLines(Matrix4f baseMat, MatrixStack ms,
                                          long time,
                                          float hw, float hh,
                                          float r, float g, float b,
                                          boolean bright) {
        float alpha = bright ? 0.95f : 0.6f;
        float lw    = bright ? 3.0f  : 1.8f;

        // ── Yatay dönen halka (XZ düzlemi) ───────────────────
        ms.push();
        ms.translate(0, hh * 0.5f, 0);

        // Zaman tabanlı Y ekseni dönüşü
        float angleY = (float)(time * 0.002);
        ms.multiply(new org.joml.Quaternionf().rotateY(org.joml.Math.toRadians(angleY)));

        Matrix4f rotMat = ms.peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.lineWidth(lw);

        Tessellator tess = Tessellator.getInstance();
        {
            // Yatay X çizgisi
            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINES,
                                           VertexFormats.POSITION_COLOR);
            buf.vertex(rotMat, -hw * 1.1f, 0f, 0f).color(r, g, b, alpha);
            buf.vertex(rotMat,  hw * 1.1f, 0f, 0f).color(r, g, b, alpha);
            BufferRenderer.drawWithGlobalProgram(buf.end());
        }
        {
            // Yatay Z çizgisi (90 derece fark)
            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINES,
                                           VertexFormats.POSITION_COLOR);
            buf.vertex(rotMat, 0f, 0f, -hw * 1.1f).color(r, g, b, alpha);
            buf.vertex(rotMat, 0f, 0f,  hw * 1.1f).color(r, g, b, alpha);
            BufferRenderer.drawWithGlobalProgram(buf.end());
        }

        ms.pop();

        // ── Dikey dönen halka (XY düzlemi, fotoğraftaki çapraz şeritler) ──
        ms.push();
        ms.translate(0, hh * 0.5f, 0);

        float angleX = (float)(time * 0.003);
        ms.multiply(new org.joml.Quaternionf().rotateX(org.joml.Math.toRadians(angleX)));

        Matrix4f rotMat2 = ms.peek().getPositionMatrix();

        // Parlak çapraz çizgi 1
        {
            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINES,
                                           VertexFormats.POSITION_COLOR);
            float hs = hh * 0.55f;
            buf.vertex(rotMat2, -hw, -hs, 0f).color(r, g, b, alpha);
            buf.vertex(rotMat2,  hw,  hs, 0f).color(r, g, b, alpha);
            BufferRenderer.drawWithGlobalProgram(buf.end());
        }
        // Parlak çapraz çizgi 2
        {
            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINES,
                                           VertexFormats.POSITION_COLOR);
            float hs = hh * 0.55f;
            buf.vertex(rotMat2,  hw, -hs, 0f).color(r, g, b, alpha);
            buf.vertex(rotMat2, -hw,  hs, 0f).color(r, g, b, alpha);
            BufferRenderer.drawWithGlobalProgram(buf.end());
        }

        ms.pop();

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    // ══════════════════════════════════════════════════════════
    //  Köşe süslemeleri — kutunun 8 köşesinde küçük parlak nokta
    // ══════════════════════════════════════════════════════════
    private static void drawCornerAccents(Matrix4f mat,
                                          float hw, float hh,
                                          float r, float g, float b) {
        float cLen = Math.min(hw, hh * 0.5f) * 0.4f;
        float alpha = 1.0f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.lineWidth(2.5f);

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINES,
                                       VertexFormats.POSITION_COLOR);

        float x0=-hw,x1=hw,y0=0,y1=hh,z0=-hw,z1=hw;

        // Her köşeden 3 yönde kısa çizgi (L şekli)
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
        // 3 kol
        buf.vertex(mat,px,py,pz).color(r,g,b,a);
        buf.vertex(mat,px+dx,py+dy,pz+dz).color(r,g,b,a);
        buf.vertex(mat,px,py,pz).color(r,g,b,a);
        buf.vertex(mat,px+ex,py+ey,pz+ez).color(r,g,b,a);
        buf.vertex(mat,px,py,pz).color(r,g,b,a);
        buf.vertex(mat,px+fx,py+fy,pz+fz).color(r,g,b,a);
    }
}
