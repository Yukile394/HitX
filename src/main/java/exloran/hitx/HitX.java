package exloran.hitx;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class HitX implements ClientModInitializer {
    // Ayarlar - Settings ekranından erişilmesi için public static
    public static boolean hudOn = true;
    public static boolean hitBoxActive = true;
    public static float xzExpand = 0.3f;
    public static float yExpand = 0.1f;

    private boolean kLast = false, rLast = false;
    private PlayerEntity target = null;
    private float alpha = 0f;
    private final List<TargetParticle> particles = new ArrayList<>();

    @Override
    public void onInitializeClient() {
        AutoConfig.register(HitXConfig.class, GsonConfigSerializer::new);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            // K Tuşu Menü / R Tuşu HUD
            long h = client.getWindow().getHandle();
            boolean kPressed = GLFW.glfwGetKey(h, GLFW.GLFW_KEY_K) == GLFW.GLFW_PRESS;
            boolean rPressed = GLFW.glfwGetKey(h, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;

            if (kPressed && !kLast) client.setScreen(new HitXSettingsScreen());
            if (rPressed && !rLast) hudOn = !hudOn;
            
            kLast = kPressed;
            rLast = rPressed;

            // Target Bulucu
            if (client.crosshairTarget instanceof EntityHitResult e && e.getEntity() instanceof PlayerEntity pl) {
                target = pl;
            } else {
                target = null;
            }

            // Animasyon ve Partiküller
            alpha = (target != null && hudOn) ? Math.min(1f, alpha + 0.1f) : Math.max(0f, alpha - 0.1f);
            if (alpha > 0.5f && client.world.random.nextFloat() < 0.3f) {
                particles.add(new TargetParticle(client.world.random.nextFloat() * 160, client.world.random.nextFloat() * 50));
            }
            particles.removeIf(TargetParticle::update);
        });

        HudRenderCallback.EVENT.register((ctx, tick) -> {
            if (!hudOn || alpha <= 0.01f) return;
            renderHUD(ctx, MinecraftClient.getInstance());
        });
    }

    private void renderHUD(DrawContext ctx, MinecraftClient mc) {
        int color = getPinkWhiteFlop(0, alpha);
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        
        ctx.getMatrices().push();
        ctx.getMatrices().translate(sw / 2 - 80, sh / 2 + 80, 0);
        
        ctx.fill(-1, -1, 161, 51, (int)(alpha * 255) << 24 | (color & 0xFFFFFF));
        ctx.fill(0, 0, 160, 50, 0xAA050505);
        
        if (target != null) {
            Identifier skin = mc.getSkinProvider().getSkinTextures(target.getGameProfile()).texture();
            ctx.drawTexture(skin, 5, 5, 30, 30, 8, 8, 8, 8, 64, 64);
            ctx.drawText(mc.textRenderer, target.getName().getString(), 40, 10, -1, true);
            float hp = target.getHealth() / target.getMaxHealth();
            ctx.fill(40, 25, 150, 32, 0x44FFFFFF);
            ctx.fill(40, 25, 40 + (int)(hp * 110), 32, color);
        }
        
        for (TargetParticle p : particles) p.render(ctx, color, alpha);
        ctx.getMatrices().pop();
    }

    public static int getPinkWhiteFlop(int off, float a) {
        double w = (Math.sin((System.currentTimeMillis() + off) / 400.0) + 1.0) / 2.0;
        return ((int)(a * 255) << 24) | (0xFF << 16) | ((int)(150 + 100 * w) << 8) | (int)(180 + 75 * w);
    }

    public static class TargetParticle {
        float x, y, mx, my, age = 20;
        public TargetParticle(float x, float y) { 
            this.x = x; this.y = y; 
            this.mx = (float)(Math.random()-0.5)*2f; 
            this.my = (float)(Math.random()-0.5)*2f; 
        }
        public boolean update() { x += mx; y += my; age--; return age < 0; }
        public void render(DrawContext ctx, int c, float a) {
            int alphaVal = (int)((age / 20f) * a * 255);
            ctx.fill((int)x, (int)y, (int)x+2, (int)y+2, (alphaVal << 24) | (c & 0xFFFFFF));
        }
    }
}
