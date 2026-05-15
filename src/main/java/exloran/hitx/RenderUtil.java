package exloran.hitx.util;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

/**
 * RenderUtil — Minecraft 1.21.1 uyumlu yuvarlak köşe çizim yardımcısı.
 * tess.draw(buf) kaldırıldı → BufferRenderer.drawWithGlobalProgram(buf.end()) kullanılıyor.
 */
public class RenderUtil {

    // ══════════════════════════════════════════════════════════
    //  DOLU YUVARLAK DİKDÖRTGEN
    // ══════════════════════════════════════════════════════════
    public static void drawRoundedRect(MatrixStack ms, float x, float y,
                                       float w, float h, float r, int color) {
        float a  = ((color >> 24) & 0xFF) / 255f;
        float rv = ((color >> 16) & 0xFF) / 255f;
        float g  = ((color >>  8) & 0xFF) / 255f;
        float b  = (color & 0xFF)          / 255f;
        if (a <= 0f) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        Matrix4f m4 = ms.peek().getPositionMatrix();
        Tessellator tess = Tessellator.getInstance();

        // TRIANGLE_FAN — merkez + çevre noktalar
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.TRIANGLE_FAN,
                                       VertexFormats.POSITION_COLOR);

        // Merkez nokta
        buf.vertex(m4, x + w / 2f, y + h / 2f, 0f).color(rv, g, b, a);

        int segs = 12;
        // 4 köşe merkezi ve başlangıç açıları (saat yönünde)
        float[] cxArr = {x + w - r, x + r,     x + r,     x + w - r};
        float[] cyArr = {y + r,     y + r,     y + h - r, y + h - r};
        float[] startA = {270f,     180f,      90f,       0f};

        for (int i = 0; i < 4; i++) {
            for (int j = 0; j <= segs; j++) {
                double ang = Math.toRadians(startA[i] + j * 90.0 / segs);
                buf.vertex(m4,
                    (float)(cxArr[i] + Math.cos(ang) * r),
                    (float)(cyArr[i] + Math.sin(ang) * r),
                    0f).color(rv, g, b, a);
            }
        }
        // Kapatma — ilk çevre noktasına dön
        double closeAng = Math.toRadians(startA[0]);
        buf.vertex(m4,
            (float)(cxArr[0] + Math.cos(closeAng) * r),
            (float)(cyArr[0] + Math.sin(closeAng) * r),
            0f).color(rv, g, b, a);

        // 1.21.1 API: tess.draw() YOK → drawWithGlobalProgram kullan
        BufferRenderer.drawWithGlobalProgram(buf.end());

        RenderSystem.disableBlend();
    }

    // ══════════════════════════════════════════════════════════
    //  YUVARLAK ÇERÇEVE (OUTLINE)
    // ══════════════════════════════════════════════════════════
    public static void drawRoundedOutline(MatrixStack ms, float x, float y,
                                          float w, float h, float r, int color) {
        float a  = ((color >> 24) & 0xFF) / 255f;
        float rv = ((color >> 16) & 0xFF) / 255f;
        float g  = ((color >>  8) & 0xFF) / 255f;
        float b  = (color & 0xFF)          / 255f;
        if (a <= 0f) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.lineWidth(1.5f);

        Matrix4f m4 = ms.peek().getPositionMatrix();
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP,
                                       VertexFormats.POSITION_COLOR);

        int segs = 12;
        float[] cxArr  = {x + w - r, x + r,     x + r,     x + w - r};
        float[] cyArr  = {y + r,     y + r,     y + h - r, y + h - r};
        float[] startA = {270f,      180f,      90f,       0f};

        for (int i = 0; i < 4; i++) {
            for (int j = 0; j <= segs; j++) {
                double ang = Math.toRadians(startA[i] + j * 90.0 / segs);
                buf.vertex(m4,
                    (float)(cxArr[i] + Math.cos(ang) * r),
                    (float)(cyArr[i] + Math.sin(ang) * r),
                    0f).color(rv, g, b, a);
            }
        }
        // Kapat — ilk noktaya dön
        double closeAng = Math.toRadians(startA[0]);
        buf.vertex(m4,
            (float)(cxArr[0] + Math.cos(closeAng) * r),
            (float)(cyArr[0] + Math.sin(closeAng) * r),
            0f).color(rv, g, b, a);

        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.disableBlend();
    }

    // ══════════════════════════════════════════════════════════
    //  PARLAMA (GLOW) EFEKTİ
    // ══════════════════════════════════════════════════════════
    public static void drawGlow(MatrixStack ms, float x, float y,
                                float w, float h, int color, int layers) {
        for (int i = layers; i > 0; i--) {
            float factor  = i / (float) layers;
            int   alpha   = (int)(((color >> 24) & 0xFF) * factor * 0.25f);
            int   glowCol = (alpha << 24) | (color & 0x00FFFFFF);
            float expand  = (layers - i + 1) * 1.8f;
            drawRoundedRect(ms,
                x - expand, y - expand,
                w + expand * 2f, h + expand * 2f,
                6f + expand, glowCol);
        }
    }

    // ══════════════════════════════════════════════════════════
    //  GRADİYAN DİKDÖRTGEN (Yatay, iki renk arası)
    // ══════════════════════════════════════════════════════════
    public static void drawGradientRect(MatrixStack ms, float x, float y,
                                        float w, float h,
                                        int colorLeft, int colorRight) {
        float aL  = ((colorLeft  >> 24) & 0xFF) / 255f;
        float rL  = ((colorLeft  >> 16) & 0xFF) / 255f;
        float gL  = ((colorLeft  >>  8) & 0xFF) / 255f;
        float bL  = (colorLeft  & 0xFF)          / 255f;

        float aR  = ((colorRight >> 24) & 0xFF) / 255f;
        float rR  = ((colorRight >> 16) & 0xFF) / 255f;
        float gR  = ((colorRight >>  8) & 0xFF) / 255f;
        float bR  = (colorRight & 0xFF)          / 255f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        Matrix4f m4 = ms.peek().getPositionMatrix();
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS,
                                       VertexFormats.POSITION_COLOR);

        buf.vertex(m4, x,     y,     0f).color(rL, gL, bL, aL);
        buf.vertex(m4, x,     y + h, 0f).color(rL, gL, bL, aL);
        buf.vertex(m4, x + w, y + h, 0f).color(rR, gR, bR, aR);
        buf.vertex(m4, x + w, y,     0f).color(rR, gR, bR, aR);

        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.disableBlend();
    }
}
