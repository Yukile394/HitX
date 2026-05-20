package exloran.hitx;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class NeonBoxRenderer {

    // ── Particle hız çarpanı (menüden ayarlanır) ─────────────
    // 1.0 = normal, 0.5 = yavaş, 2.0 = hızlı
    public static float particleSpeed = 1.0f;
    // Her tick üretilen particle sayısı (menüden ayarlanır)
    public static int   particleCount = 6;

    private static final List<Particle> particles = new ArrayList<>();
    private static final Random rng = new Random();
    private static float particleHue = 0f;

    // 6 farklı particle tipi
    private enum PType { ORBIT, RISE, SPARK, RING, TRAIL, BURST }

    private static class Particle {
        double x, y, z;
        double vx, vy, vz;
        float r, g, b, hue;
        int age, life;
        PType type;
        double angle; // orbit için
        double radius;

        Particle(double x, double y, double z, double vx, double vy, double vz,
                 float r, float g, float b, float hue, int life, PType type,
                 double angle, double radius) {
            this.x=x; this.y=y; this.z=z;
            this.vx=vx; this.vy=vy; this.vz=vz;
            this.r=r; this.g=g; this.b=b; this.hue=hue;
            this.life=life; this.type=type;
            this.angle=angle; this.radius=radius;
        }
    }

    public static void register() {
        WorldRenderEvents.LAST.register(NeonBoxRenderer::render);
        HudRenderCallback.EVENT.register(NeonBoxRenderer::renderHud);
    }

    private static void render(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;
        MatrixStack ms = ctx.matrixStack();
        if (ms == null) return;
        Vec3d cam  = ctx.camera().getPos();
        float td   = ctx.tickCounter().getTickDelta(false);
        long  time = System.currentTimeMillis();

        ms.push();
        drawTrail(ms, cam);
        drawJumpRings(ms, cam);
        drawHitRings(ms, cam);
        ms.pop();

        if (HitX.particlesActive) {
            tickParticles(mc, td, time);
            drawParticles(ms, cam, time);
        }
    }

    // ══════════════════════════════════════════════════════════
    //  PARTICLES — 6 farklı tip, hız ayarlanabilir
    // ══════════════════════════════════════════════════════════
    private static void tickParticles(MinecraftClient mc, float td, long time) {
        particleHue = (particleHue + 0.012f * particleSpeed) % 1.0f;

        LivingEntity target = null;
        if (mc.crosshairTarget instanceof EntityHitResult ehr
                && ehr.getEntity() instanceof LivingEntity le
                && le != mc.player && le.isAlive())
            target = le;

        if (target != null) {
            float hh = (float) target.getBoundingBox().getLengthY();
            float hw = (float)(target.getBoundingBox().getLengthX() * 0.5f);

            for (int i = 0; i < particleCount; i++) {
                float pr, pg, pb, hue;
                if (HitX.particleColorMode == 1) {
                    hue = (particleHue + i * 0.10f) % 1.0f;
                    float[] rgb = HitX.hsv(hue, 1.0f, 1.0f);
                    pr=rgb[0]; pg=rgb[1]; pb=rgb[2];
                } else {
                    hue = 0f;
                    pr=HitX.particleColorR; pg=HitX.particleColorG; pb=HitX.particleColorB;
                }

                // Tip döngüsü — 6 tip sırayla
                PType type = PType.values()[i % 6];
                double angle = rng.nextDouble() * Math.PI * 2;
                double yOff  = rng.nextDouble() * hh;
                double rad   = hw * (0.85 + rng.nextDouble() * 0.35);

                double ox = Math.cos(angle) * rad;
                double oz = Math.sin(angle) * rad;
                double vx, vy, vz;
                int life;

                switch (type) {
                    case ORBIT -> {
                        // Yavaş yörüngede döner
                        vx = -Math.sin(angle) * 0.012 * particleSpeed;
                        vy = 0.003 * particleSpeed;
                        vz =  Math.cos(angle) * 0.012 * particleSpeed;
                        life = 30 + rng.nextInt(20);
                    }
                    case RISE -> {
                        // Yukarı çıkar, kaybolur
                        vx = (rng.nextDouble()-0.5) * 0.008 * particleSpeed;
                        vy = (0.025 + rng.nextDouble()*0.02) * particleSpeed;
                        vz = (rng.nextDouble()-0.5) * 0.008 * particleSpeed;
                        life = 20 + rng.nextInt(12);
                    }
                    case SPARK -> {
                        // Hızlı fışkırır
                        vx = Math.cos(angle) * 0.04 * particleSpeed;
                        vy = (rng.nextDouble()*0.03) * particleSpeed;
                        vz = Math.sin(angle) * 0.04 * particleSpeed;
                        life = 12 + rng.nextInt(8);
                    }
                    case RING -> {
                        // Yatayda yayılır
                        vx = Math.cos(angle) * 0.018 * particleSpeed;
                        vy = 0;
                        vz = Math.sin(angle) * 0.018 * particleSpeed;
                        life = 16 + rng.nextInt(10);
                        yOff = hh * 0.5; // orta yükseklikte
                    }
                    case TRAIL -> {
                        // Aşağı düşer
                        vx = (rng.nextDouble()-0.5) * 0.01 * particleSpeed;
                        vy = -(0.015 + rng.nextDouble()*0.015) * particleSpeed;
                        vz = (rng.nextDouble()-0.5) * 0.01 * particleSpeed;
                        life = 22 + rng.nextInt(10);
                        yOff = hh * (0.5 + rng.nextDouble()*0.5);
                    }
                    default -> { // BURST
                        // Patlar, büyük hız
                        double bAngle = rng.nextDouble() * Math.PI * 2;
                        double bPitch = (rng.nextDouble()-0.5) * Math.PI;
                        double sp = (0.03 + rng.nextDouble()*0.04) * particleSpeed;
                        vx = Math.cos(bAngle)*Math.cos(bPitch)*sp;
                        vy = Math.sin(bPitch)*sp;
                        vz = Math.sin(bAngle)*Math.cos(bPitch)*sp;
                        life = 10 + rng.nextInt(8);
                    }
                }

                particles.add(new Particle(
                    target.getX() + ox,
                    target.getY() + yOff,
                    target.getZ() + oz,
                    vx, vy, vz,
                    pr, pg, pb, hue, life, type, angle, rad));
            }
        }

        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle p = it.next();
            p.x += p.vx;
            p.y += p.vy;
            p.z += p.vz;
            // Tip'e göre fizik
            if (p.type == PType.RISE || p.type == PType.BURST)
                p.vy -= 0.0008 * particleSpeed;
            if (p.type == PType.TRAIL)
                p.vy -= 0.0005 * particleSpeed;
            p.angle += 0.04 * particleSpeed;
            p.age++;
            if (p.age >= p.life) it.remove();
        }
    }

    private static void drawParticles(MatrixStack ms, Vec3d cam, long time) {
        if (particles.isEmpty()) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        Tessellator tess = Tessellator.getInstance();
        ms.push();
        Matrix4f mat = ms.peek().getPositionMatrix();

        for (Particle p : particles) {
            float life = 1f - (p.age / (float)p.life);
            float alpha = life * 0.97f;
            if (alpha < 0.02f) continue;

            float pr = p.r, pg = p.g, pb = p.b;
            if (HitX.particleColorMode == 1) {
                float hue2 = (p.hue + p.age * 0.018f) % 1.0f;
                float[] rgb = HitX.hsv(hue2, 1.0f, 1.0f);
                pr=rgb[0]; pg=rgb[1]; pb=rgb[2];
            }

            float px = (float)(p.x - cam.x);
            float py = (float)(p.y - cam.y);
            float pz = (float)(p.z - cam.z);

            float sz = life * 0.10f;

            // Tipe göre farklı çizim
            switch (p.type) {
                case ORBIT, RING -> {
                    // Dönen küçük halka (mini circle, 8 seg)
                    RenderSystem.lineWidth(2.5f);
                    BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);
                    for (int s=0;s<=8;s++) {
                        double a2 = 2*Math.PI*s/8;
                        float r2 = sz * 1.2f;
                        buf.vertex(mat, px+(float)Math.cos(a2)*r2, py+(float)Math.sin(a2)*r2, pz).color(pr,pg,pb,alpha);
                    }
                    BufferRenderer.drawWithGlobalProgram(buf.end());
                }
                case SPARK, BURST -> {
                    // Çizgi şeklinde (velocity yönünde)
                    RenderSystem.lineWidth(3.0f);
                    float vLen = (float)Math.sqrt(p.vx*p.vx+p.vy*p.vy+p.vz*p.vz);
                    float tailLen = life * 0.18f;
                    float nx = vLen>0 ? (float)(p.vx/vLen)*tailLen : 0;
                    float ny = vLen>0 ? (float)(p.vy/vLen)*tailLen : 0;
                    float nz = vLen>0 ? (float)(p.vz/vLen)*tailLen : 0;
                    BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
                    buf.vertex(mat, px, py, pz).color(pr,pg,pb,alpha);
                    buf.vertex(mat, px-nx, py-ny, pz-nz).color(pr,pg,pb,alpha*0.1f);
                    BufferRenderer.drawWithGlobalProgram(buf.end());
                    // Glow baş
                    RenderSystem.lineWidth(6.0f);
                    buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
                    buf.vertex(mat, px-sz*0.5f, py, pz).color(1f,1f,1f,alpha*0.5f);
                    buf.vertex(mat, px+sz*0.5f, py, pz).color(pr,pg,pb,alpha*0.2f);
                    BufferRenderer.drawWithGlobalProgram(buf.end());
                    RenderSystem.lineWidth(3.0f);
                }
                case RISE -> {
                    // Dikey yükselen çizgi + yıldız
                    RenderSystem.lineWidth(2.5f);
                    BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
                    buf.vertex(mat, px, py, pz).color(pr,pg,pb,alpha);
                    buf.vertex(mat, px, py-sz*1.8f, pz).color(pr,pg,pb,0.05f);
                    BufferRenderer.drawWithGlobalProgram(buf.end());
                    // Küçük yatay çapraz
                    RenderSystem.lineWidth(2.0f);
                    buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
                    buf.vertex(mat, px-sz*0.7f, py, pz).color(1f,1f,1f,alpha*0.6f);
                    buf.vertex(mat, px+sz*0.7f, py, pz).color(1f,1f,1f,alpha*0.6f);
                    BufferRenderer.drawWithGlobalProgram(buf.end());
                }
                default -> { // TRAIL
                    // Küçük yıldız (3 çizgi)
                    RenderSystem.lineWidth(3.0f);
                    BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
                    buf.vertex(mat, px-sz, py, pz).color(pr,pg,pb,alpha);
                    buf.vertex(mat, px+sz, py, pz).color(pr,pg,pb,alpha);
                    BufferRenderer.drawWithGlobalProgram(buf.end());
                    buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
                    buf.vertex(mat, px, py-sz, pz).color(pr,pg,pb,alpha);
                    buf.vertex(mat, px, py+sz, pz).color(pr,pg,pb,alpha);
                    BufferRenderer.drawWithGlobalProgram(buf.end());
                    buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
                    buf.vertex(mat, px, py, pz-sz).color(pr,pg,pb,alpha);
                    buf.vertex(mat, px, py, pz+sz).color(pr,pg,pb,alpha);
                    BufferRenderer.drawWithGlobalProgram(buf.end());
                    // Glow
                    RenderSystem.lineWidth(7.0f);
                    buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
                    buf.vertex(mat, px-sz*2f, py, pz).color(pr,pg,pb,alpha*0.18f);
                    buf.vertex(mat, px+sz*2f, py, pz).color(pr,pg,pb,alpha*0.18f);
                    BufferRenderer.drawWithGlobalProgram(buf.end());
                    RenderSystem.lineWidth(3.0f);
                }
            }
        }

        ms.pop();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    // ══════════════════════════════════════════════════════════
    //  HUD — yuvarlak köşeli, skin kafası + isim + HP
    //  Baktığında çıkar (hitbox bağımsız)
    // ══════════════════════════════════════════════════════════
    private static void renderHud(DrawContext ctx, net.minecraft.client.render.RenderTickCounter tickCounter) {
        if (!HitX.hudActive) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        LivingEntity target = null;
        if (mc.crosshairTarget instanceof EntityHitResult ehr
                && ehr.getEntity() instanceof LivingEntity le
                && le != mc.player && le.isAlive())
            target = le;
        if (target == null) return;

        MatrixStack ms = ctx.getMatrices();
        int hx = HitX.hudX, hy = HitX.hudY;
        int panelW = 148, panelH = 46;
        int avatarSize = 30;
        int avatarX = hx + 8, avatarY = hy + 8;

        // Panel arka plan
        HitX.fillRound(ms, hx, hy, panelW, panelH, 11f, 0xE0181010);
        HitX.outlineRound(ms, hx, hy, panelW, panelH, 11f, 0xBB8855CC);

        // Avatar arka
        HitX.fillRound(ms, avatarX-2, avatarY-2, avatarSize+4, avatarSize+4, 7f, 0xFF2A1818);

        try {
            if (target instanceof net.minecraft.entity.player.PlayerEntity pe) {
                net.minecraft.util.Identifier skinId =
                    mc.getSkinProvider().getSkinTextures(pe.getGameProfile()).texture();
                ctx.drawTexture(skinId, avatarX, avatarY, avatarSize, avatarSize, 8, 8, 8, 8, 64, 64);
                ctx.drawTexture(skinId, avatarX, avatarY, avatarSize, avatarSize, 40, 8, 8, 8, 64, 64);
            } else {
                HitX.fillRound(ms, avatarX, avatarY, avatarSize, avatarSize, 5f, 0xFF553322);
                ctx.drawCenteredTextWithShadow(mc.textRenderer, "§7✦",
                    avatarX + avatarSize/2, avatarY + avatarSize/2 - 4, 0xFFFFFFFF);
            }
        } catch (Exception ignored) {
            HitX.fillRound(ms, avatarX, avatarY, avatarSize, avatarSize, 5f, 0xFF553322);
        }
        HitX.outlineRound(ms, avatarX-2, avatarY-2, avatarSize+4, avatarSize+4, 7f, 0xFF2A1818);

        int textX = avatarX + avatarSize + 9;
        int textY = hy + 11;
        String name = target.getName().getString();
        if (name.length() > 13) name = name.substring(0, 13);
        ctx.drawTextWithShadow(mc.textRenderer, "§f" + name, textX, textY, 0xFFFFFFFF);
        ctx.drawTextWithShadow(mc.textRenderer, "§cHP: §f" + (int)target.getHealth(),
            textX, textY + 13, 0xFFDDCCCC);

        int barX = textX, barY = textY + 27;
        int barW = panelW - avatarSize - 24;
        HitX.fillRound(ms, barX, barY, barW, 4, 2f, 0xFF2A2A2A);
        float pct = Math.max(0, Math.min(1, target.getHealth() / target.getMaxHealth()));
        float barR, barG, barB;
        if (pct > 0.5f) { barR = 1f-(pct-0.5f)*2f; barG=0.9f; barB=0.1f; }
        else            { barR = 0.9f; barG=pct*2f*0.8f; barB=0.05f; }
        HitX.fillRound(ms, barX, barY, Math.max(3,(int)(pct*barW)), 4, 2f,
            HitX.packRgb(barR, barG, barB, 1f));
    }

    // ══════════════════════════════════════════════════════════
    //  TRAIL — sadece kalın tek katman, ince glow yok
    // ══════════════════════════════════════════════════════════
    private static void drawTrail(MatrixStack ms, Vec3d cam) {
        if (HitX.trail.isEmpty()) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        HitX.TrailPoint[] pts = HitX.trail.toArray(new HitX.TrailPoint[0]);
        Tessellator tess = Tessellator.getInstance();

        // Ana kalın katman — 11f
        RenderSystem.lineWidth(11.0f);
        for (int i = 0; i < pts.length - 1; i++) {
            HitX.TrailPoint p1 = pts[i];
            HitX.TrailPoint p2 = pts[i+1];
            float life1 = 1f - (p1.age / (float)HitX.TRAIL_LIFE);
            float life2 = 1f - (p2.age / (float)HitX.TRAIL_LIFE);

            float r1=p1.r, g1=p1.g, b1=p1.b;
            float r2=p2.r, g2=p2.g, b2=p2.b;
            if (HitX.trailColorMode == 1) {
                float[] rgb1 = HitX.hsv(p1.hue, 1.0f, 1.0f);
                float[] rgb2 = HitX.hsv(p2.hue, 1.0f, 1.0f);
                r1=rgb1[0]; g1=rgb1[1]; b1=rgb1[2];
                r2=rgb2[0]; g2=rgb2[1]; b2=rgb2[2];
            }

            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            buf.vertex(ms.peek().getPositionMatrix(),
                (float)(p1.x-cam.x),(float)(p1.y-cam.y),(float)(p1.z-cam.z))
               .color(r1,g1,b1,life1*0.98f);
            buf.vertex(ms.peek().getPositionMatrix(),
                (float)(p2.x-cam.x),(float)(p2.y-cam.y),(float)(p2.z-cam.z))
               .color(r2,g2,b2,life2*0.98f);
            BufferRenderer.drawWithGlobalProgram(buf.end());
        }

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    // ══════════════════════════════════════════════════════════
    //  JUMP RING — zıplayınca kalın daire + "HitX" yazısı
    // ══════════════════════════════════════════════════════════
    private static void drawJumpRings(MatrixStack ms, Vec3d cam) {
        if (HitX.jumpRings.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();

        for (HitX.JumpRing jr : HitX.jumpRings) {
            float life = 1f - (jr.age / (float)HitX.JUMP_RING_LIFE);
            float alpha = life * 0.92f;
            if (alpha < 0.02f) continue;

            float cr=HitX.hitColorR, cg=HitX.hitColorG, cb=HitX.hitColorB;

            ms.push();
            ms.translate(jr.x-cam.x, jr.y-cam.y+0.02, jr.z-cam.z);
            Matrix4f mat = ms.peek().getPositionMatrix();
            drawCircle(mat, jr.radius,         cr, cg, cb, alpha,        9.0f, 96);
            drawCircle(mat, jr.radius*1.10f,   cr, cg, cb, alpha*0.28f,  4.0f, 72);
            drawCircle(mat, jr.radius*0.90f,   cr, cg, cb, alpha*0.35f,  3.0f, 64);
            drawCircle(mat, jr.radius*0.98f,  1f,  1f, 1f,  alpha*0.16f, 2.0f, 64);
            ms.pop();

            // "HitX" billboard yazısı
            if (mc != null && mc.player != null) {
                ms.push();
                ms.translate(jr.x-cam.x, jr.y-cam.y+0.12, jr.z-cam.z);
                float yaw   = mc.gameRenderer.getCamera().getYaw();
                float pitch = mc.gameRenderer.getCamera().getPitch();
                ms.multiply(new Quaternionf().rotateY((float)Math.toRadians(-yaw)));
                ms.multiply(new Quaternionf().rotateX((float)Math.toRadians(pitch)));
                float scale = jr.radius * 0.014f;
                ms.scale(-scale, -scale, scale);
                Matrix4f textMat = ms.peek().getPositionMatrix();
                net.minecraft.client.font.TextRenderer tr = mc.textRenderer;
                String label = "HitX";
                int tw = tr.getWidth(label);
                RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
                VertexConsumerProvider.Immediate vcp = mc.getBufferBuilders().getEntityVertexConsumers();
                tr.draw(label, -tw/2f, -4f, HitX.packRgb(cr, cg, cb, alpha),
                    true, textMat, vcp,
                    net.minecraft.client.font.TextRenderer.TextLayerType.NORMAL, 0, 15728880);
                vcp.draw();
                RenderSystem.disableBlend();
                ms.pop();
            }
        }
    }

    // ══════════════════════════════════════════════════════════
    //  HIT RING
    // ══════════════════════════════════════════════════════════
    private static void drawHitRings(MatrixStack ms, Vec3d cam) {
        if (HitX.hitRings.isEmpty()) return;
        for (HitX.HitRing hr : HitX.hitRings) {
            float life = 1f - (hr.age / (float)HitX.HIT_RING_LIFE);
            float alpha = life * 0.95f;
            if (alpha < 0.02f) continue;
            ms.push();
            ms.translate(hr.x-cam.x, hr.y-cam.y+0.02, hr.z-cam.z);
            Matrix4f mat = ms.peek().getPositionMatrix();
            drawCircle(mat, hr.radius,       HitX.hitColorR, HitX.hitColorG, HitX.hitColorB, alpha,       8.0f, 80);
            drawCircle(mat, hr.radius*1.10f, 1.0f, 0.8f, 0.8f, alpha*0.35f, 3.5f, 64);
            drawCircle(mat, hr.radius*0.85f, HitX.hitColorR, HitX.hitColorG, HitX.hitColorB, alpha*0.5f,  2.5f, 56);
            ms.pop();
        }
    }

    // ══════════════════════════════════════════════════════════
    //  DAİRE ÇİZİCİ
    // ══════════════════════════════════════════════════════════
    private static void drawCircle(Matrix4f mat, float radius,
                                   float r, float g, float b, float alpha,
                                   float lw, int segs) {
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.lineWidth(lw);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);
        for (int i=0; i<=segs; i++) {
            double a = 2.0*Math.PI*i/segs;
            buf.vertex(mat,(float)(Math.cos(a)*radius),0f,(float)(Math.sin(a)*radius))
               .color(r,g,b,alpha);
        }
        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.enableDepthTest(); RenderSystem.disableBlend();
    }
}
