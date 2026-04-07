package exloran.hitx;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class HitX implements ClientModInitializer {
    public static boolean hudOn = true, tagOn = true, particleOn = true;
    public static float xzExpand = 0.3f, yExpand = 0.1f;
    public static boolean hitBoxActive = true;
    
    private PlayerEntity target = null;
    private float alpha = 0f;
    private boolean rLast, kLast;
    private final List<TargetParticle> particles = new ArrayList<>();

    @Override
    public void onInitializeClient() {
        AutoConfig.register(HitXConfig.class, GsonConfigSerializer::new);

        ScreenEvents.AFTER_INIT.register((client, screen, W, H) -> {
            if (screen instanceof GenericContainerScreen chest) {
                int sx = W / 2 + 92, sy = H / 2 - 80, id = chest.getScreenHandler().syncId;
                iconBtn(screen, new ItemStack(Items.HOPPER), "Hepsini Çek", sx, sy, 24, 22, b -> {
                    for (int i = 0; i < chest.getScreenHandler().getInventory().size(); i++)
                        client.interactionManager.clickSlot(id, i, 0, SlotActionType.QUICK_MOVE, client.player);
                });
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            
            // Tuş Kontrolleri
            long h = client.getWindow().getHandle();
            if (GLFW.glfwGetKey(h, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS && !rLast) hudOn = !hudOn;
            if (GLFW.glfwGetKey(h, GLFW.GLFW_KEY_K) == GLFW.GLFW_PRESS && !kLast) client.setScreen(new HitXSettingsScreen());
            rLast = GLFW.glfwGetKey(h, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;
            kLast = GLFW.glfwGetKey(h, GLFW.GLFW_KEY_K) == GLFW.GLFW_PRESS;

            // Target Arama
            if (client.crosshairTarget instanceof EntityHitResult e && e.getEntity() instanceof PlayerEntity pl) target = pl;
            else target = null;

            alpha = (target != null && hudOn) ? Math.min(1f, alpha + 0.08f) : Math.max(0f, alpha - 0.08f);

            if (particleOn && target != null && alpha > 0.5f) {
                if (client.world.random.nextFloat() < 0.3f) particles.add(new TargetParticle(client.world.random.nextFloat() * 160, client.world.random.nextFloat() * 50));
            }
            particles.removeIf(TargetParticle::update);
        });

        HudRenderCallback.EVENT.register((ctx, tick) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.options.hudHidden || alpha <= 0.01f) return;

            int color = getPinkWhiteFlop(0, alpha);
            int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
            int bX = sw / 2 - 80, bY = sh / 2 + 100;

            ctx.getMatrices().push();
            ctx.getMatrices().translate(bX, bY, 0);
            ctx.fill(-1, -1, 161, 51, (int)(alpha * 255) << 24 | (color & 0xFFFFFF));
            ctx.fill(0, 0, 160, 50, 0xCC050505);
            
            if (target != null) {
                Identifier sk = mc.getSkinProvider().getSkinTextures(target.getGameProfile()).texture();
                ctx.drawTexture(sk, 5, 5, 30, 30, 8, 8, 8, 8, 64, 64);
                ctx.drawText(mc.textRenderer, target.getName().getString(), 40, 10, -1, true);
                float hp = target.getHealth() / target.getMaxHealth();
                ctx.fill(40, 25, 40 + 110, 30, 0x44FFFFFF);
                ctx.fill(40, 25, 40 + (int)(hp * 110), 30, color);
            }
            for (TargetParticle p : particles) p.render(ctx, color);
            ctx.getMatrices().pop();
        });
    }

    public static int getPinkWhiteFlop(int off, float a) {
        double w = (Math.sin((System.currentTimeMillis() + off) / 400.0) + 1.0) / 2.0;
        return ((int)(a * 255) << 24) | (0xFF << 16) | ((int)(150 + 100 * w) << 8) | (int)(180 + 75 * w);
    }

    private void iconBtn(Screen s, ItemStack i, String t, int x, int y, int w, int h, ButtonWidget.PressAction a) {
        Screens.getButtons(s).add(ButtonWidget.builder(Text.literal(t), a).dimensions(x, y, w, h).build());
    }

    public static class TargetParticle {
        float x, y, mx, my, age = 20;
        public TargetParticle(float x, float y) { this.x = x; this.y = y; this.mx = (float)(Math.random()-0.5)*2; this.my = (float)(Math.random()-0.5)*2; }
        public boolean update() { x += mx; y += my; age--; return age < 0; }
        public void render(DrawContext ctx, int c) {
            int a = (int)((age / 20f) * 255);
            ctx.fill((int)x, (int)y, (int)x+2, (int)y+2, (a << 24) | (c & 0xFFFFFF));
        }
    }
}
