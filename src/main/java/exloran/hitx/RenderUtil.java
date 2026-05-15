package exloran.hitx.util;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import java.awt.Color;

public class RenderUtil {

    // ── Yuvarlak Dikdörtgen (Rounded Rect) ──────────────────
    public static void drawRoundedRect(MatrixStack matrices, float x, float y,
                                       float w, float h, float r, int color) {
        float a = ((color >> 24) & 0xFF) / 255f;
        float red = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8)  & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.TRIANGLE_FAN,
                                       VertexFormats.POSITION_COLOR);
        Matrix4f mx = matrices.peek().getPositionMatrix();

        int segs = 12; // köşe başına segment sayısı
        float[] cx = {x+r, x+w-r, x+w-r, x+r};
        float[] cy = {y+h-r, y+h-r, y+r, y+r};
        float[] startAngle = {90f, 0f, 270f, 180f};

        // Merkez
        buf.vertex(mx, x + w/2, y + h/2, 0).color(red,g,b,a);

        for (int i = 0; i < 4; i++) {
            for (int j = 0; j <= segs; j++) {
                double angle = Math.toRadians(startAngle[i] + j * 90f / segs);
                float vx = (float)(cx[i] + Math.cos(angle) * r);
                float vy = (float)(cy[i] - Math.sin(angle) * r);
                buf.vertex(mx, vx, vy, 0).color(red,g,b,a);
            }
        }
        // ilk noktayı kapat
        double angle = Math.toRadians(startAngle[0]);
        buf.vertex(mx, (float)(cx[0]+Math.cos(angle)*r),
                       (float)(cy[0]-Math.sin(angle)*r), 0).color(red,g,b,a);

        tess.draw(buf);
        RenderSystem.disableBlend();
    }

    // ── Yuvarlak Outline (Çerçeve) ───────────────────────────
    public static void drawRoundedOutline(MatrixStack matrices, float x, float y,
                                          float w, float h, float r, float thick, int color) {
        // İçi şeffaf dolgu + kenar
        drawRoundedRect(matrices, x, y, w, h, r, color & 0x44FFFFFF);
        // Her kenara ince çizgi (basit yöntem)
        drawRoundedRect(matrices, x, y, w, thick, 0, color);
        drawRoundedRect(matrices, x, y+h-thick, w, thick, 0, color);
        drawRoundedRect(matrices, x, y, thick, h, 0, color);
        drawRoundedRect(matrices, x+w-thick, y, thick, h, 0, color);
    }

    // ── Parıltı (Glow) Efekti ────────────────────────────────
    public static void drawGlow(MatrixStack matrices, float x, float y,
                                float w, float h, int color, int layers) {
        for (int i = layers; i > 0; i--) {
            int alpha = (int)(((color >> 24) & 0xFF) * (i / (float)layers) * 0.3f);
            int glowColor = (alpha << 24) | (color & 0x00FFFFFF);
            float expand = (layers - i + 1) * 1.5f;
            drawRoundedRect(matrices, x - expand, y - expand,
                    w + expand*2, h + expand*2, 6f + expand, glowColor);
        }
    }
}
