package exloran.hitx;

// ═══════════════════════════════════════════════════════════════
//  NeonBoxRenderer.java  — v3 GÜNCEL
//
//  DEĞİŞİKLİKLER:
//  1) Trail → KALIN (6f), SARI/ALTIN renk sabit
//  2) HUD TAG → KALDIRILDI
//  3) Hitbox ring → entity'nin hitbox boyutuna göre dönen halka
//  4) Şapka → kendi playerımıza animasyonlu hasır şapka
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
        // HUD TAG KALDIRILDI — HudRenderCallback yok
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

        // ── 1. Trail (yürüme izi) — KALIN + SARI ────────────
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

                // Hitbox boyutunu al
                float hw = (float)(target.getBoundingBox().getLengthX() * 0.5f);
                float hh = (float)(target.getBoundingBox().getLengthY());
                float pulse = (float)(Math.sin(time*0.004)*0.25+0.75);
                float r = 0.30f*pulse, g = 0.15f*pulse, b = 1.0f*pulse;

                ms.push();
                ms.translate(ex, ey, ez);

                drawBox(ms.peek().getPositionMatrix(), hw, hh, r, g, b, 0.95f, 2.5f);
                drawBox(ms.peek().getPositionMatrix(), hw*0.80f, hh*0.87f, r, g, b, 0.28f, 1.1f);
                drawCorners(ms.peek().getPositionMatrix(), hw, hh, r, g, b);
                drawRotatingLines(ms, time, hw, hh, r, g, b);

                ms.pop();

                // ── Hitbox boyutuna göre dönen zemin halkası ──
                // Halka tam hitbox yarıçapında, ortada dönsün
                ms.push();
                ms.translate(ex, ey + 0.02, ez);
                drawHitboxSizedRing(ms, time, hw);
                ms.pop();
            }
        }

        // ── 5. Kendi playerımıza animasyonlu hasır şapka ─────
        drawPlayerHat(ctx, ms, cam, td, time);
    }

    // ══════════════════════════════════════════════════════════
    //  TRAIL — KALIN (6f) + SARI/ALTIN sabit renk
    // ══════════════════════════════════════════════════════════
    private static void drawTrail(MatrixStack ms, Vec3d cam, long time) {
        if (HitX.trail.isEmpty()) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.lineWidth(6.0f); // KALIN

        HitX.TrailPoint[] pts = HitX.trail.toArray(new HitX.TrailPoint[0]);

        for (int i = 0; i < pts.length - 1; i++) {
            HitX.TrailPoint p1 = pts[i];
            HitX.TrailPoint p2 = pts[i+1];

            float life1 = 1f - (p1.age / (float)HitX.TRAIL_LIFE);
            float life2 = 1f - (p2.age / (float)HitX.TRAIL_LIFE);

            Tessellator tess = Tessellator.getInstance();
            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINES,
                                           VertexFormats.POSITION_COLOR);

            // SARI renk: R=1, G=0.85, B=0 (altın sarısı)
            buf.vertex(ms.peek().getPositionMatrix(),
                (float)(p1.x-cam.x), (float)(p1.y-cam.y), (float)(p1.z-cam.z))
               .color(1.0f, 0.85f, 0.0f, life1 * 0.95f);
            buf.vertex(ms.peek().getPositionMatrix(),
                (float)(p2.x-cam.x), (float)(p2.y-cam.y), (float)(p2.z-cam.z))
               .color(1.0f, 0.85f, 0.0f, life2 * 0.95f);

            BufferRenderer.drawWithGlobalProgram(buf.end());
        }

        // Parlama katmanı — daha geniş, daha şeffaf
        RenderSystem.lineWidth(10.0f);
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
               .color(1.0f, 0.9f, 0.1f, life1 * 0.30f);
            buf.vertex(ms.peek().getPositionMatrix(),
                (float)(p2.x-cam.x), (float)(p2.y-cam.y), (float)(p2.z-cam.z))
               .color(1.0f, 0.9f, 0.1f, life2 * 0.30f);
            BufferRenderer.drawWithGlobalProgram(buf.end());
        }

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    // ══════════════════════════════════════════════════════════
    //  HİTBOX BOYUTUNA GÖRE DÖNEN HALKA
    //  Halka entity'nin hitbox yarıçapında, yavaş döner, orta kalın
    // ══════════════════════════════════════════════════════════
    private static void drawHitboxSizedRing(MatrixStack ms, long time, float hw) {
        float pulse = (float)(Math.sin(time*0.004)*0.2+0.8);
        float rotY  = (float)(time * 0.0009 * 60.0); // yavaş dönsün

        ms.push();
        ms.multiply(new Quaternionf().rotateY((float)Math.toRadians(rotY)));
        Matrix4f mat = ms.peek().getPositionMatrix();

        // Ana halka — hitbox boyutunda, orta kalınlık (4.5f)
        drawCircle(mat, hw, 1.0f, 0.08f*pulse, 0.08f*pulse, 0.9f*pulse, 4.5f, 80);
        // Dış parlama
        drawCircle(mat, hw * 1.12f, 1.0f, 0.2f*pulse, 0.2f*pulse, 0.7f*pulse, 2.0f, 64);
        // İç parlama
        drawCircle(mat, hw * 0.86f, 0.9f, 0.1f*pulse, 0.1f*pulse, 0.8f*pulse, 1.5f, 56);
        ms.pop();
    }

    // ══════════════════════════════════════════════════════════
    //  JUMP RING — tam DAİRE, KALIN halka
    // ══════════════════════════════════════════════════════════
    private static void drawJumpRings(MatrixStack ms, Vec3d cam) {
        if (HitX.jumpRings.isEmpty()) return;

        for (HitX.JumpRing jr : HitX.jumpRings) {
            float life = 1f - (jr.age / (float)HitX.JUMP_RING_LIFE);
            float alpha = life * 0.90f;
            if (alpha < 0.02f) continue;

            float[] rgb = HitX.hsv(0.48f + life*0.04f, 0.85f, 1.0f);

            ms.push();
            ms.translate(jr.x-cam.x, jr.y-cam.y+0.02, jr.z-cam.z);

            Matrix4f mat = ms.peek().getPositionMatrix();
            drawCircle(mat, jr.radius, rgb[0], rgb[1], rgb[2], alpha, 6.0f, 80);
            drawCircle(mat, jr.radius * 1.08f, rgb[0], rgb[1], rgb[2], alpha * 0.35f, 3.5f, 64);
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
            drawCircle(mat, hr.radius, 1.0f, 0.1f, 0.1f, alpha, 7.0f, 80);
            drawCircle(mat, hr.radius * 1.10f, 1.0f, 0.3f, 0.05f, alpha * 0.4f, 3.5f, 64);
            drawCircle(mat, hr.radius * 0.85f, 1.0f, 0.05f, 0.05f, alpha * 0.5f, 2.5f, 56);

            ms.pop();
        }
    }

    // ══════════════════════════════════════════════════════════
    //  ANİMASYONLU HASIR ŞAPKA — kendi playerımıza
    //  Fotoğraftaki gibi: yayvan konik şapka, sarı/altın renk,
    //  hafifçe sallanır (bob animasyonu)
    // ══════════════════════════════════════════════════════════
    private static void drawPlayerHat(WorldRenderContext ctx, MatrixStack ms,
                                       Vec3d cam, float td, long time) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        // Kamera arkasından render için: 3. şahıs değilse de çiz
        // (1. şahıs modunda da baş üstünde görünsün)
        double px = mc.player.prevX + (mc.player.getX() - mc.player.prevX) * td;
        double py = mc.player.prevY + (mc.player.getY() - mc.player.prevY) * td;
        double pz = mc.player.prevZ + (mc.player.getZ() - mc.player.prevZ) * td;

        double rx = px - cam.x;
        double ry = py - cam.y;
        double rz = pz - cam.z;

        // Başın üstüne yerleştir (player yüksekliği ~1.8, kafa ~1.5-1.9 arası)
        float headY = 1.88f;

        // Hafif sallanma animasyonu
        float bob = (float)(Math.sin(time * 0.0025) * 0.04);

        // Player yaw yönüne döndür
        float yaw = mc.player.prevBodyYaw + (mc.player.bodyYaw - mc.player.prevBodyYaw) * td;

        ms.push();
        ms.translate(rx, ry + headY + bob, rz);
        ms.multiply(new Quaternionf().rotateY((float)Math.toRadians(-yaw)));

        Matrix4f mat = ms.peek().getPositionMatrix();

        // ── Şapka gövdesi: yayvan koni (fotoğraftaki gibi) ──
        // Hasır şapka = geniş kenarlı, orta yükseklikte konik
        // Renkler: koyu sarı/altın + kenar açık sarı

        // Koni tepe noktası
        float coneH   = 0.32f;  // koni yüksekliği
        float brimR   = 0.52f;  // şapka kenar yarıçapı (geniş)
        float crownR  = 0.20f;  // tepe yarıçapı (geniş tavan)
        float brimY   = 0.0f;
        float crownY  = coneH;

        int segs = 24;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        Tessellator tess = Tessellator.getInstance();

        // Geniş kenarlık (brim) — üst yüz
        {
            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.TRIANGLE_FAN,
                                            VertexFormats.POSITION_COLOR);
            buf.vertex(mat, 0f, brimY + 0.01f, 0f).color(0.55f, 0.38f, 0.08f, 0.95f);
            for (int i = 0; i <= segs; i++) {
                double a = 2.0 * Math.PI * i / segs;
                buf.vertex(mat,
                    (float)(Math.cos(a) * brimR),
                    brimY + 0.01f,
                    (float)(Math.sin(a) * brimR))
                   .color(0.70f, 0.52f, 0.12f, 0.95f);
            }
            BufferRenderer.drawWithGlobalProgram(buf.end());
        }

        // Koni yan yüzeyi (crown)
        {
            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.TRIANGLE_STRIP,
                                            VertexFormats.POSITION_COLOR);
            for (int i = 0; i <= segs; i++) {
                double a = 2.0 * Math.PI * i / segs;
                float cx = (float)Math.cos(a);
                float cz = (float)Math.sin(a);
                // Dip (brim iç kenar)
                buf.vertex(mat, cx * crownR * 0.9f, brimY, cz * crownR * 0.9f)
                   .color(0.48f, 0.32f, 0.05f, 0.95f);
                // Tepe (flat top - hasır şapkada düz tepe)
                buf.vertex(mat, cx * crownR, crownY, cz * crownR)
                   .color(0.62f, 0.44f, 0.10f, 0.95f);
            }
            BufferRenderer.drawWithGlobalProgram(buf.end());
        }

        // Tepe düz kapak
        {
            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.TRIANGLE_FAN,
                                            VertexFormats.POSITION_COLOR);
            buf.vertex(mat, 0f, crownY, 0f).color(0.65f, 0.46f, 0.10f, 0.95f);
            for (int i = 0; i <= segs; i++) {
                double a = 2.0 * Math.PI * i / segs;
                buf.vertex(mat,
                    (float)(Math.cos(a) * crownR),
                    crownY,
                    (float)(Math.sin(a) * crownR))
                   .color(0.55f, 0.38f, 0.08f, 0.95f);
            }
            BufferRenderer.drawWithGlobalProgram(buf.end());
        }

        // Alt brim alt yüz (hafif görünür)
        {
            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.TRIANGLE_FAN,
                                            VertexFormats.POSITION_COLOR);
            buf.vertex(mat, 0f, brimY - 0.01f, 0f).color(0.38f, 0.25f, 0.04f, 0.80f);
            for (int i = segs; i >= 0; i--) {
                double a = 2.0 * Math.PI * i / segs;
                buf.vertex(mat,
                    (float)(Math.cos(a) * brimR),
                    brimY - 0.01f,
                    (float)(Math.sin(a) * brimR))
                   .color(0.45f, 0.30f, 0.06f, 0.80f);
            }
            BufferRenderer.drawWithGlobalProgram(buf.end());
        }

        // Şapka kenar çizgisi (neon outline — mavi/cyan, fotoğraftaki gibi)
        RenderSystem.lineWidth(2.0f);
        {
            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP,
                                            VertexFormats.POSITION_COLOR);
            for (int i = 0; i <= segs; i++) {
                double a = 2.0 * Math.PI * i / segs;
                buf.vertex(mat,
                    (float)(Math.cos(a) * brimR),
                    brimY,
                    (float)(Math.sin(a) * brimR))
                   .color(0.20f, 0.80f, 1.0f, 0.85f);
            }
            BufferRenderer.drawWithGlobalProgram(buf.end());
        }
        // Tepe outline
        {
            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP,
                                            VertexFormats.POSITION_COLOR);
            for (int i = 0; i <= segs; i++) {
                double a = 2.0 * Math.PI * i / segs;
                buf.vertex(mat,
                    (float)(Math.cos(a) * crownR),
                    crownY,
                    (float)(Math.sin(a) * crownR))
                   .color(0.20f, 0.80f, 1.0f, 0.70f);
            }
            BufferRenderer.drawWithGlobalProgram(buf.end());
        }

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();

        ms.pop();
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

    // ══════════════════════════════════════════════════════════
    //  TAM DAİRE çizici (rx = rz)
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
            buf.vertex(mat, (float)(Math.cos(a)*radius), 0f, (float)(Math.sin(a)*radius))
               .color(r, g, b, alpha);
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
