package exloran.hitx;

// ═══════════════════════════════════════════════════════════════
//  NeonBoxRenderer.java — v4
//
//  DEĞİŞİKLİKLER:
//  1) Şapka → KALDIRILDI
//  2) Hitbox mavi kutu → KALDIRILDI
//     Yerine: entity'nin TAM ORTASINDA dönen keskin pembe/beyaz halka
//  3) Trail → KALIN (8f), rengi HitColor veya Rainbow
//  4) Particles modülü → önündeki entity etrafında süslü partiküller
//  5) HUD → fotoğraftaki gibi yuvarlak, skin kafası + isim + HP
// ═══════════════════════════════════════════════════════════════

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

    // ── Particle sistemi ─────────────────────────────────────
    private static final List<Particle> particles = new ArrayList<>();
    private static final Random rng = new Random();
    private static float particleHue = 0f;

    private static class Particle {
        double x, y, z;
        double vx, vy, vz;
        float r, g, b;
        float hue;
        int age, life;
        Particle(double x, double y, double z, double vx, double vy, double vz,
                 float r, float g, float b, float hue, int life) {
            this.x=x; this.y=y; this.z=z;
            this.vx=vx; this.vy=vy; this.vz=vz;
            this.r=r; this.g=g; this.b=b; this.hue=hue;
            this.life=life;
        }
    }

    // ── Merkez dönen halka için son target kaydı ──────────────
    private static LivingEntity lastTarget = null;

    public static void register() {
        WorldRenderEvents.LAST.register(NeonBoxRenderer::render);
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

        // ── 1. Trail ─────────────────────────────────────────
        drawTrail(ms, cam, time);

        // ── 2. Jump Rings ────────────────────────────────────
        drawJumpRings(ms, cam);

        // ── 3. Hit Rings ─────────────────────────────────────
        drawHitRings(ms, cam);

        ms.pop();

        // ── 4. Hitbox target: merkez dönen halka ─────────────
        if (HitX.hitBoxActive) {
            LivingEntity target = null;
            if (mc.crosshairTarget instanceof EntityHitResult ehr
                    && ehr.getEntity() instanceof LivingEntity le
                    && le != mc.player && le.isAlive())
                target = le;

            lastTarget = target;

            if (target != null) {
                double ex = target.prevX + (target.getX()-target.prevX)*td - cam.x;
                double ey = target.prevY + (target.getY()-target.prevY)*td - cam.y;
                double ez = target.prevZ + (target.getZ()-target.prevZ)*td - cam.z;

                float hh = (float)(target.getBoundingBox().getLengthY());
                float hw = (float)(target.getBoundingBox().getLengthX() * 0.5f);

                // Entity'nin TAM ortası
                ms.push();
                ms.translate(ex, ey + hh * 0.5f, ez);
                drawCenterSpinRing(ms, time, hw, hh);
                ms.pop();
            }
        } else {
            lastTarget = null;
        }

        // ── 5. Particles ─────────────────────────────────────
        if (HitX.particlesActive) {
            tickParticles(mc, cam, time, td);
            drawParticles(ms, cam, time);
        }
    }

    // ══════════════════════════════════════════════════════════
    //  TAM ORTADA DÖNEN KESKİN PEMBE/BEYAZ HALKA
    //  Entity'nin hitbox yüksekliğinin tam ortasında, sabit konumda döner
    // ══════════════════════════════════════════════════════════
    private static void drawCenterSpinRing(MatrixStack ms, long time, float hw, float hh) {
        float pulse  = (float)(Math.sin(time * 0.005) * 0.3 + 0.7);
        float pulse2 = (float)(Math.sin(time * 0.007 + 1.0) * 0.3 + 0.7);

        // ─ Yatay dönen halka (Y ekseni etrafında) ─
        float rotY1 = (float)(time * 0.0012 * 60.0);
        float rotY2 = -(float)(time * 0.0009 * 60.0); // ters yönde

        // Keskin pembe/beyaz — 3 katmanlı halka
        ms.push();
        ms.multiply(new Quaternionf().rotateY((float)Math.toRadians(rotY1)));
        Matrix4f mat = ms.peek().getPositionMatrix();
        // Dış beyaz parlama
        drawCircle(mat, hw * 1.1f, 1.0f, 1.0f, 1.0f, 0.18f * pulse, 2.5f, 80);
        // Ana pembe halka
        drawCircle(mat, hw,        1.0f, 0.25f * pulse2, 0.85f * pulse2, 7.0f, 80);
        // İç parlama
        drawCircle(mat, hw * 0.88f, 1.0f, 0.5f * pulse, 1.0f * pulse, 3.0f, 64);
        ms.pop();

        // ─ Dikey dönen halka (X ekseni etrafında, ters) ─
        ms.push();
        ms.multiply(new Quaternionf().rotateY((float)Math.toRadians(rotY2)));
        ms.multiply(new Quaternionf().rotateX((float)Math.toRadians(rotY1 * 0.7f)));
        Matrix4f mat2 = ms.peek().getPositionMatrix();
        // Dış beyaz
        drawCircle(mat2, hw * 1.05f, 1.0f, 0.9f, 1.0f, 0.12f * pulse2, 2.0f, 72);
        // Ana cyan/pembe çapraz
        drawCircle(mat2, hw * 0.9f, 0.8f * pulse, 0.3f, 1.0f * pulse, 5.0f, 72);
        ms.pop();

        // ─ 3. dönen çizgiler (X çapraz) ─
        float rotX = (float)(time * 0.0006 * 60.0);
        ms.push();
        ms.multiply(new Quaternionf().rotateY((float)Math.toRadians(rotX)));
        Matrix4f mat3 = ms.peek().getPositionMatrix();

        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.lineWidth(3.5f);

        Tessellator tess = Tessellator.getInstance();
        // Yatay çapraz çizgi — keskin beyaz
        { BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
          buf.vertex(mat3, -hw*1.2f, 0, 0).color(1f, 0.3f, 0.9f, 0.9f * pulse);
          buf.vertex(mat3,  hw*1.2f, 0, 0).color(1f, 1.0f, 1.0f, 0.9f * pulse);
          BufferRenderer.drawWithGlobalProgram(buf.end()); }
        // Dikey çapraz çizgi
        { BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
          buf.vertex(mat3, 0, 0, -hw*1.2f).color(1f, 1.0f, 1.0f, 0.9f * pulse);
          buf.vertex(mat3, 0, 0,  hw*1.2f).color(1f, 0.3f, 0.9f, 0.9f * pulse);
          BufferRenderer.drawWithGlobalProgram(buf.end()); }

        RenderSystem.enableDepthTest(); RenderSystem.disableBlend();
        ms.pop();
    }

    // ══════════════════════════════════════════════════════════
    //  PARTICLES — target etrafında süslü partiküller
    // ══════════════════════════════════════════════════════════
    private static void tickParticles(MinecraftClient mc, Vec3d cam, long time, float td) {
        particleHue = (particleHue + 0.015f) % 1.0f;

        // Önündeki entity
        LivingEntity target = null;
        if (mc.crosshairTarget instanceof EntityHitResult ehr
                && ehr.getEntity() instanceof LivingEntity le
                && le != mc.player && le.isAlive())
            target = le;

        // Tick — yeni partiküller üret
        if (target != null && mc.player.age % 1 == 0) {
            float hh = (float) target.getBoundingBox().getLengthY();
            float hw = (float)(target.getBoundingBox().getLengthX() * 0.5f);

            for (int i = 0; i < 3; i++) {
                // Entity yüzeyine yakın rastgele nokta
                double angle = rng.nextDouble() * Math.PI * 2;
                double yOff  = rng.nextDouble() * hh;
                double rad   = hw * (0.8 + rng.nextDouble() * 0.4);
                double ox = Math.cos(angle) * rad;
                double oz = Math.sin(angle) * rad;

                // Hafif yukarı + dışa doğru hareket
                double vx = Math.cos(angle) * 0.015;
                double vy = 0.015 + rng.nextDouble() * 0.02;
                double vz = Math.sin(angle) * 0.015;

                float pr, pg, pb;
                float hue;
                if (HitX.particleColorMode == 1) {
                    hue = (particleHue + i * 0.15f) % 1.0f;
                    float[] rgb = HitX.hsv(hue, 1.0f, 1.0f);
                    pr=rgb[0]; pg=rgb[1]; pb=rgb[2];
                } else {
                    hue = 0f;
                    pr=HitX.particleColorR; pg=HitX.particleColorG; pb=HitX.particleColorB;
                }

                particles.add(new Particle(
                    target.getX() + ox,
                    target.getY() + yOff,
                    target.getZ() + oz,
                    vx, vy, vz,
                    pr, pg, pb, hue, 18 + rng.nextInt(10)));
            }
        }

        // Partikülleri güncelle
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle p = it.next();
            p.x += p.vx; p.y += p.vy; p.z += p.vz;
            p.vy -= 0.001; // hafif yerçekimi
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
        RenderSystem.lineWidth(3.5f);

        Tessellator tess = Tessellator.getInstance();

        ms.push();
        Matrix4f mat = ms.peek().getPositionMatrix();

        for (Particle p : particles) {
            float life = 1f - (p.age / (float)p.life);
            float alpha = life * 0.95f;
            if (alpha < 0.02f) continue;

            float pr = p.r, pg = p.g, pb = p.b;
            if (HitX.particleColorMode == 1) {
                float hue2 = (p.hue + p.age * 0.02f) % 1.0f;
                float[] rgb = HitX.hsv(hue2, 1.0f, 1.0f);
                pr=rgb[0]; pg=rgb[1]; pb=rgb[2];
            }

            float px = (float)(p.x - cam.x);
            float py2 = (float)(p.y - cam.y);
            float pz = (float)(p.z - cam.z);

            // Parlak nokta olarak küçük çizgiler (yıldız şekli)
            float sz = life * 0.08f;
            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            buf.vertex(mat, px-sz, py2, pz).color(pr, pg, pb, alpha);
            buf.vertex(mat, px+sz, py2, pz).color(pr, pg, pb, alpha);
            BufferRenderer.drawWithGlobalProgram(buf.end());

            buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            buf.vertex(mat, px, py2-sz, pz).color(pr, pg, pb, alpha);
            buf.vertex(mat, px, py2+sz, pz).color(pr, pg, pb, alpha);
            BufferRenderer.drawWithGlobalProgram(buf.end());

            buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            buf.vertex(mat, px, py2, pz-sz).color(pr, pg, pb, alpha);
            buf.vertex(mat, px, py2, pz+sz).color(pr, pg, pb, alpha);
            BufferRenderer.drawWithGlobalProgram(buf.end());

            // Glow — daha büyük, şeffaf
            RenderSystem.lineWidth(6.0f);
            buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            buf.vertex(mat, px-sz*2f, py2, pz).color(pr, pg, pb, alpha*0.25f);
            buf.vertex(mat, px+sz*2f, py2, pz).color(pr, pg, pb, alpha*0.25f);
            BufferRenderer.drawWithGlobalProgram(buf.end());
            RenderSystem.lineWidth(3.5f);
        }

        ms.pop();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    // ══════════════════════════════════════════════════════════
    //  HUD — fotoğraftaki gibi yuvarlak köşeli, skin + isim + HP
    //  Sadece hitBoxActive açıkken VE bir entity'e bakılıyorken göster
    // ══════════════════════════════════════════════════════════
    private static void renderHud(DrawContext ctx, float tickDelta) {
        if (!HitX.hudActive) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        // Baktığımız entity (hitbox açık olsun ya da olmasın, HUD için ayrı kontrol)
        LivingEntity target = null;
        if (mc.crosshairTarget instanceof EntityHitResult ehr
                && ehr.getEntity() instanceof LivingEntity le
                && le != mc.player && le.isAlive())
            target = le;

        // Hitbox açık değilse veya target yoksa HUD'ı gösterme
        if (!HitX.hitBoxActive || target == null) return;

        MatrixStack ms = ctx.getMatrices();

        int hx = HitX.hudX;
        int hy = HitX.hudY;

        // ── Panel boyutları ───────────────────────────────────
        int panelW = 140;
        int panelH = 42;
        int avatarSize = 28;
        int avatarX = hx + 8;
        int avatarY = hy + 7;

        // ── Arka plan — koyu, yuvarlak köşeli (fotoğraftaki gibi) ──
        // Koyu gri/kahve arka plan
        HitX.fillRound(ms, hx, hy, panelW, panelH, 10f, 0xD8201818);
        // İnce outline
        HitX.outlineRound(ms, hx, hy, panelW, panelH, 10f, 0xAA6644AA);

        // ── Avatar (skin kafası) ──────────────────────────────
        // Skin karesiz — yuvarlak köşeli kırpma efekti için üst üste iki çizim
        HitX.fillRound(ms, avatarX-1, avatarY-1, avatarSize+2, avatarSize+2, 6f, 0xFF3A2020);

        // Skin kafasını çiz (DrawContext.drawPlayerHead veya entity'nin uuid'siyle)
        // Fabric'te direkt player head çizilebilir:
        try {
            // Oyuncu skin texture'ını al ve kafayı çiz
            if (target instanceof net.minecraft.entity.player.PlayerEntity pe) {
                // Player skin — kafanın UV koordinatları: 8,8 ile 16,16 arası (64x64 texture)
                net.minecraft.util.Identifier skinId =
                    mc.getSkinProvider().getSkinTextures(pe.getGameProfile()).texture();
                ctx.drawTexture(skinId,
                    avatarX, avatarY, avatarSize, avatarSize,
                    8, 8, 8, 8, 64, 64);
                // Dış katman (hat layer)
                ctx.drawTexture(skinId,
                    avatarX, avatarY, avatarSize, avatarSize,
                    40, 8, 8, 8, 64, 64);
            } else {
                // Mob ise kare ile doldur, entity türünü simge olarak göster
                HitX.fillRound(ms, avatarX, avatarY, avatarSize, avatarSize, 4f, 0xFF554422);
                ctx.drawCenteredTextWithShadow(mc.textRenderer, "§7?", avatarX + avatarSize/2, avatarY + avatarSize/2 - 4, 0xFFFFFFFF);
            }
        } catch (Exception ignored) {
            HitX.fillRound(ms, avatarX, avatarY, avatarSize, avatarSize, 4f, 0xFF554422);
        }

        // Yuvarlak kesme efekti — avatar üstüne koyu overlay köşelere
        HitX.outlineRound(ms, avatarX-1, avatarY-1, avatarSize+2, avatarSize+2, 6f, 0xFF3A2020);

        // ── İsim ─────────────────────────────────────────────
        int textX = avatarX + avatarSize + 8;
        int textY = hy + 10;

        String name = target.getName().getString();
        if (name.length() > 12) name = name.substring(0, 12);

        ctx.drawTextWithShadow(mc.textRenderer, "§f" + name, textX, textY, 0xFFFFFFFF);

        // ── HP ────────────────────────────────────────────────
        int hp = (int) target.getHealth();
        int maxHp = (int) target.getMaxHealth();

        ctx.drawTextWithShadow(mc.textRenderer,
            "§cHP: §f" + hp,
            textX, textY + 13, 0xFFDDDDDD);

        // HP bar — ince çizgi
        int barX = textX;
        int barY = textY + 26;
        int barW = panelW - avatarSize - 22;
        int barH = 3;

        // Arka plan
        HitX.fillRound(ms, barX, barY, barW, barH, 1.5f, 0xFF333333);
        // Dolu kısım
        float hpPct = Math.max(0, Math.min(1, target.getHealth() / target.getMaxHealth()));
        // Renk: yeşil→sarı→kırmızı
        float barR, barG, barB;
        if (hpPct > 0.5f) { barR = 1f - (hpPct-0.5f)*2f; barG=0.9f; barB=0.1f; }
        else               { barR = 0.9f; barG = hpPct*2f*0.8f; barB=0.05f; }
        int barColor = HitX.packRgb(barR, barG, barB, 1.0f);
        int filledW = Math.max(2, (int)(hpPct * barW));
        HitX.fillRound(ms, barX, barY, filledW, barH, 1.5f, barColor);
    }

    // ══════════════════════════════════════════════════════════
    //  TRAIL — KALIN (8f), HitColor veya Rainbow rengi
    // ══════════════════════════════════════════════════════════
    private static void drawTrail(MatrixStack ms, Vec3d cam, long time) {
        if (HitX.trail.isEmpty()) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        HitX.TrailPoint[] pts = HitX.trail.toArray(new HitX.TrailPoint[0]);

        // Ana katman — KALIN (8f)
        RenderSystem.lineWidth(8.0f);
        for (int i = 0; i < pts.length - 1; i++) {
            HitX.TrailPoint p1 = pts[i];
            HitX.TrailPoint p2 = pts[i+1];
            float life1 = 1f - (p1.age / (float)HitX.TRAIL_LIFE);
            float life2 = 1f - (p2.age / (float)HitX.TRAIL_LIFE);

            float r1 = p1.r, g1 = p1.g, b1 = p1.b;
            float r2 = p2.r, g2 = p2.g, b2 = p2.b;

            // Rainbow modunda renk kayması
            if (HitX.trailColorMode == 1) {
                float[] rgb1 = HitX.hsv(p1.hue, 1.0f, 1.0f);
                float[] rgb2 = HitX.hsv(p2.hue, 1.0f, 1.0f);
                r1=rgb1[0]; g1=rgb1[1]; b1=rgb1[2];
                r2=rgb2[0]; g2=rgb2[1]; b2=rgb2[2];
            }

            Tessellator tess = Tessellator.getInstance();
            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINES,
                                           VertexFormats.POSITION_COLOR);
            buf.vertex(ms.peek().getPositionMatrix(),
                (float)(p1.x-cam.x), (float)(p1.y-cam.y), (float)(p1.z-cam.z))
               .color(r1, g1, b1, life1 * 0.98f);
            buf.vertex(ms.peek().getPositionMatrix(),
                (float)(p2.x-cam.x), (float)(p2.y-cam.y), (float)(p2.z-cam.z))
               .color(r2, g2, b2, life2 * 0.98f);
            BufferRenderer.drawWithGlobalProgram(buf.end());
        }

        // Parlama katmanı — (13f), daha şeffaf
        RenderSystem.lineWidth(13.0f);
        for (int i = 0; i < pts.length - 1; i++) {
            HitX.TrailPoint p1 = pts[i];
            HitX.TrailPoint p2 = pts[i+1];
            float life1 = 1f - (p1.age / (float)HitX.TRAIL_LIFE);
            float life2 = 1f - (p2.age / (float)HitX.TRAIL_LIFE);

            float r1 = p1.r, g1 = p1.g, b1 = p1.b;
            float r2 = p2.r, g2 = p2.g, b2 = p2.b;
            if (HitX.trailColorMode == 1) {
                float[] rgb1 = HitX.hsv(p1.hue, 0.7f, 1.0f);
                float[] rgb2 = HitX.hsv(p2.hue, 0.7f, 1.0f);
                r1=rgb1[0]; g1=rgb1[1]; b1=rgb1[2];
                r2=rgb2[0]; g2=rgb2[1]; b2=rgb2[2];
            }

            Tessellator tess = Tessellator.getInstance();
            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINES,
                                           VertexFormats.POSITION_COLOR);
            buf.vertex(ms.peek().getPositionMatrix(),
                (float)(p1.x-cam.x), (float)(p1.y-cam.y), (float)(p1.z-cam.z))
               .color(r1, g1, b1, life1 * 0.22f);
            buf.vertex(ms.peek().getPositionMatrix(),
                (float)(p2.x-cam.x), (float)(p2.y-cam.y), (float)(p2.z-cam.z))
               .color(r2, g2, b2, life2 * 0.22f);
            BufferRenderer.drawWithGlobalProgram(buf.end());
        }

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    // ══════════════════════════════════════════════════════════
    //  JUMP RING — zıplayınca kalın daire + "HitX" yazısı
    //  (fotoğraftaki dairesel halka gibi, ortasında yazı)
    // ══════════════════════════════════════════════════════════
    private static void drawJumpRings(MatrixStack ms, Vec3d cam) {
        if (HitX.jumpRings.isEmpty()) return;

        MinecraftClient mc = MinecraftClient.getInstance();

        for (HitX.JumpRing jr : HitX.jumpRings) {
            float life = 1f - (jr.age / (float)HitX.JUMP_RING_LIFE);
            float alpha = life * 0.92f;
            if (alpha < 0.02f) continue;

            // HitColor rengi kullan (fotoğraftaki kırmızı gibi)
            float cr = HitX.hitColorR;
            float cg = HitX.hitColorG;
            float cb = HitX.hitColorB;

            ms.push();
            ms.translate(jr.x-cam.x, jr.y-cam.y+0.02, jr.z-cam.z);
            Matrix4f mat = ms.peek().getPositionMatrix();

            // ── Kalın ana halka ───────────────────────────────
            drawCircle(mat, jr.radius,        cr, cg, cb, alpha,        8.0f, 96);
            drawCircle(mat, jr.radius * 1.10f, cr, cg, cb, alpha*0.30f, 4.0f, 72);
            drawCircle(mat, jr.radius * 0.90f, cr, cg, cb, alpha*0.40f, 3.0f, 64);

            // ── Parlak beyaz iç çizgi ─────────────────────────
            drawCircle(mat, jr.radius * 0.98f, 1f, 1f, 1f, alpha*0.18f, 2.0f, 64);

            ms.pop();

            // ── "HitX" yazısı — halkanın içinde, yatay billboarded ──
            // Yazıyı 2D HUD üzerinden değil, 3D world'de bill-board olarak çiziyoruz
            // Bu kısım WorldRender içinde olmadığından HUD'a yansımayacak,
            // ancak aşağıdaki yaklaşım world-space text render ile yapılabilir.
            // Basit versiyon: halkanın tam ortasına floating text
            if (mc != null && mc.player != null) {
                ms.push();
                ms.translate(
                    jr.x - cam.x,
                    jr.y - cam.y + 0.12,
                    jr.z - cam.z);

                // Kameraya doğru döndür (billboard)
                float yaw = mc.gameRenderer.getCamera().getYaw();
                float pitch = mc.gameRenderer.getCamera().getPitch();
                ms.multiply(new Quaternionf().rotateY((float)Math.toRadians(-yaw)));
                ms.multiply(new Quaternionf().rotateX((float)Math.toRadians(pitch)));

                float scale = jr.radius * 0.014f;
                ms.scale(-scale, -scale, scale);

                // Yazıyı çiz
                Matrix4f textMat = ms.peek().getPositionMatrix();
                net.minecraft.client.font.TextRenderer tr = mc.textRenderer;
                String label = "HitX";
                int tw = tr.getWidth(label);

                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                VertexConsumerProvider.Immediate vcp =
                    mc.getBufferBuilders().getEntityVertexConsumers();
                tr.draw(label, -tw/2f, -4f, HitX.packRgb(cr, cg, cb, alpha),
                    true, textMat, vcp, net.minecraft.client.font.TextRenderer.TextLayerType.NORMAL,
                    0, 15728880);
                vcp.draw();
                RenderSystem.disableBlend();

                ms.pop();
            }
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

            drawCircle(mat, hr.radius, HitX.hitColorR, HitX.hitColorG, HitX.hitColorB, alpha, 7.0f, 80);
            drawCircle(mat, hr.radius * 1.10f, 1.0f, 0.8f, 0.8f, alpha * 0.4f, 3.5f, 64);
            drawCircle(mat, hr.radius * 0.85f, HitX.hitColorR, HitX.hitColorG, HitX.hitColorB, alpha * 0.5f, 2.5f, 56);

            ms.pop();
        }
    }

    // ══════════════════════════════════════════════════════════
    //  TAM DAİRE çizici
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
}
